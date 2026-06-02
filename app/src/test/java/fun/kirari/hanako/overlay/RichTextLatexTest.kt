package `fun`.kirari.hanako.overlay

import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RichTextLatexTest {
    @Test
    fun processLatex_stripsInlineMathDelimiters() {
        assertEquals(
            """a + b""",
            processLatex("""${'$'}a + b${'$'}""")
        )
    }

    @Test
    fun processLatex_stripsBlockMathDelimiters() {
        assertEquals(
            """\begin{bmatrix}1 & 2 \\ 3 & 4\end{bmatrix}""",
            processLatex("""$$\begin{bmatrix}1 & 2 \\ 3 & 4\end{bmatrix}$$""")
        )
    }

    @Test
    fun preprocessMarkdown_keepsExactMatrixSourceIntact() {
        val source = """已知矩阵 \( A = \begin{bmatrix} 3 & 0 & 0 \\ 0 & a & b \\ 0 & 2 & 3 \end{bmatrix} \) 和 \( B = \begin{bmatrix} 3 & 0 & 0 \\ 0 & 4 & 0 \\ 0 & 0 & -1 \end{bmatrix} \) 相似,则 \( b = \) _ ."""

        val result = parseMarkdown(source)

        assertEquals(
            """已知矩阵 $ A = \begin{bmatrix} 3 & 0 & 0 \\ 0 & a & b \\ 0 & 2 & 3 \end{bmatrix} $ 和 $ B = \begin{bmatrix} 3 & 0 & 0 \\ 0 & 4 & 0 \\ 0 & 0 & -1 \end{bmatrix} $ 相似,则 $ b = $ _ .""",
            result.preprocessed
        )
        assertNotNull(result.astTree.findChildRecursive(GFMElementTypes.INLINE_MATH))
    }

    @Test
    fun preprocessMarkdown_turnsBracketLatexIntoBlockMath() {
        val source = """
            B：若 \(\sum_{n=1}^{\infty}u_n=s\)，则 \[
            \sum_{n=1}^{\infty}(u_n+u_{n+1})
            =2s-u_1
            \]
        """.trimIndent()

        val result = parseMarkdown(source)

        assertEquals(
            """
                B：若 $\sum_{n=1}^{\infty}u_n=s$，则
                $$
                \sum_{n=1}^{\infty}(u_n+u_{n+1})
                =2s-u_1
                $$
            """.trimIndent(),
            result.preprocessed.trimEnd()
        )
        assertNotNull(result.astTree.findChildRecursive(GFMElementTypes.BLOCK_MATH))
    }

    @Test
    fun preprocessMarkdown_keepsDollarBlockMathAsBlockMath() {
        val source = """
            所以 C：\(\sum_{n=1}^{\infty}(-1)^n\left(1+\frac1n\right)^n\)，因为
            $$
            \left(1+\frac1n\right)^n \to e
            $$
            所以通项不趋于 0。
        """.trimIndent()

        val result = parseMarkdown(source)

        assertEquals(
            """
                所以 C：$\sum_{n=1}^{\infty}(-1)^n\left(1+\frac1n\right)^n$，因为
                $$
                \left(1+\frac1n\right)^n \to e
                $$
                所以通项不趋于 0。
            """.trimIndent(),
            result.preprocessed
        )
        assertNotNull(result.astTree.findChildRecursive(GFMElementTypes.BLOCK_MATH))
    }

    @Test
    fun preprocessMarkdown_unescapesDollarBlockMathDelimiters() {
        val source = """
            • B：若 \(\sum_{n=1}^{\infty}u_n=s\)，则
            \$\$
            \sum_{n=1}^{\infty}u_n+u_{n+1}
            =2s-u_1
            \$\$
            不一定等于 \(2s\)，所以错误。
        """.trimIndent()

        val result = parseMarkdown(source)

        assertEquals(
            """
                • B：若 $\sum_{n=1}^{\infty}u_n=s$，则
                $$
                \sum_{n=1}^{\infty}u_n+u_{n+1}
                =2s-u_1
                $$
                不一定等于 $2s$，所以错误。
            """.trimIndent(),
            result.preprocessed
        )
        assertNotNull(result.astTree.findChildRecursive(GFMElementTypes.BLOCK_MATH))
    }
}
