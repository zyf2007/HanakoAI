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
import `fun`.kirari.hanako.debug.AppDebugLogStore
import kotlin.math.roundToInt

internal class BubbleWindowController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onSingleTap: () -> Unit,
    private val onDoubleTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onLongPressHaptic: () -> Unit,
    private val isStaticModeEnabled: () -> Boolean
) {
    private val logTag = "HanakoBubbleWindow"
    private var bubbleView: FrameLayout? = null
    private var surfaceView: FrameLayout? = null
    private var iconView: ImageView? = null
    private var textView: TextView? = null
    private var spinnerView: ProgressBar? = null
    private var colorAnimator: ValueAnimator? = null
    private var currentParams: WindowManager.LayoutParams? = null
    private var countView: TextView? = null

    fun show() {
        if (bubbleView != null) return
        val density = context.resources.displayMetrics.density
        val rootSizePx = (56f * density).roundToInt()
        val surfaceSizePx = (40f * density).roundToInt()
        val iconSizePx = (20f * density).roundToInt()
        val spinnerSizePx = (54f * density).roundToInt()
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
        val shadowSizePx = surfaceSizePx + (3f * density).roundToInt()
        val shadowView = android.view.View(context).apply {
            layoutParams = FrameLayout.LayoutParams(shadowSizePx, shadowSizePx, Gravity.CENTER)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#20000000"))
            }
            translationY = 1.5f * density
            alpha = 0.45f
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
            layoutParams = FrameLayout.LayoutParams(surfaceSizePx, surfaceSizePx, Gravity.CENTER)
            gravity = Gravity.CENTER
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            visibility = View.GONE
        }
        val surface = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(surfaceSizePx, surfaceSizePx, Gravity.CENTER)
            addView(icon)
            addView(text)
        }
        val view = createBubbleRoot(rootSizePx, params, shadowView, spinner, surface, countView)
        surfaceView = surface
        iconView = icon
        textView = text
        spinnerView = spinner
        this.countView = countView
        currentParams = params
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
        val appearance = BubbleRenderer.render(
            state = bubbleState,
            launchMode = launchMode,
            context = context,
            staticModeEnabled = isStaticModeEnabled()
        )
        val bubbleLayoutParams = bubble.layoutParams as? FrameLayout.LayoutParams
        if (bubbleLayoutParams != null) {
            val sizePx = (40f * context.resources.displayMetrics.density * appearance.sizeScale).roundToInt()
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

        updateIconAndText(icon, text, appearance)
        updateSpinner(spinner, appearance)
        updateCaptureCount(countView, appearance)
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
        countView = null
        currentParams = null
    }

    /**
     * 返回气泡中心在屏幕上的坐标，用于菜单定位。
     */
    fun getBubbleCenter(): Pair<Int, Int>? {
        val params = currentParams ?: return null
        val density = context.resources.displayMetrics.density
        val rootSizePx = (56f * density).roundToInt()
        return Pair(params.x + rootSizePx / 2, params.y + rootSizePx / 2)
    }

    private fun createBubbleRoot(
        rootSizePx: Int,
        params: WindowManager.LayoutParams,
        shadowView: android.view.View,
        spinner: ProgressBar,
        surface: FrameLayout,
        countView: TextView
    ): FrameLayout {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        return FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(rootSizePx, rootSizePx)
            addView(shadowView)
            addView(spinner)
            addView(surface)
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
        appearance: `fun`.kirari.hanako.automation.BubbleAppearance
    ) {
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
        appearance: `fun`.kirari.hanako.automation.BubbleAppearance
    ) {
        spinner?.apply {
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

    private fun updateCaptureCount(
        countView: TextView?,
        appearance: `fun`.kirari.hanako.automation.BubbleAppearance
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
