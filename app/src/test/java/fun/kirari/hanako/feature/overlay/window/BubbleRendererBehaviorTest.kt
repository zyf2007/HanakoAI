package `fun`.kirari.hanako.feature.overlay.window

import `fun`.kirari.hanako.feature.overlay.state.BubbleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleRendererBehaviorTest {

    @Test
    fun shouldShowSpinner_onlyForProcessingState() {
        assertTrue(BubbleRenderer.shouldShowSpinner(BubbleState.Processing))
        assertFalse(BubbleRenderer.shouldShowSpinner(BubbleState.Idle))
        assertFalse(BubbleRenderer.shouldShowSpinner(BubbleState.MultiPageCapturing(captureCount = 1)))
    }

    @Test
    fun getStateDescription_coversRepresentativeStates() {
        assertEquals("Idle", BubbleRenderer.getStateDescription(BubbleState.Idle))
        assertEquals("Copied(done)", BubbleRenderer.getStateDescription(BubbleState.Copied("done")))
        assertEquals(
            "ShowingLetters(AB)",
            BubbleRenderer.getStateDescription(BubbleState.ShowingLetters("AB"))
        )
        assertEquals(
            "MultiPageCapture(count=2)",
            BubbleRenderer.getStateDescription(BubbleState.MultiPageCapture(captureCount = 2))
        )
        assertEquals(
            "MenuExpanded(prev=Copied)",
            BubbleRenderer.getStateDescription(
                BubbleState.MenuExpanded(BubbleState.Copied("x"))
            )
        )
        assertEquals("Error(boom)", BubbleRenderer.getStateDescription(BubbleState.Error("boom")))
    }

    @Test
    fun bubbleShapeForLetters_singleOptionStaysCircle() {
        assertEquals(BubbleShape.CIRCLE, bubbleShapeForLetters("A"))
        assertEquals(BubbleShape.CIRCLE, bubbleShapeForLetters("对"))
        assertEquals(BubbleShape.CIRCLE, bubbleShapeForLetters("错"))
        assertEquals(BubbleShape.CIRCLE, bubbleShapeForLetters("√"))
        assertEquals(BubbleShape.CIRCLE, bubbleShapeForLetters("×"))
        assertEquals(BubbleShape.CIRCLE, bubbleShapeForLetters(null))
        assertEquals(BubbleShape.CIRCLE, bubbleShapeForLetters(""))
    }

    @Test
    fun bubbleShapeForLetters_multipleOptionsUseRoundedRect() {
        assertEquals(BubbleShape.ROUNDED_RECT, bubbleShapeForLetters("AB"))
        assertEquals(BubbleShape.ROUNDED_RECT, bubbleShapeForLetters("ABCD"))
        assertEquals(BubbleShape.ROUNDED_RECT, bubbleShapeForLetters("ABCDEFGH"))
    }

    @Test
    fun appearanceShape_followsLettersWhenCopied() {
        val base = appearance(letters = "ABCD")
        assertEquals(BubbleShape.ROUNDED_RECT, base.shape)

        // 菜单展开等场景会 copy(letters = null)，形状必须自动回到圆形
        val cleared = base.copy(letters = null)
        assertEquals(BubbleShape.CIRCLE, cleared.shape)

        val single = base.copy(letters = "A")
        assertEquals(BubbleShape.CIRCLE, single.shape)
    }

    private fun appearance(letters: String?): BubbleAppearance {
        return BubbleAppearance(
            backgroundColor = 0,
            iconTint = 0,
            iconRes = 0,
            spinnerColor = 0,
            showSpinner = false,
            letters = letters
        )
    }
}
