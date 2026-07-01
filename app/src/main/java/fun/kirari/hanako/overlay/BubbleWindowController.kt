package `fun`.kirari.hanako.overlay

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
import `fun`.kirari.hanako.automation.BubbleRenderer
import `fun`.kirari.hanako.automation.BubbleState
import `fun`.kirari.hanako.data.BubbleAppearanceSettings
import `fun`.kirari.hanako.debug.AppDebugLogStore
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
    private var iconView: ImageView? = null
    private var textView: TextView? = null
    private var spinnerView: ProgressBar? = null
    private var colorAnimator: ValueAnimator? = null

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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 32
            y = 360
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
        val view = createBubbleRoot(rootSizePx, params, spinner, surface, text)
        surfaceView = surface
        iconView = icon
        textView = text
        spinnerView = spinner
        windowManager.addView(view, params)
        bubbleView = view
        update(BubbleState.Idle, OverlayLaunchMode.NORMAL)
    }

    fun update(bubbleState: BubbleState, launchMode: OverlayLaunchMode) {
        AppDebugLogStore.d(logTag, "update state=${bubbleState::class.simpleName} launchMode=$launchMode")
        val bubble = surfaceView ?: return
        val icon = iconView ?: return
        val text = textView
        val spinner = spinnerView
        val settings = bubbleAppearanceSettings()
        val appearance = BubbleRenderer.render(
            state = bubbleState,
            launchMode = launchMode,
            context = context,
            staticModeEnabled = isStaticModeEnabled()
        )
        val overallAlpha = (settings.overallOpacity / 100f).coerceIn(0f, 1f)
        updateRootSize(settings)
        val bubbleLayoutParams = bubble.layoutParams as? FrameLayout.LayoutParams
        if (bubbleLayoutParams != null) {
            val sizePx = (
                settings.bubbleDiameterDp *
                    context.resources.displayMetrics.density *
                    appearance.sizeScale
                ).roundToInt()
            if (bubbleLayoutParams.width != sizePx || bubbleLayoutParams.height != sizePx) {
                bubbleLayoutParams.width = sizePx
                bubbleLayoutParams.height = sizePx
                bubbleLayoutParams.gravity = Gravity.CENTER
                bubble.layoutParams = bubbleLayoutParams
                bubble.requestLayout()
            }
        }
        bubble.alpha = overallAlpha

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
    }

    fun destroy() {
        colorAnimator?.cancel()
        colorAnimator = null
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        surfaceView = null
        iconView = null
        textView = null
        spinnerView = null
    }

    private fun createBubbleRoot(
        rootSizePx: Int,
        params: WindowManager.LayoutParams,
        spinner: ProgressBar,
        surface: FrameLayout,
        text: TextView
    ): FrameLayout {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        return FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(rootSizePx, rootSizePx)
            addView(spinner)
            addView(surface)
            addView(text)
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
        appearance: `fun`.kirari.hanako.automation.BubbleAppearance,
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
                    .setDuration(120L)
                    .withEndAction {
                        icon.setImageDrawable(ContextCompat.getDrawable(context, appearance.iconRes))
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
        appearance: `fun`.kirari.hanako.automation.BubbleAppearance,
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
}
