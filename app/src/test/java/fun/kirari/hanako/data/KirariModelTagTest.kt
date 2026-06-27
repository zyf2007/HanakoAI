package `fun`.kirari.hanako.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KirariModelTagTest {
    @Test
    fun `maps supported kirari model tags`() {
        assertEquals(KirariModelTag.TEXT, "text".toKirariModelTag())
        assertEquals(KirariModelTag.OCR, "ocr".toKirariModelTag())
        assertEquals(KirariModelTag.MULTIMODAL, "multimodal".toKirariModelTag())
        assertEquals(KirariModelTag.FALLBACK, "fallback".toKirariModelTag())
    }

    @Test
    fun `returns null for unknown kirari model tag`() {
        assertNull("vision".toKirariModelTag())
    }
}
