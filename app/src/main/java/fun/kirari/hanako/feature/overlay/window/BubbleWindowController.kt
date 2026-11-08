package `fun`.kirari.hanako.feature.overlay.window

import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
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
import androidx.core.content.ContextCompat
import `fun`.kirari.hanako.feature.overlay.window.BubbleRenderer
import `fun`.kirari.hanako.feature.overlay.state.BubbleState
import `fun`.kirari.hanako.core.data.BubbleAppearanceSettings
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.feature.overlay.window.AntiScreenshotHelper
import `fun`.kirari.hanako.feature.overlay.window.overlayWindowType
import kotlin.math.roundToInt

internal class BubbleWindowController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onSingleTap: () -> Unit,
    private val onDoubleTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onLongPressHaptic: () -> Unit,
    private val isStaticModeEnabled: () -> Boolean,
    private val bubbleAppearanceSettings: () -> BubbleAppearanceSettings
) {
    private val logTag = "HanakoBubbleWindow"
    private var bubbleView: FrameLayout? = null
    private var surfaceView: FrameLayout? = null
    private var shadowView: View? = null
    private var iconView: ImageView? = null
    private var textView: TextView? = null
    private var spinnerView: ProgressBar? = null
    private var colorAnimator: ValueAnimator? = null
    private var currentParams: WindowManager.LayoutParams? = null
    private var countView: TextView? = null

    fun show() {
        if (bubbleView != null) return
        val density = context.resources.displayMetrics.density
        val initialSettings = bubbleAppearanceSettings()
        val rootSizePx = computeRootSizePx(initialSettings)
        val surfaceSizePx = (initialSettings.bubbleDiameterDp * density).roundToInt()
        val iconSizePx = (initialSettings.bubbleDiameterDp * density * 0.5f).roundToInt()
        val spinnerSizePx = (initialSettings.spinnerDiameterDp * density).roundToInt()
        val letterSizePx = computeLetterLayerSizePx(initialSettings)
        val params = WindowManager.LayoutParams(
            rootSizePx,
            rootSizePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 32
            y = 360
        }
        val shadowView = android.view.View(context).apply {
            layoutParams = FrameLayout.LayoutParams(0, 0, Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#20000000"))
            }
            translationY = 1.5f * density
        }
        val countSizePx = (18f * density).roundToInt()
        val countView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(countSizePx, countSizePx).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = (2f * density).roundToInt()
                marginEnd = (2f * density).roundToInt()
            }
            gravity = Gravity.CENTER
            textSize = 10f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#BA1A1A"))
                setStroke((1.5f * density).roundToInt(), android.graphics.Color.WHITE)
            }
            visibility = View.GONE
        }
        val spinner = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
            layoutParams = FrameLayout.LayoutParams(spinnerSizePx, spinnerSizePx, Gravity.CENTER)
            isIndeterminate = true
            visibility = View.GONE
            alpha = 0f
        }
        val icon = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(iconSizePx, iconSizePx, Gravity.CENTER)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        val text = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(letterSizePx, letterSizePx, Gravity.CENTER)
            gravity = Gravity.CENTER
            textSize = initialSettings.letterTextSizeDp
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            includeFontPadding = false
            visibility = View.GONE
        }
        val surface = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(surfaceSizePx, surfaceSizePx, Gravity.CENTER)
            addView(icon)
        }
        val view = createBubbleRoot(rootSizePx, params, shadowView, spinner, surface, text, countView)
        surfaceView = surface
        this.shadowView = shadowView
        iconView = icon
        textView = text
        spinnerView = spinner
        this.countView = countView
        currentParams = params
        windowManager.addView(view, params)
        AntiScreenshotHelper.applyTo(view)
        bubbleView = view
        update(BubbleState.Idle, CaptureLaunchMode.NORMAL)
    }

    fun update(bubbleState: BubbleState, launchMode: CaptureLaunchMode) {
        AppDebugLogStore.d(logTag, "update state=${bubbleState::class.simpleName} launchMode=$launchMode")
        val bubble = surfaceView ?: return
        val icon = iconView ?: return
        val text = textView
        val spinner = spinnerView
        val settings = if (bubbleState is BubbleState.MenuExpanded) {
            BubbleAppearanceSettings()
        } else {
            bubbleAppearanceSettings()
        }
        val appearance = BubbleRenderer.render(
            state = bubbleState,
            launchMode = launchMode,
            context = context,
            staticModeEnabled = isStaticModeEnabled()
        )
        val overallAlpha = (settings.overallOpacity / 100f).coerceIn(0f, 1f)
        updateRootSize(settings)
        val sizePx = (
            settings.bubbleDiameterDp *
                context.resources.displayMetrics.density *
                appearance.sizeScale
            ).roundToInt()
        val bubbleLayoutParams = bubble.layoutParams as? FrameLayout.LayoutParams
        if (bubbleLayoutParams != null) {
            if (bubbleLayoutParams.width != sizePx || bubbleLayoutParams.height != sizePx) {
                bubbleLayoutParams.width = sizePx
                bubbleLayoutParams.height = sizePx
                bubbleLayoutParams.gravity = Gravity.CENTER
                bubble.layoutParams = bubbleLayoutParams
                bubble.requestLayout()
            }
        }
        bubble.alpha = overallAlpha
        updateShadow(sizePx, overallAlpha)

        val existing = bubble.background as? GradientDrawable
        val drawable = existing ?: GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke((1.5f * context.resources.displayMetrics.density).roundToInt(), android.graphics.Color.WHITE)
        }
        val startColor = existing?.color?.defaultColor ?: appearance.backgroundColor
        colorAnimator?.cancel()
        if (appearance.animateTransitions) {
            colorAnimator = ValueAnimator.ofArgb(startColor, appearance.backgroundColor).apply {
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

        updateIconAndText(icon, text, appearance, settings, overallAlpha)
        updateSpinner(spinner, appearance, settings, overallAlpha)
        updateCaptureCount(countView, appearance)
    }

    fun destroy() {
        colorAnimator?.cancel()
        colorAnimator = null
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        surfaceView = null
        shadowView = null
        iconView = null
        textView = null
        spinnerView = null
        countView = null
        currentParams = null
    }

    /**
     * 返回气泡中心在屏幕上的坐标，用于菜单定位。
     */
    fun getBubbleCenter(): Pair<Int, Int>? {
        val params = currentParams ?: return null
        return Pair(params.x + params.width / 2, params.y + params.height / 2)
    }

    private fun createBubbleRoot(
        rootSizePx: Int,
        params: WindowManager.LayoutParams,
        shadowView: android.view.View,
        spinner: ProgressBar,
        surface: FrameLayout,
        text: TextView,
        countView: TextView
    ): FrameLayout {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        return FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(rootSizePx, rootSizePx)
            addView(shadowView)
            addView(spinner)
            addView(surface)
            addView(text)
            addView(countView)
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
                    onLongPressHaptic()
                    onLongPress()
                }
            }

            val doubleTapTimeoutRunnable = Runnable {
                if (!doubleTapDetected && !longPressTriggered) {
                    onSingleTap()
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
                                doubleTapDetected = true
                                removeCallbacks(doubleTapTimeoutRunnable)
                                onDoubleTap()
                            } else {
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
    }

    private fun updateIconAndText(
        icon: ImageView,
        text: TextView?,
        appearance: `fun`.kirari.hanako.feature.overlay.window.BubbleAppearance,
        settings: BubbleAppearanceSettings,
        overallAlpha: Float
    ) {
        val iconSizePx = (
            settings.bubbleDiameterDp *
                context.resources.displayMetrics.density *
                0.5f *
                appearance.sizeScale
            ).roundToInt()
        (icon.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.width != iconSizePx || params.height != iconSizePx) {
                params.width = iconSizePx
                params.height = iconSizePx
                params.gravity = Gravity.CENTER
                icon.layoutParams = params
            }
        }
        text?.apply {
            val letterSizePx = computeLetterLayerSizePx(settings)
            (layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                if (params.width != letterSizePx || params.height != letterSizePx) {
                    params.width = letterSizePx
                    params.height = letterSizePx
                    params.gravity = Gravity.CENTER
                    layoutParams = params
                    requestLayout()
                }
            }
            textSize = settings.letterTextSizeDp
            alpha = (settings.letterOpacity / 100f).coerceIn(0f, 1f)
        }
        if (appearance.letters != null) {
            text?.text = appearance.letters
            text?.setTextColor(appearance.iconTint)
            text?.visibility = View.VISIBLE
            icon.visibility = View.GONE
        } else if (!appearance.showIcon) {
            text?.visibility = View.GONE
            icon.visibility = View.GONE
        } else {
            text?.visibility = View.GONE
            icon.visibility = View.VISIBLE
            if (appearance.animateTransitions && icon.tag != appearance.iconRes) {
                icon.animate().cancel()
                icon.animate()
                    .alpha(0f)
                    .scaleX(0.82f)
                    .scaleY(0.82f)
                    .rotation(-45f)
                    .setDuration(90L)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction {
                        icon.setImageDrawable(ContextCompat.getDrawable(context, appearance.iconRes))
                        icon.imageTintList = ColorStateList.valueOf(appearance.iconTint)
                        icon.tag = appearance.iconRes
                        icon.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .rotation(0f)
                            .setDuration(180L)
                            .setInterpolator(android.view.animation.OvershootInterpolator(1.6f))
                            .start()
                    }
                    .start()
            } else {
                icon.setImageDrawable(ContextCompat.getDrawable(context, appearance.iconRes))
                icon.imageTintList = ColorStateList.valueOf(appearance.iconTint)
                icon.alpha = 1f
                icon.scaleX = 1f
                icon.scaleY = 1f
            }
        }
    }

    private fun updateSpinner(
        spinner: ProgressBar?,
        appearance: `fun`.kirari.hanako.feature.overlay.window.BubbleAppearance,
        settings: BubbleAppearanceSettings,
        overallAlpha: Float
    ) {
        spinner?.apply {
            (layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                val spinnerSizePx = (settings.spinnerDiameterDp * context.resources.displayMetrics.density).roundToInt()
                if (params.width != spinnerSizePx || params.height != spinnerSizePx) {
                    params.width = spinnerSizePx
                    params.height = spinnerSizePx
                    params.gravity = Gravity.CENTER
                    layoutParams = params
                    requestLayout()
                }
            }
            val drawable = indeterminateDrawable?.mutate()
            if (drawable != null) {
                drawable.setColorFilter(appearance.spinnerColor, PorterDuff.Mode.SRC_IN)
                indeterminateDrawable = drawable
            }
            animate().cancel()
            if (appearance.showSpinner) {
                visibility = View.VISIBLE
                if (appearance.animateTransitions) {
                    animate().alpha(overallAlpha).setDuration(160L).start()
                } else {
                    alpha = overallAlpha
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

    private fun updateRootSize(settings: BubbleAppearanceSettings) {
        val root = bubbleView ?: return
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        val nextSize = computeRootSizePx(settings)
        if (params.width == nextSize && params.height == nextSize) return
        params.width = nextSize
        params.height = nextSize
        windowManager.updateViewLayout(root, params)
    }

    private fun updateShadow(bubbleSizePx: Int, overallAlpha: Float) {
        val shadow = shadowView ?: return
        shadow.visibility = if (bubbleSizePx > 0) View.VISIBLE else View.GONE
        shadow.alpha = 0.45f * overallAlpha
        val sizePx = bubbleSizePx + (3f * context.resources.displayMetrics.density).roundToInt()
        val params = shadow.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.width != sizePx || params.height != sizePx) {
            params.width = sizePx
            params.height = sizePx
            shadow.layoutParams = params
        }
    }

    private fun computeRootSizePx(settings: BubbleAppearanceSettings): Int {
        val density = context.resources.displayMetrics.density
        val contentSizePx = maxOf(
            settings.bubbleDiameterDp,
            settings.spinnerDiameterDp,
            settings.letterTextSizeDp * 1.8f
        ) * density
        val paddingPx = 16f * density
        return (contentSizePx + paddingPx).roundToInt()
    }

    private fun computeLetterLayerSizePx(settings: BubbleAppearanceSettings): Int {
        val density = context.resources.displayMetrics.density
        return (settings.letterTextSizeDp * 1.8f * density).roundToInt()
    }

    private fun updateCaptureCount(
        countView: TextView?,
        appearance: `fun`.kirari.hanako.feature.overlay.window.BubbleAppearance
    ) {
        countView?.apply {
            if (appearance.showCaptureCount) {
                text = appearance.captureCount.toString()
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
    }
}
