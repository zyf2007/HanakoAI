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
        // 初始尺寸按 Idle 外观计算，随后的 update(Idle) 会得到同样的结果，不会产生跳动。
        val initialLayout = resolveLayout(
            appearance = BubbleRenderer.render(
                state = BubbleState.Idle,
                launchMode = CaptureLaunchMode.NORMAL,
                context = context,
                staticModeEnabled = isStaticModeEnabled()
            ),
            settings = initialSettings,
            text = null
        )
        val surfaceSizePx = initialLayout.surfaceWidthPx
        val iconSizePx = (initialSettings.bubbleDiameterDp * density * 0.5f).roundToInt()
        val spinnerSizePx = (initialSettings.spinnerDiameterDp * density).roundToInt()
        val letterSizePx = initialLayout.letterLayerWidthPx
        val params = WindowManager.LayoutParams(
            initialLayout.windowWidthPx,
            initialLayout.windowHeightPx,
            overlayWindowType(),
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
        val view = createBubbleRoot(
            initialLayout.windowWidthPx,
            initialLayout.windowHeightPx,
            params,
            shadowView,
            spinner,
            surface,
            text,
            countView
        )
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
        val layout = resolveLayout(appearance, settings, text)
        updateWindowSize(layout)
        updateSurfaceSize(bubble, layout, overallAlpha)
        applySurfaceShape(bubble, appearance, layout)
        updateShadow(layout)
        updateIconAndText(icon, text, appearance, settings, overallAlpha, layout)
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
        windowWidthPx: Int,
        windowHeightPx: Int,
        params: WindowManager.LayoutParams,
        shadowView: android.view.View,
        spinner: ProgressBar,
        surface: FrameLayout,
        text: TextView,
        countView: TextView
    ): FrameLayout {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        return FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(windowWidthPx, windowHeightPx)
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
        overallAlpha: Float,
        layout: BubbleLayout
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
            (layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                if (params.width != layout.letterLayerWidthPx || params.height != layout.letterLayerHeightPx) {
                    params.width = layout.letterLayerWidthPx
                    params.height = layout.letterLayerHeightPx
                    params.gravity = Gravity.CENTER
                    layoutParams = params
                    requestLayout()
                }
            }
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, layout.letterTextSizePx)
            // 多选项字母必须单行完整显示，不能被换行或省略号裁切。
            isSingleLine = true
            maxLines = 1
            ellipsize = null
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

    /**
     * 计算当前外观对应的窗口、球体与字母层尺寸。
     *
     * 多选项字母时用 [TextView.paint] 实测文字宽度，保证胶囊宽度足够容纳全部选项。
     */
    private fun resolveLayout(
        appearance: `fun`.kirari.hanako.feature.overlay.window.BubbleAppearance,
        settings: BubbleAppearanceSettings,
        text: TextView?
    ): BubbleLayout {
        val displayMetrics = context.resources.displayMetrics
        val letterTextSizePx = maxOf(settings.letterTextSizeDp * displayMetrics.scaledDensity, 1f)
        val letters = appearance.letters
        val needsTextMeasure = appearance.shape == BubbleShape.ROUNDED_RECT && !letters.isNullOrEmpty()
        val measured = if (needsTextMeasure && text != null) {
            measureLetterText(text, letters.orEmpty(), letterTextSizePx)
        } else {
            null
        }
        return BubbleLayoutCalculator.calculate(
            BubbleLayoutInput(
                shape = appearance.shape,
                letters = letters,
                sizeScale = appearance.sizeScale,
                density = displayMetrics.density,
                screenWidthPx = displayMetrics.widthPixels,
                bubbleDiameterDp = settings.bubbleDiameterDp,
                spinnerDiameterDp = settings.spinnerDiameterDp,
                letterTextSizeDp = settings.letterTextSizeDp,
                letterTextSizePx = letterTextSizePx,
                naturalTextWidthPx = measured?.first ?: 0f,
                naturalTextHeightPx = measured?.second ?: 0f
            )
        )
    }

    /**
     * 用字母视图自身的 [android.text.TextPaint] 实测文字尺寸，
     * 这样字重/字体与真正渲染时完全一致。测量后恢复原字号。
     */
    private fun measureLetterText(text: TextView, letters: String, textSizePx: Float): Pair<Float, Float> {
        val paint = text.paint
        val previousTextSize = paint.textSize
        paint.textSize = textSizePx
        val widthPx = paint.measureText(letters)
        val fontMetrics = paint.fontMetrics
        val heightPx = fontMetrics.descent - fontMetrics.ascent
        paint.textSize = previousTextSize
        return widthPx to heightPx
    }

    /**
     * 调整悬浮球窗口尺寸。
     *
     * 窗口变宽时以原中心为锚点向两侧扩展，否则气泡会随着窗口向右「滑动」；
     * 最后把窗口夹回屏幕内，避免多选项胶囊被屏幕边缘裁切。
     */
    private fun updateWindowSize(layout: BubbleLayout) {
        val root = bubbleView ?: return
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        if (params.width == layout.windowWidthPx && params.height == layout.windowHeightPx) return
        val centerXPx = params.x + params.width / 2
        val centerYPx = params.y + params.height / 2
        params.width = layout.windowWidthPx
        params.height = layout.windowHeightPx
        params.x = centerXPx - layout.windowWidthPx / 2
        params.y = centerYPx - layout.windowHeightPx / 2
        val displayMetrics = context.resources.displayMetrics
        val maxXPx = (displayMetrics.widthPixels - layout.windowWidthPx).coerceAtLeast(0)
        val maxYPx = (displayMetrics.heightPixels - layout.windowHeightPx).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxXPx)
        params.y = params.y.coerceIn(0, maxYPx)
        windowManager.updateViewLayout(root, params)
    }

    private fun updateSurfaceSize(bubble: FrameLayout, layout: BubbleLayout, overallAlpha: Float) {
        bubble.alpha = overallAlpha
        val params = bubble.layoutParams as? FrameLayout.LayoutParams ?: return
        if (params.width == layout.surfaceWidthPx && params.height == layout.surfaceHeightPx) return
        params.width = layout.surfaceWidthPx
        params.height = layout.surfaceHeightPx
        params.gravity = Gravity.CENTER
        bubble.layoutParams = params
        bubble.requestLayout()
    }

    /**
     * 应用背景形状与颜色：圆形用 [GradientDrawable.OVAL]，
     * 多选项胶囊用整圆角的 [GradientDrawable.RECTANGLE]。
     */
    private fun applySurfaceShape(
        bubble: FrameLayout,
        appearance: `fun`.kirari.hanako.feature.overlay.window.BubbleAppearance,
        layout: BubbleLayout
    ) {
        val density = context.resources.displayMetrics.density
        val existing = bubble.background as? GradientDrawable
        val drawable = existing ?: GradientDrawable().apply {
            setStroke((1.5f * density).roundToInt(), android.graphics.Color.WHITE)
        }
        val targetShape = if (layout.shape == BubbleShape.ROUNDED_RECT) {
            GradientDrawable.RECTANGLE
        } else {
            GradientDrawable.OVAL
        }
        val targetCornerRadius = if (targetShape == GradientDrawable.RECTANGLE) {
            layout.surfaceHeightPx / 2f
        } else {
            0f
        }
        if (drawable.shape != targetShape || drawable.cornerRadius != targetCornerRadius) {
            drawable.shape = targetShape
            drawable.cornerRadius = targetCornerRadius
            bubble.background = drawable
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
    }

    /**
     * 阴影跟随球体形状，否则胶囊后面会露出一个圆形阴影。
     */
    private fun updateShadow(layout: BubbleLayout) {
        val shadow = shadowView ?: return
        val density = context.resources.displayMetrics.density
        val strokePx = (3f * density).roundToInt()
        val widthPx = layout.surfaceWidthPx + strokePx
        val heightPx = layout.surfaceHeightPx + strokePx
        (shadow.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.width != widthPx || params.height != heightPx) {
                params.width = widthPx
                params.height = heightPx
                params.gravity = Gravity.CENTER
                shadow.layoutParams = params
            }
        }
        val drawable = shadow.background as? GradientDrawable ?: return
        val targetShape = if (layout.shape == BubbleShape.ROUNDED_RECT) {
            GradientDrawable.RECTANGLE
        } else {
            GradientDrawable.OVAL
        }
        val targetCornerRadius = if (targetShape == GradientDrawable.RECTANGLE) heightPx / 2f else 0f
        if (drawable.shape != targetShape || drawable.cornerRadius != targetCornerRadius) {
            drawable.shape = targetShape
            drawable.cornerRadius = targetCornerRadius
            shadow.background = drawable
        }
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
