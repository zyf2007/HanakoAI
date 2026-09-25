package `fun`.kirari.hanako.feature.overlay.window

import kotlin.math.roundToInt

/**
 * 悬浮球一次布局所需的全部像素尺寸。
 *
 * 悬浮球的窗口（root）比可见球体大一圈，两者需要同时变化，
 * 因此这里把窗口、球体、字母层三者的尺寸集中在一次计算里返回。
 */
internal data class BubbleLayout(
    val shape: BubbleShape,
    val surfaceWidthPx: Int,
    val surfaceHeightPx: Int,
    val letterLayerWidthPx: Int,
    val letterLayerHeightPx: Int,
    val letterTextSizePx: Float,
    val windowWidthPx: Int,
    val windowHeightPx: Int
)

/**
 * [BubbleLayoutCalculator.calculate] 的输入。
 *
 * 文字尺寸由调用方用真实 `TextView` 的 `Paint` 实测后传入，
 * 这样这里只做纯数学，可以在 JVM 单元测试里用合成宽度断言「一定装得下」。
 */
internal data class BubbleLayoutInput(
    val shape: BubbleShape,
    val letters: String?,
    val sizeScale: Float,
    val density: Float,
    val screenWidthPx: Int,
    val bubbleDiameterDp: Float,
    val spinnerDiameterDp: Float,
    val letterTextSizeDp: Float,
    /** 用户设置的字号换算成 px（已含系统字体缩放） */
    val letterTextSizePx: Float,
    /** 以 [letterTextSizePx] 实测的文字宽度，圆形分支不使用 */
    val naturalTextWidthPx: Float,
    /** 以 [letterTextSizePx] 实测的单行文字高度，圆形分支不使用 */
    val naturalTextHeightPx: Float
)

/**
 * 悬浮球几何计算（纯函数，无副作用，便于单元测试）。
 *
 * 圆形分支完全沿用历史尺寸公式，保证单字母/对错符号的观感不变；
 * 圆角矩形分支用于多选项字母，宽度按实测文字自适应。
 */
internal object BubbleLayoutCalculator {

    /** 悬浮球与窗口之间的留白（dp），沿用原实现的 16dp */
    const val WINDOW_PADDING_DP = 16f

    /** 多选项胶囊内文字左右两侧的内边距（dp） */
    const val PILL_HORIZONTAL_PADDING_DP = 12f

    /** 胶囊最大宽度占屏幕宽度的比例，超出则压缩字号而不是截断文字 */
    const val MAX_PILL_SCREEN_RATIO = 0.72f

    /** 单字母层的边长系数，沿用原实现的 letterTextSizeDp * 1.8 */
    const val LETTER_LAYER_FACTOR = 1.8f

    /** 实测高度不可用时的单行高度兜底系数 */
    private const val SINGLE_LINE_HEIGHT_FACTOR = 1.2f

    fun calculate(input: BubbleLayoutInput): BubbleLayout {
        val windowPaddingPx = WINDOW_PADDING_DP * input.density
        val contentSizeDp = maxOf(
            input.bubbleDiameterDp,
            input.spinnerDiameterDp,
            input.letterTextSizeDp * LETTER_LAYER_FACTOR
        )
        val windowHeightPx = (contentSizeDp * input.density + windowPaddingPx).roundToInt()
        val bubbleDiameterPx = input.bubbleDiameterDp * input.density * input.sizeScale
        val letterTextSizePx = maxOf(input.letterTextSizePx, 1f)

        if (input.shape != BubbleShape.ROUNDED_RECT || input.letters.isNullOrEmpty()) {
            // 圆形：尺寸与历史实现保持一致
            val layerPx = (input.letterTextSizeDp * LETTER_LAYER_FACTOR * input.density).roundToInt()
            val surfacePx = bubbleDiameterPx.roundToInt()
            return BubbleLayout(
                shape = BubbleShape.CIRCLE,
                surfaceWidthPx = surfacePx,
                surfaceHeightPx = surfacePx,
                letterLayerWidthPx = layerPx,
                letterLayerHeightPx = layerPx,
                letterTextSizePx = letterTextSizePx,
                windowWidthPx = windowHeightPx,
                windowHeightPx = windowHeightPx
            )
        }

        // 圆角矩形：宽度随文字扩展，高度至少装得下单行文字
        val horizontalPaddingPx = PILL_HORIZONTAL_PADDING_DP * input.density
        val fit = fitPill(
            naturalTextWidthPx = input.naturalTextWidthPx,
            naturalTextSizePx = letterTextSizePx,
            minSurfaceWidthPx = bubbleDiameterPx,
            maxSurfaceWidthPx = input.screenWidthPx * MAX_PILL_SCREEN_RATIO,
            horizontalPaddingPx = horizontalPaddingPx
        )
        val textHeightPx = maxOf(
            input.naturalTextHeightPx,
            letterTextSizePx * SINGLE_LINE_HEIGHT_FACTOR
        )
        val surfaceHeightPx = maxOf(bubbleDiameterPx, textHeightPx).roundToInt()
        val windowWidthPx = maxOf(
            windowHeightPx,
            (fit.surfaceWidthPx + windowPaddingPx).roundToInt()
        )
        return BubbleLayout(
            shape = BubbleShape.ROUNDED_RECT,
            surfaceWidthPx = fit.surfaceWidthPx,
            surfaceHeightPx = surfaceHeightPx,
            letterLayerWidthPx = fit.surfaceWidthPx,
            letterLayerHeightPx = surfaceHeightPx,
            letterTextSizePx = fit.textSizePx,
            windowWidthPx = windowWidthPx,
            windowHeightPx = windowHeightPx
        )
    }

    /**
     * 胶囊宽度与字号自适应。
     *
     * 不变量：返回的文字在 [PillFit.textSizePx] 下渲染后的宽度
     * **不会超过** `surfaceWidthPx - 2 * horizontalPaddingPx`，
     * 也就保证所有选项都能单行完整显示，最多只是字号被压缩。
     */
    fun fitPill(
        naturalTextWidthPx: Float,
        naturalTextSizePx: Float,
        minSurfaceWidthPx: Float,
        maxSurfaceWidthPx: Float,
        horizontalPaddingPx: Float
    ): PillFit {
        val lowerBound = maxOf(minSurfaceWidthPx, 0f)
        val upperBound = maxOf(maxSurfaceWidthPx, lowerBound)
        val desiredWidthPx = naturalTextWidthPx + 2f * horizontalPaddingPx
        val surfaceWidthPx = desiredWidthPx.coerceIn(lowerBound, upperBound).roundToInt()
        val availableTextWidthPx = (surfaceWidthPx - 2f * horizontalPaddingPx).coerceAtLeast(1f)
        val textSizePx = if (naturalTextWidthPx > availableTextWidthPx) {
            naturalTextSizePx * (availableTextWidthPx / naturalTextWidthPx)
        } else {
            naturalTextSizePx
        }
        return PillFit(surfaceWidthPx = surfaceWidthPx, textSizePx = textSizePx)
    }

    /** 胶囊宽度与字号的自适应结果 */
    data class PillFit(
        val surfaceWidthPx: Int,
        val textSizePx: Float
    )
}
