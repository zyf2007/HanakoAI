package `fun`.kirari.hanako.overlay

import android.app.Service
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.content.Intent
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import `fun`.kirari.hanako.automation.BubbleRenderer
import `fun`.kirari.hanako.automation.BubbleState
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import `fun`.kirari.hanako.R
import `fun`.kirari.hanako.capture.MediaProjectionForegroundService
import `fun`.kirari.hanako.capture.ProjectionPermissionActivity
import `fun`.kirari.hanako.debug.AppDebugLogStore
import `fun`.kirari.hanako.easeOutCubic
import `fun`.kirari.hanako.copyToClipboard
import `fun`.kirari.hanako.copyToClipboardWithToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val logTag = "HanakoOverlaySvc"
    private val dispatcher = ServiceLifecycleDispatcher(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceViewModelStore = ViewModelStore()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle

    override val savedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = serviceViewModelStore

    private lateinit var windowManager: WindowManager
    private var bubbleView: FrameLayout? = null
    private var bubbleSurfaceView: FrameLayout? = null
    private var bubbleIconView: ImageView? = null
    private var bubbleTextView: TextView? = null
    private var bubbleSpinnerView: ProgressBar? = null
    private var bubbleColorAnimator: ValueAnimator? = null
    private var bubbleCompletionResetJob: Job? = null
    private var bubbleLetterVibrationJob: Job? = null
    private var lastHandledCompletionId: String? = null
    private var panelView: FrameLayout? = null
    private var panelContentView: androidx.compose.ui.platform.ComposeView? = null
    private var panelHandleView: FrameLayout? = null
    private lateinit var overlayViewModel: OverlayViewModel
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelHandleParams: WindowManager.LayoutParams? = null
    private var panelScreenHeightPx: Int = 0
    private var panelHeightPx: Int = 0
    private var panelDockHeightPx: Int = 0
    private var panelCurrentHeightPx: Int = 0
    private var panelHandleHeightPx: Int = 0
    private var panelHandleWidthPx: Int = 0
    private var panelAnimationJob: Job? = null
    private var panelClosing = false

    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching {
            overlayViewModel = ViewModelProvider(
                this,
                OverlayViewModel.factory(applicationContext)
            )[OverlayViewModel::class.java]
            OverlayRuntimeState.setRunning(true)
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
            showBubble()
            observeUiState()
        }.onFailure {
            AppDebugLogStore.e(logTag, "Overlay initialization failed", it)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatcher.onServicePreSuperOnStart()
        when (intent?.action) {
            ACTION_STOP -> {
                dismissPanel()
                stopSelf()
            }

            else -> {
                intent?.getStringExtra(ProjectionPermissionActivity.EXTRA_LAUNCH_MODE)
                    ?.let { runCatching { OverlayLaunchMode.valueOf(it) }.getOrNull() }
                    ?.let(overlayViewModel::setLaunchMode)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        dismissPanel()
        bubbleColorAnimator?.cancel()
        bubbleColorAnimator = null
        bubbleCompletionResetJob?.cancel()
        bubbleCompletionResetJob = null
        bubbleLetterVibrationJob?.cancel()
        bubbleLetterVibrationJob = null
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        bubbleSurfaceView = null
        bubbleIconView = null
        bubbleTextView = null
        bubbleSpinnerView = null
        OverlayRuntimeState.setRunning(false)
        serviceViewModelStore.clear()
        serviceScope.cancel()
        super.onDestroy()
        dispatcher.onServicePreSuperOnDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeUiState() {
        serviceScope.launch {
            overlayViewModel.uiState.collect { state ->
                AppDebugLogStore.d(
                    logTag,
                    "uiState launchMode=${state.launchMode} autoRunState=${state.autoRunState} bubble=${state.bubbleState::class.simpleName} sheetVisible=${state.sheetVisible} working=${state.working} resultId=${state.result?.id} error=${state.error}"
                )
                updateBubbleAppearance(state.bubbleState, state.launchMode)
                if (state.sheetVisible) {
                    showOrUpdatePanel(state.sheetMode)
                } else {
                    hidePanelWithAnimation()
                }
                if (state.autoRunState == AutoRunState.COMPLETED) {
                    val completionId = state.result?.id ?: state.autoCopiedLabel
                    if (completionId != null && completionId != lastHandledCompletionId) {
                        AppDebugLogStore.i(
                            logTag,
                            "auto completion handled completionId=$completionId copiedLabel=${state.autoCopiedLabel} bubble=${state.bubbleState::class.simpleName}"
                        )
                        lastHandledCompletionId = completionId
                        state.autoCopiedLabel?.let { copyToClipboard(this@OverlayService, "Hanako Auto Copy", it) }
                        if (state.settings.automation.completionNotificationEnabled) {
                            notifyAutomationCompleted(state.autoCopiedLabel)
                        }
                        bubbleCompletionResetJob?.cancel()
                        bubbleCompletionResetJob = serviceScope.launch {
                            delay(AUTO_COMPLETED_VISIBLE_MS)
                            overlayViewModel.consumeAutoCompletedState()
                        }
                    }
                    if (state.settings.automation.staticModeEnabled && !state.pendingVibrationLetters.isNullOrBlank()) {
                        val letters = state.pendingVibrationLetters
                        bubbleLetterVibrationJob?.cancel()
                        bubbleLetterVibrationJob = serviceScope.launch {
                            AppDebugLogStore.i(logTag, "start pending letter vibration letters=$letters")
                            vibrateLetters(letters, state.settings.automation)
                            overlayViewModel.consumePendingVibrationLetters()
                        }
                    }
                } else {
                    if (bubbleCompletionResetJob != null) {
                        AppDebugLogStore.d(logTag, "auto completion reset job cancelled because state=${state.autoRunState}")
                    }
                    bubbleCompletionResetJob?.cancel()
                    bubbleCompletionResetJob = null
                    bubbleLetterVibrationJob?.cancel()
                    bubbleLetterVibrationJob = null
                }
            }
        }
    }

    private fun showBubble() {
        val rootSizePx = (56f * resources.displayMetrics.density).roundToInt()
        val surfaceSizePx = (40f * resources.displayMetrics.density).roundToInt()
        val iconSizePx = (20f * resources.displayMetrics.density).roundToInt()
        val spinnerSizePx = (54f * resources.displayMetrics.density).roundToInt()
        val params = WindowManager.LayoutParams(
            rootSizePx,
            rootSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 32
            y = 360
        }
        bubbleParams = params
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val spinnerView = ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
            layoutParams = FrameLayout.LayoutParams(spinnerSizePx, spinnerSizePx, Gravity.CENTER)
            scaleX = 1.00f
            scaleY = 1.00f
            isIndeterminate = true
            visibility = View.GONE
            alpha = 0f
        }
        val iconView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        val textView = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(surfaceSizePx, surfaceSizePx, Gravity.CENTER)
            gravity = Gravity.CENTER
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            visibility = View.GONE
        }
        val surfaceView = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(surfaceSizePx, surfaceSizePx, Gravity.CENTER)
            addView(iconView)
            addView(textView)
        }
        val view = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(rootSizePx, rootSizePx)
            addView(spinnerView)
            addView(surfaceView)
            var downRawX = 0f
            var downRawY = 0f
            var startX = 0
            var startY = 0
            var dragging = false
            var longPressTriggered = false
            val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
            val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
            var lastTapTime = 0L
            var doubleTapDetected = false
            
            val longPressRunnable = Runnable {
                if (!dragging) {
                    longPressTriggered = true
                    performHapticFeedback(
                        HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                    vibrateShort()
                    overlayViewModel.handleLongPress()
                }
            }
            
            val doubleTapTimeoutRunnable = Runnable {
                if (!doubleTapDetected && !longPressTriggered) {
                    // 单击超时，执行单击操作
                    overlayViewModel.handleSingleTap()
                }
                doubleTapDetected = false
            }
            
            setOnTouchListener { _: View, event: MotionEvent ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startX = params.x
                        startY = params.y
                        dragging = false
                        longPressTriggered = false
                        doubleTapDetected = false
                        removeCallbacks(longPressRunnable)
                        removeCallbacks(doubleTapTimeoutRunnable)
                        postDelayed(longPressRunnable, longPressTimeout)
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downRawX).toInt()
                        val dy = (event.rawY - downRawY).toInt()
                        if (!dragging && (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)) {
                            dragging = true
                            removeCallbacks(longPressRunnable)
                            removeCallbacks(doubleTapTimeoutRunnable)
                        }
                        if (dragging) {
                            params.x = startX + dx
                            params.y = startY + dy
                            windowManager.updateViewLayout(this, params)
                        }
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        removeCallbacks(longPressRunnable)
                        if (!dragging && !longPressTriggered) {
                            val currentTime = android.os.SystemClock.uptimeMillis()
                            val timeSinceLastTap = currentTime - lastTapTime
                            
                            if (timeSinceLastTap < doubleTapTimeout) {
                                // 双击检测
                                doubleTapDetected = true
                                removeCallbacks(doubleTapTimeoutRunnable)
                                overlayViewModel.handleDoubleTap()
                            } else {
                                // 可能是单击或双击的第一次，等待超时
                                lastTapTime = currentTime
                                postDelayed(doubleTapTimeoutRunnable, doubleTapTimeout)
                            }
                        }
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        removeCallbacks(longPressRunnable)
                        removeCallbacks(doubleTapTimeoutRunnable)
                        true
                    }

                    else -> false
                }
            }
        }
        bubbleSurfaceView = surfaceView
        bubbleIconView = iconView
        bubbleTextView = textView
        bubbleSpinnerView = spinnerView
        windowManager.addView(view, params)
        bubbleView = view
        updateBubbleAppearance(BubbleState.Idle, OverlayLaunchMode.NORMAL)
    }

    private fun showOrUpdatePanel(mode: OverlaySheetMode) {
        panelClosing = false
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val screenHeightPx = metrics.heightPixels
        val density = resources.displayMetrics.density
        val dockHeightPx = resources.displayMetrics.density.times(SheetDockOffset.value).roundToInt()
        val targetHeightPx = (screenHeightPx * if (mode == OverlaySheetMode.CROP) 0.88f else 0.92f)
            .roundToInt()
            .coerceAtLeast(dockHeightPx)

        panelScreenHeightPx = screenHeightPx
        panelHeightPx = targetHeightPx
        panelDockHeightPx = dockHeightPx
        panelHandleHeightPx = (28f * density).roundToInt()
        panelHandleWidthPx = (88f * density).roundToInt()
        if (panelView == null) {
            panelCurrentHeightPx = dockHeightPx
        } else {
            panelCurrentHeightPx = panelCurrentHeightPx.coerceIn(dockHeightPx, targetHeightPx)
        }

        val params = panelParams ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            panelCurrentHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = screenHeightPx - panelCurrentHeightPx
        }
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = panelCurrentHeightPx
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = (screenHeightPx - panelCurrentHeightPx).coerceAtLeast(0)
        panelParams = params

        val handleParams = panelHandleParams ?: WindowManager.LayoutParams(
            panelHandleWidthPx,
            panelHandleHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = screenHeightPx - panelCurrentHeightPx
        }
        handleParams.width = panelHandleWidthPx
        handleParams.height = panelHandleHeightPx
        handleParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        handleParams.x = 0
        handleParams.y = panelHandleY(screenHeightPx, panelCurrentHeightPx)
        panelHandleParams = handleParams

        if (panelView == null) {
            val composeView = androidx.compose.ui.platform.ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@OverlayService)
                setViewTreeViewModelStoreOwner(this@OverlayService)
                setViewTreeSavedStateRegistryOwner(this@OverlayService)
                setContent {
                    `fun`.kirari.hanako.ui.theme.HanakoTheme {
                        OverlayPanel(
                            viewModel = overlayViewModel,
                            onDismiss = { overlayViewModel.closeSheet() },
                            panelHeightPx = panelHeightPx
                        )
                    }
                }
            }
            val panelRoot = FrameLayout(this).apply {
                setViewTreeLifecycleOwner(this@OverlayService)
                setViewTreeViewModelStoreOwner(this@OverlayService)
                setViewTreeSavedStateRegistryOwner(this@OverlayService)
                clipChildren = true
                clipToPadding = true
                addView(
                    composeView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        targetHeightPx
                    )
                )
            }
            val handleView = createPanelHandleView()
            panelView = panelRoot
            panelContentView = composeView
            panelHandleView = handleView
            windowManager.addView(panelRoot, params)
            windowManager.addView(handleView, handleParams)
            applyPanelHeight(panelCurrentHeightPx)
            animatePanelHeight(
                fromHeightPx = panelCurrentHeightPx,
                toHeightPx = targetHeightPx
            )
        } else {
            runCatching { windowManager.updateViewLayout(panelView, params) }
            runCatching { windowManager.updateViewLayout(panelHandleView, handleParams) }
            applyPanelHeight(panelCurrentHeightPx)
        }
    }

    private fun createPanelHandleView(): FrameLayout {
        var dragStartRawY = 0f
        var dragStartHeightPx = 0
        return FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                FrameLayout(this@OverlayService).apply {
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(86, 86, 86))
                        cornerRadius = 999f * resources.displayMetrics.density
                    }
                },
                FrameLayout.LayoutParams(
                    (68f * resources.displayMetrics.density).roundToInt(),
                    (8f * resources.displayMetrics.density).roundToInt()
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = (10f * resources.displayMetrics.density).roundToInt()
                }
            )
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        panelAnimationJob?.cancel()
                        dragStartRawY = event.rawY
                        dragStartHeightPx = panelCurrentHeightPx
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val nextHeight = (dragStartHeightPx - (event.rawY - dragStartRawY))
                            .roundToInt()
                        updatePanelHeight(nextHeight)
                        true
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> true

                    else -> false
                }
            }
        }
    }

    private fun updatePanelHeight(heightPx: Int) {
        if (panelDockHeightPx <= 0 || panelHeightPx <= 0) return
        applyPanelHeight(heightPx.coerceIn(panelDockHeightPx, panelHeightPx))
    }

    private fun applyPanelHeight(heightPx: Int) {
        val view = panelView ?: return
        val params = panelParams
        val handleParams = panelHandleParams
        if (panelHeightPx <= 0 || panelScreenHeightPx <= 0) return
        panelCurrentHeightPx = heightPx.coerceIn(0, panelHeightPx)
        if (params != null) {
            params.y = (panelScreenHeightPx - panelCurrentHeightPx).coerceAtLeast(0)
            params.height = panelCurrentHeightPx.coerceAtLeast(1)
            runCatching { windowManager.updateViewLayout(view, params) }
        }
        if (handleParams != null) {
            handleParams.y = panelHandleY(panelScreenHeightPx, panelCurrentHeightPx)
            runCatching { windowManager.updateViewLayout(panelHandleView, handleParams) }
        }
    }

    private fun panelHandleY(screenHeightPx: Int, currentHeightPx: Int): Int {
        val offsetPx = (PanelHandleYOffset.value * resources.displayMetrics.density).roundToInt()
        return (screenHeightPx - currentHeightPx - offsetPx).coerceAtLeast(0)
    }

    private fun animatePanelHeight(
        fromHeightPx: Int,
        toHeightPx: Int,
        onEnd: (() -> Unit)? = null
    ) {
        panelAnimationJob?.cancel()
        panelAnimationJob = serviceScope.launch {
            val start = fromHeightPx.coerceIn(0, panelHeightPx.coerceAtLeast(fromHeightPx))
            val end = toHeightPx.coerceIn(0, panelHeightPx.coerceAtLeast(toHeightPx))
            animatePanelHeightSegment(
                startHeightPx = start,
                endHeightPx = end,
                durationMs = SheetAnimationDurationMs,
                easing = ::easeOutCubic
            )
            updatePanelHeightAllowZero(end)
            onEnd?.invoke()
        }
    }

    private suspend fun animatePanelHeightSegment(
        startHeightPx: Int,
        endHeightPx: Int,
        durationMs: Int,
        easing: (Float) -> Float
    ) {
        val startTimeMs = android.os.SystemClock.uptimeMillis()
        while (currentCoroutineContext().isActive) {
            val elapsed = android.os.SystemClock.uptimeMillis() - startTimeMs
            val fraction = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            val eased = easing(fraction)
            val height = (startHeightPx + (endHeightPx - startHeightPx) * eased).roundToInt()
            updatePanelHeightAllowZero(height)
            if (fraction >= 1f) break
            kotlinx.coroutines.delay(16L)
        }
        updatePanelHeightAllowZero(endHeightPx)
    }

    private fun updatePanelHeightAllowZero(heightPx: Int) {
        applyPanelHeight(heightPx)
    }

    private fun hidePanelWithAnimation() {
        if (panelClosing) return
        val view = panelView ?: return
        panelClosing = true
        val startHeight = panelCurrentHeightPx
        animatePanelHeight(
            fromHeightPx = startHeight,
            toHeightPx = 0
        ) {
            if (panelView === view) {
                removePanelNow()
            }
        }
    }

    private fun updateBubbleAppearance(bubbleState: BubbleState, launchMode: OverlayLaunchMode) {
        AppDebugLogStore.d(
            logTag,
            "updateBubbleAppearance state=${bubbleState::class.simpleName} launchMode=$launchMode"
        )
        val bubble = bubbleSurfaceView ?: return
        val icon = bubbleIconView ?: return
        val textView = bubbleTextView
        val spinner = bubbleSpinnerView
        val staticModeEnabled = overlayViewModel.uiState.value.settings.automation.staticModeEnabled
        val appearance = BubbleRenderer.render(bubbleState, launchMode, this, staticModeEnabled)
        val bubbleLayoutParams = bubble.layoutParams as? FrameLayout.LayoutParams
        if (bubbleLayoutParams != null) {
            val sizePx = (40f * resources.displayMetrics.density * appearance.sizeScale).roundToInt()
            if (bubbleLayoutParams.width != sizePx || bubbleLayoutParams.height != sizePx) {
                bubbleLayoutParams.width = sizePx
                bubbleLayoutParams.height = sizePx
                bubbleLayoutParams.gravity = Gravity.CENTER
                bubble.layoutParams = bubbleLayoutParams
                bubble.requestLayout()
            }
        }
        
        val existing = bubble.background as? GradientDrawable
        val drawable = existing ?: GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke((1.5f * resources.displayMetrics.density).roundToInt(), Color.WHITE)
        }
        val startColor = existing?.color?.defaultColor ?: appearance.backgroundColor
        bubbleColorAnimator?.cancel()
        if (appearance.animateTransitions) {
            bubbleColorAnimator = ValueAnimator.ofArgb(startColor, appearance.backgroundColor).apply {
                duration = 220L
                addUpdateListener { animator ->
                    drawable.setColor(animator.animatedValue as Int)
                    bubble.background = drawable
                }
                start()
            }
        } else {
            drawable.setColor(appearance.backgroundColor)
            bubble.background = drawable
        }

        if (appearance.letters != null) {
            textView?.text = appearance.letters
            textView?.setTextColor(appearance.iconTint)
            textView?.visibility = View.VISIBLE
            icon.visibility = View.GONE
        } else if (!appearance.showIcon) {
            textView?.visibility = View.GONE
            icon.visibility = View.GONE
        } else {
            textView?.visibility = View.GONE
            icon.visibility = View.VISIBLE
            if (appearance.animateTransitions && icon.tag != appearance.iconRes) {
                icon.animate().cancel()
                icon.animate()
                    .alpha(0f)
                    .scaleX(0.82f)
                    .scaleY(0.82f)
                    .setDuration(120L)
                    .withEndAction {
                        icon.setImageDrawable(ContextCompat.getDrawable(this, appearance.iconRes))
                        icon.imageTintList = ColorStateList.valueOf(appearance.iconTint)
                        icon.tag = appearance.iconRes
                        icon.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(180L)
                            .start()
                    }
                    .start()
            } else {
                icon.setImageDrawable(ContextCompat.getDrawable(this, appearance.iconRes))
                icon.imageTintList = ColorStateList.valueOf(appearance.iconTint)
                icon.alpha = 1f
                icon.scaleX = 1f
                icon.scaleY = 1f
            }
        }

        spinner?.apply {
            // 设置 spinner 颜色
            val drawable = indeterminateDrawable?.mutate()
            if (drawable != null) {
                drawable.setColorFilter(appearance.spinnerColor, PorterDuff.Mode.SRC_IN)
                indeterminateDrawable = drawable
            }
            animate().cancel()
            if (appearance.showSpinner) {
                visibility = View.VISIBLE
                if (appearance.animateTransitions) {
                    animate().alpha(1f).setDuration(160L).start()
                } else {
                    alpha = 1f
                }
            } else {
                if (appearance.animateTransitions) {
                    animate()
                        .alpha(0f)
                        .setDuration(160L)
                        .withEndAction { visibility = View.GONE }
                        .start()
                } else {
                    alpha = 0f
                    visibility = View.GONE
                }
            }
        }
    }

    private fun dismissPanel() {
        panelAnimationJob?.cancel()
        panelAnimationJob = null
        panelClosing = false
        removePanelNow()
    }

    private fun removePanelNow() {
        panelHandleView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        panelView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        panelHandleView = null
        panelContentView = null
        panelView = null
        panelHandleParams = null
        panelParams = null
        panelCurrentHeightPx = 0
        panelClosing = false
    }

    companion object {
        const val ACTION_STOP = "fun.kirari.hanako.overlay.STOP"
        internal const val CHANNEL_ID = "overlay_service"
        internal const val AUTOMATION_CHANNEL_ID = "overlay_automation"
        private const val NOTIFICATION_ID = 1001
        internal const val AUTOMATION_COMPLETE_NOTIFICATION_ID = 1002
        private const val AUTO_COMPLETED_VISIBLE_MS = 2800L
    }
}
