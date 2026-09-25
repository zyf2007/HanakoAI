package `fun`.kirari.hanako.feature.overlay.window

import android.content.Context
import android.graphics.Color
import `fun`.kirari.hanako.R
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import `fun`.kirari.hanako.feature.overlay.state.BubbleState

/**
 * 悬浮球背景形状。
 *
 * 单个字母或「对」「错」「√」「×」使用圆形；
 * 多选项字母（如 `ABCD`）在圆形里必然被裁切，改用圆角矩形横向容纳。
 */
enum class BubbleShape {
    CIRCLE,
    ROUNDED_RECT
}

/**
 * 由字母内容推导背景形状。
 *
 * 单字符保持圆形；多字符（只可能是 1-8 个英文字母）需要横向空间，改用圆角矩形。
 */
internal fun bubbleShapeForLetters(letters: String?): BubbleShape {
    return if (letters != null && letters.length > 1) BubbleShape.ROUNDED_RECT else BubbleShape.CIRCLE
}

/**
 * 悬浮球视觉表现数据类
 */
data class BubbleAppearance(
    val backgroundColor: Int,
    val iconTint: Int,
    val iconRes: Int,
    val spinnerColor: Int,
    val showSpinner: Boolean,
    val letters: String?,
    val showIcon: Boolean = true,
    val sizeScale: Float = 1f,
    val animateTransitions: Boolean = true,
    val showCaptureCount: Boolean = false,
    val captureCount: Int = 0
) {
    /**
     * 背景形状始终由 [letters] 推导，而不是独立字段。
     *
     * 这样 `copy(letters = null)`（例如菜单展开时）会自动回到圆形，
     * 不会出现「形状是胶囊但内容已清空」的错位。
     */
    val shape: BubbleShape
        get() = bubbleShapeForLetters(letters)
}

/**
 * 悬浮球渲染器
 * 将状态转换为视觉表现，纯函数，无副作用
 */
internal object BubbleRenderer {

    /**
     * 根据状态渲染悬浮球外观
     */
    fun render(
        state: BubbleState,
        launchMode: CaptureLaunchMode,
        context: Context,
        staticModeEnabled: Boolean
    ): BubbleAppearance {
        if (staticModeEnabled) {
            return BubbleAppearance(
                backgroundColor = Color.WHITE,
                iconTint = Color.WHITE,
                iconRes = 0,
                spinnerColor = Color.WHITE,
                showSpinner = false,
                letters = null,
                showIcon = false,
                sizeScale = 0.5f,
                animateTransitions = false
            )
        }
        return when (state) {
            is BubbleState.Idle -> renderIdle(launchMode, context)
            is BubbleState.Processing -> renderProcessing(context)
            is BubbleState.Copied -> renderCopied(context)
            is BubbleState.ShowingLetters -> renderShowingLetters(state, context)
            is BubbleState.MultiPageCapture -> renderMultiPageCapture(state, context)
            is BubbleState.MultiPageCapturing -> renderMultiPageCapturing(state, context)
            is BubbleState.MultiPageCaptureSuccess -> renderMultiPageCaptureSuccess(state, context)
            is BubbleState.MenuExpanded -> renderMenuExpanded(state, launchMode, context)
            is BubbleState.Error -> renderError(context)
        }
    }

    private fun renderIdle(launchMode: CaptureLaunchMode, context: Context): BubbleAppearance {
        return if (launchMode == CaptureLaunchMode.AUTO) {
            // 自动模式使用不同的图标
            BubbleAppearance(
                backgroundColor = Color.parseColor("#EADDFF"),
                iconTint = Color.parseColor("#4F378B"),
                iconRes = R.drawable.ic_bubble_auto,
                spinnerColor = Color.parseColor("#6750A4"),
                showSpinner = false,
                letters = null
            )
        } else {
            // 普通模式
            BubbleAppearance(
                backgroundColor = Color.parseColor("#D0BCFF"),
                iconTint = Color.parseColor("#381E72"),
                iconRes = R.drawable.ic_bubble_crop,
                spinnerColor = Color.parseColor("#6750A4"),
                showSpinner = false,
                letters = null
            )
        }
    }

    private fun renderProcessing(context: Context): BubbleAppearance {
        return BubbleAppearance(
            backgroundColor = Color.parseColor("#EADDFF"),
            iconTint = Color.parseColor("#4F378B"),
            iconRes = R.drawable.ic_bubble_auto,
            spinnerColor = Color.parseColor("#6750A4"),
            showSpinner = true,
            letters = null
        )
    }

    private fun renderCopied(context: Context): BubbleAppearance {
        return BubbleAppearance(
            backgroundColor = Color.parseColor("#D3E3FD"),
            iconTint = Color.parseColor("#0B57D0"),
            iconRes = R.drawable.ic_bubble_clipboard,
            spinnerColor = Color.parseColor("#0B57D0"),
            showSpinner = false,
            letters = null
        )
    }

    private fun renderShowingLetters(state: BubbleState.ShowingLetters, context: Context): BubbleAppearance {
        return BubbleAppearance(
            backgroundColor = Color.parseColor("#E8F0FE"),
            iconTint = Color.parseColor("#0B57D0"),
            iconRes = 0,
            spinnerColor = Color.parseColor("#0B57D0"),
            showSpinner = false,
            letters = state.letters
        )
    }

    private fun renderMultiPageCapture(state: BubbleState.MultiPageCapture, context: Context): BubbleAppearance {
        return BubbleAppearance(
            backgroundColor = Color.parseColor("#FFDAD6"),
            iconTint = Color.parseColor("#BA1A1A"),
            iconRes = R.drawable.ic_bubble_crop,
            spinnerColor = Color.parseColor("#BA1A1A"),
            showSpinner = false,
            letters = null,
            showCaptureCount = true,
            captureCount = state.captureCount
        )
    }

    private fun renderMultiPageCapturing(state: BubbleState.MultiPageCapturing, context: Context): BubbleAppearance {
        return BubbleAppearance(
            backgroundColor = Color.parseColor("#FFDAD6"),
            iconTint = Color.parseColor("#BA1A1A"),
            iconRes = R.drawable.ic_bubble_crop,
            spinnerColor = Color.parseColor("#BA1A1A"),
            showSpinner = true,  // 显示外圈 spinner
            letters = null,
            showCaptureCount = true,
            captureCount = state.captureCount
        )
    }

    private fun renderMultiPageCaptureSuccess(state: BubbleState.MultiPageCaptureSuccess, context: Context): BubbleAppearance {
        return BubbleAppearance(
            backgroundColor = Color.parseColor("#FFDAD6"),
            iconTint = Color.parseColor("#BA1A1A"),
            iconRes = R.drawable.ic_bubble_image,
            spinnerColor = Color.parseColor("#BA1A1A"),
            showSpinner = false,
            letters = null,
            showCaptureCount = true,
            captureCount = state.captureCount
        )
    }

    private fun renderMenuExpanded(
        state: BubbleState.MenuExpanded,
        launchMode: CaptureLaunchMode,
        context: Context
    ): BubbleAppearance {
        val prev = if (state.previousState is BubbleState.MenuExpanded) BubbleState.Idle else state.previousState
        val baseAppearance = render(prev, launchMode, context, staticModeEnabled = false)
        // letters 置空后 shape 会随之回到圆形，菜单入口不会残留上一个状态的胶囊形状。
        return baseAppearance.copy(
            iconRes = R.drawable.ic_bubble_menu,
            letters = null
        )
    }

    private fun renderError(context: Context): BubbleAppearance {
        return BubbleAppearance(
            backgroundColor = Color.parseColor("#B3261E"),
            iconTint = Color.WHITE,
            iconRes = R.drawable.ic_bubble_error,
            spinnerColor = Color.WHITE,
            showSpinner = false,
            letters = null
        )
    }

    /**
     * 检查是否显示加载动画
     */
    fun shouldShowSpinner(state: BubbleState): Boolean {
        return state is BubbleState.Processing
    }

    /**
     * 获取状态描述（用于调试）
     */
    fun getStateDescription(state: BubbleState): String {
        return when (state) {
            is BubbleState.Idle -> "Idle"
            is BubbleState.Processing -> "Processing"
            is BubbleState.Copied -> "Copied(${state.label})"
            is BubbleState.ShowingLetters -> "ShowingLetters(${state.letters})"
            is BubbleState.MultiPageCapture -> "MultiPageCapture(count=${state.captureCount})"
            is BubbleState.MultiPageCapturing -> "MultiPageCapturing(count=${state.captureCount})"
            is BubbleState.MultiPageCaptureSuccess -> "MultiPageCaptureSuccess(count=${state.captureCount})"
            is BubbleState.MenuExpanded -> "MenuExpanded(prev=${state.previousState::class.simpleName})"
            is BubbleState.Error -> "Error(${state.message})"
        }
    }
}
