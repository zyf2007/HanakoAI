package `fun`.kirari.hanako.overlay

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

internal val markdownFlavor by lazy {
    GFMFlavourDescriptor(makeHttpsAutoLinks = true, useSafeLinks = true)
}

internal val markdownParser by lazy {
    MarkdownParser(markdownFlavor)
}

private fun String.normalizeMarkdownNewlines(): String = replace("\r\n", "\n").replace('\r', '\n')

internal val inlineLatexRegex = Regex("""\\\((.+?)\\\)""", RegexOption.DOT_MATCHES_ALL)
internal val blockLatexRegex = Regex("""[ \t]*\\\[(.+?)\\\][ \t]*""", RegexOption.DOT_MATCHES_ALL)
internal val escapedDollarBlockDelimiterRegex = Regex("""(?m)^([ \t]*)\\\$\\\$[ \t]*$""")
internal val dollarBlockLatexRegex = Regex("""(?m)(^|[ \t]*\n)[ \t]*\$\$[ \t]*\n?([\s\S]+?)\n?[ \t]*\$\$[ \t]*(?=\n|$)""")
internal val codeBlockRegex = Regex("```[\\s\\S]*?```|`[^`\n]*`", RegexOption.DOT_MATCHES_ALL)
internal val breakLineRegex = Regex("(?i)<br\\s*/?>")
internal val copyMarkerRegex = Regex("""\[(?:copy|复制):(.*?)\]""", RegexOption.DOT_MATCHES_ALL)

private fun codeRangesIn(content: String): List<IntRange> {
    val codeBlocks = mutableListOf<IntRange>()
    codeBlockRegex.findAll(content).forEach { match ->
        codeBlocks.add(match.range)
    }
    return codeBlocks
}

private fun replaceCopyMarkers(content: String): Pair<String, List<CopyMarkerToken>> {
    val codeBlocks = codeRangesIn(content)
    fun inCodeBlock(index: Int): Boolean = codeBlocks.any { index in it }

    val markers = mutableListOf<CopyMarkerToken>()
    val result = copyMarkerRegex.replace(content) { match ->
        if (inCodeBlock(match.range.first)) {
            match.value
        } else {
            val copyText = match.groupValues[1].trim()
            if (copyText.isBlank()) {
                match.value
            } else {
                val placeholder = "HanakoCopyMarker${markers.size}"
                markers += CopyMarkerToken(placeholder = placeholder, copyText = copyText)
                placeholder
            }
        }
    }

    return result to markers
}

internal fun preprocessMarkdown(content: String): Pair<String, List<CopyMarkerToken>> {
    val normalizedContent = content.normalizeMarkdownNewlines()
    val (copyPreprocessed, copyMarkers) = replaceCopyMarkers(normalizedContent)
    val codeBlocks = codeRangesIn(copyPreprocessed)

    fun inCodeBlock(index: Int): Boolean = codeBlocks.any { index in it }

    var result = escapedDollarBlockDelimiterRegex.replace(copyPreprocessed) { match ->
        if (inCodeBlock(match.range.first)) match.value else "${match.groupValues[1]}${'$'}${'$'}"
    }
    result = inlineLatexRegex.replace(result) { match ->
        if (inCodeBlock(match.range.first)) match.value else "${'$'}${match.groupValues[1]}${'$'}"
    }
    result = blockLatexRegex.replace(result) { match ->
        if (inCodeBlock(match.range.first)) {
            match.value
        } else {
            "\n${'$'}${'$'}\n${match.groupValues[1].trim()}\n${'$'}${'$'}\n"
        }
    }
    result = dollarBlockLatexRegex.replace(result) { match ->
        if (inCodeBlock(match.range.first)) {
            match.value
        } else {
            val prefix = if (match.groupValues[1].contains('\n')) "\n" else match.groupValues[1]
            "${prefix}${'$'}${'$'}\n${match.groupValues[2].trim()}\n${'$'}${'$'}"
        }
    }
    return result to copyMarkers
}

internal data class CopyMarkerToken(
    val placeholder: String,
    val copyText: String
)

internal data class MarkdownParseResult(
    val preprocessed: String,
    val astTree: ASTNode,
    val copyMarkers: List<CopyMarkerToken> = emptyList()
)

internal fun ASTNode.getText(text: String): String = text.substring(startOffset, endOffset)

internal fun ASTNode.findChildRecursive(vararg types: IElementType): ASTNode? {
    if (type in types) return this
    for (child in children) {
        child.findChildRecursive(*types)?.let { return it }
    }
    return null
}

internal fun parseMarkdown(content: String): MarkdownParseResult {
    val (preprocessed, copyMarkers) = preprocessMarkdown(content)
    return MarkdownParseResult(
        preprocessed = preprocessed,
        astTree = markdownParser.buildMarkdownTreeFromString(preprocessed),
        copyMarkers = copyMarkers
    )
}

internal fun extractCodeFenceContent(node: ASTNode, content: String): String {
    val startIndex = node.children.indexOfFirst { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
    if (startIndex == -1) return node.getText(content)
    val eolElement = node.children.subList(0, startIndex).findLast { it.type == MarkdownTokenTypes.EOL } ?: return node.getText(content)
    val startOffset = eolElement.endOffset
    val endOffset = node.children.findLast { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }?.endOffset ?: return node.getText(content)
    return content.substring(startOffset, endOffset).trimIndent()
}
