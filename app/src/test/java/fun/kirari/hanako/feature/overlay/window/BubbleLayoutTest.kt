package `fun`.kirari.hanako.feature.overlay.window

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * 悬浮球多选项胶囊的几何计算测试。
 *
 * 这里只测纯数学，文字宽度用合成值传入，因此不需要 Android 运行时。
 * 核心不变量：压缩后的文字宽度一定不超过胶囊内可用宽度（否则选项会被裁切）。
 */
class BubbleLayoutTest {

    private val density = 2.75f
    private val screenWidthPx = 1080

    private fun input(
        shape: BubbleShape = BubbleShape.ROUNDED_RECT,
        letters: String? = "ABCD",
        bubbleDiameterDp: Float = 40f,
        spinnerDiameterDp: Float = 50f,
        letterTextSizeDp: Float = 28f,
        letterTextSizePx: Float = 28f * 2.75f,
        naturalTextWidthPx: Float = 200f,
        naturalTextHeightPx: Float = 80f,
        sizeScale: Float = 1f
    ) = BubbleLayoutInput(
        shape = shape,
        letters = letters,
        sizeScale = sizeScale,
        density = density,
        screenWidthPx = screenWidthPx,
        bubbleDiameterDp = bubbleDiameterDp,
        spinnerDiameterDp = spinnerDiameterDp,
        letterTextSizeDp = letterTextSizeDp,
        letterTextSizePx = letterTextSizePx,
        naturalTextWidthPx = naturalTextWidthPx,
        naturalTextHeightPx = naturalTextHeightPx
    )

    @Test
    fun calculate_singleLetterOrNoLetters_keepsCircleGeometry() {
        val circle = BubbleLayoutCalculator.calculate(input(shape = BubbleShape.CIRCLE, letters = "A"))

        assertEquals(BubbleShape.CIRCLE, circle.shape)
        assertEquals(circle.surfaceWidthPx, circle.surfaceHeightPx)
        // 圆形分支沿用历史公式：max(球径, spinner, 字号*1.8) * density + 16dp
        val contentSizeDp = maxOf(40f, 50f, 28f * BubbleLayoutCalculator.LETTER_LAYER_FACTOR)
        val expectedWindow = (contentSizeDp * density + BubbleLayoutCalculator.WINDOW_PADDING_DP * density).roundToInt()
        assertEquals(expectedWindow, circle.windowWidthPx)
        assertEquals(expectedWindow, circle.windowHeightPx)
        // 单字母层是正方形
        assertEquals(circle.letterLayerWidthPx, circle.letterLayerHeightPx)
    }

    @Test
    fun calculate_multiLetter_expandsWidthBeyondHeight() {
        val pill = BubbleLayoutCalculator.calculate(input())

        assertEquals(BubbleShape.ROUNDED_RECT, pill.shape)
        assertTrue(
            "多选项胶囊必须比圆形更宽：width=${pill.surfaceWidthPx} height=${pill.surfaceHeightPx}",
            pill.surfaceWidthPx > pill.surfaceHeightPx
        )
        // 窗口必须比可见胶囊更大或相等，否则会被窗口边界裁切
        assertTrue(pill.windowWidthPx >= pill.surfaceWidthPx)
        assertTrue(pill.windowHeightPx >= pill.surfaceHeightPx)
    }

    @Test
    fun calculate_multiLetter_letterLayerMatchesSurfaceSoTextCenters() {
        val pill = BubbleLayoutCalculator.calculate(input())

        assertEquals(pill.surfaceWidthPx, pill.letterLayerWidthPx)
        assertEquals(pill.surfaceHeightPx, pill.letterLayerHeightPx)
    }

    @Test
    fun calculate_multiLetter_shortTextIsNotNarrowerThanTheCircle() {
        val circle = BubbleLayoutCalculator.calculate(input(shape = BubbleShape.CIRCLE, letters = "A"))
        val pill = BubbleLayoutCalculator.calculate(input(naturalTextWidthPx = 4f))

        assertTrue(
            "两个字母不应该比圆形还窄：pill=${pill.surfaceWidthPx} circle=${circle.surfaceWidthPx}",
            pill.surfaceWidthPx >= circle.surfaceWidthPx
        )
    }

    @Test
    fun calculate_tallLetterText_growsHeightAboveBubbleDiameter() {
        // 用户把字号拉到 96sp，40dp 的球高装不下一行字，胶囊必须长高
        val pill = BubbleLayoutCalculator.calculate(
            input(
                letterTextSizeDp = 96f,
                letterTextSizePx = 96f * 2.75f,
                naturalTextWidthPx = 700f,
                naturalTextHeightPx = 96f * 2.75f * 1.16f
            )
        )

        val bubbleDiameterPx = 40f * density
        assertTrue(
            "字高超过球径时高度必须跟随文字：height=${pill.surfaceHeightPx}",
            pill.surfaceHeightPx > bubbleDiameterPx
        )
    }

    @Test
    fun fitPill_neverExceedsAvailableTextWidth() {
        // 覆盖一批真实可能出现的字母数量与字号
        val letterCounts = 2..8
        val textSizes = listOf(8f, 14f, 28f, 48f, 96f)
        val horizontalPaddingPx = BubbleLayoutCalculator.PILL_HORIZONTAL_PADDING_DP * density

        for (count in letterCounts) {
            for (textSizeDp in textSizes) {
                val naturalTextSizePx = textSizeDp * density
                // 粗体大写字母平均约 0.62em 宽
                val naturalTextWidthPx = count * naturalTextSizePx * 0.62f
                val fit = BubbleLayoutCalculator.fitPill(
                    naturalTextWidthPx = naturalTextWidthPx,
                    naturalTextSizePx = naturalTextSizePx,
                    minSurfaceWidthPx = 40f * density,
                    maxSurfaceWidthPx = screenWidthPx * BubbleLayoutCalculator.MAX_PILL_SCREEN_RATIO,
                    horizontalPaddingPx = horizontalPaddingPx
                )
                val availableTextWidthPx = fit.surfaceWidthPx - 2f * horizontalPaddingPx
                val renderedTextWidthPx = naturalTextWidthPx * (fit.textSizePx / naturalTextSizePx)
                assertTrue(
                    "选项会被裁切：count=$count textSize=$textSizeDp " +
                        "rendered=$renderedTextWidthPx available=$availableTextWidthPx",
                    renderedTextWidthPx <= availableTextWidthPx + 0.5f
                )
            }
        }
    }

    @Test
    fun fitPill_withinBudget_keepsUserTextSize() {
        val fit = BubbleLayoutCalculator.fitPill(
            naturalTextWidthPx = 120f,
            naturalTextSizePx = 77f,
            minSurfaceWidthPx = 110f,
            maxSurfaceWidthPx = 700f,
            horizontalPaddingPx = 33f
        )

        assertEquals(77f, fit.textSizePx, 0.001f)
        // 期望宽度 = 文字 + 左右内边距
        assertEquals(186, fit.surfaceWidthPx)
    }

    @Test
    fun fitPill_overBudget_shrinksTextInsteadOfClipping() {
        val horizontalPaddingPx = 33f
        val fit = BubbleLayoutCalculator.fitPill(
            naturalTextWidthPx = 2000f,
            naturalTextSizePx = 100f,
            minSurfaceWidthPx = 110f,
            maxSurfaceWidthPx = 700f,
            horizontalPaddingPx = horizontalPaddingPx
        )

        assertEquals(700, fit.surfaceWidthPx)
        assertTrue("字号必须被压缩", fit.textSizePx < 100f)
        // 压缩后正好填满可用宽度
        val availableTextWidthPx = 700f - 2f * horizontalPaddingPx
        assertEquals(availableTextWidthPx, 2000f * (fit.textSizePx / 100f), 0.5f)
    }
}
