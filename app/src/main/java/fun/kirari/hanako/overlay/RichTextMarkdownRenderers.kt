package `fun`.kirari.hanako.overlay

import android.content.Intent
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

@Composable
fun MarkdownLatexText(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = androidx.compose.material3.LocalTextStyle.current
) {
    val parsed = remember(content) { parseMarkdown(content) }
    val copyMarkerMap = remember(parsed.copyMarkers) {
        parsed.copyMarkers.associateBy { it.placeholder }
    }

    ProvideTextStyle(style) {
        Column(modifier = modifier) {
            splitDisplayMathBlocks(parsed.preprocessed).fastForEach { block ->
                when (block) {
                    is MarkdownRenderBlock.DisplayMath -> LatexBlock(
                        latex = block.latex,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    is MarkdownRenderBlock.Markdown -> {
                        val astTree = remember(block.content) {
                            markdownParser.buildMarkdownTreeFromString(block.content)
                        }
                        astTree.children.fastForEach { child ->
                            MarkdownNode(
                                node = child,
                                content = block.content,
                                copyMarkerMap = copyMarkerMap
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyMarkerChip(
    copyText: String
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .clickable {
                    clipboardManager.setText(AnnotatedString(copyText))
                    android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = copyText.replace('\n', ' '),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "复制",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun InlineMarkdownText(
    text: String,
    copyMarkerMap: Map<String, CopyMarkerToken>
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val textStyle = androidx.compose.material3.LocalTextStyle.current
    val inlineContents = remember(text, copyMarkerMap) {
        mutableMapOf<String, InlineTextContent>()
    }

    val annotated = remember(text, copyMarkerMap) {
        buildAnnotatedString {
            appendCopyAwareText(text, inlineContents, copyMarkerMap, colorScheme, density, textStyle)
        }
    }

    Text(
        text = annotated,
        inlineContent = inlineContents,
        softWrap = true,
        overflow = TextOverflow.Visible
    )
}

@Composable
private fun CopyMarkerInline(
    copyText: String
) {
    CopyMarkerChip(copyText = copyText)
}

private fun copyPlaceholderFor(copyText: String): Placeholder {
    val displayText = copyText.replace('\n', ' ')
    val width = (displayText.length.coerceIn(2, 24) * 8 + 44).sp
    return Placeholder(
        width = width,
        height = 32.sp,
        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
    )
}

@Composable
private fun MarkdownNode(
    node: ASTNode,
    content: String,
    copyMarkerMap: Map<String, CopyMarkerToken>
) {
    when (node.type) {
        MarkdownElementTypes.MARKDOWN_FILE -> node.children.fastForEach { child -> MarkdownNode(child, content, copyMarkerMap) }
        MarkdownElementTypes.PARAGRAPH -> Paragraph(node = node, content = content, copyMarkerMap = copyMarkerMap)
        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6 -> Heading(node = node, content = content, copyMarkerMap = copyMarkerMap)
        MarkdownElementTypes.UNORDERED_LIST -> ListNode(node = node, content = content, ordered = false, copyMarkerMap = copyMarkerMap)
        MarkdownElementTypes.ORDERED_LIST -> ListNode(node = node, content = content, ordered = true, copyMarkerMap = copyMarkerMap)
        MarkdownElementTypes.BLOCK_QUOTE -> QuoteBlock(node = node, content = content, copyMarkerMap = copyMarkerMap)
        MarkdownTokenTypes.HORIZONTAL_RULE -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
            thickness = 1.dp
        )
        MarkdownElementTypes.INLINE_LINK -> LinkNode(node = node, content = content, copyMarkerMap = copyMarkerMap)
        GFMElementTypes.INLINE_MATH -> LatexInline(latex = node.getText(content), modifier = Modifier.padding(horizontal = 1.dp))
        GFMElementTypes.BLOCK_MATH -> LatexBlock(latex = node.getText(content), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        MarkdownElementTypes.CODE_SPAN -> CodeSpan(node = node, content = content, copyMarkerMap = copyMarkerMap)
        MarkdownElementTypes.CODE_FENCE -> CodeFence(node = node, content = content, copyMarkerMap = copyMarkerMap)
        MarkdownElementTypes.IMAGE -> ImageNode(node = node, content = content, copyMarkerMap = copyMarkerMap)
        MarkdownElementTypes.HTML_BLOCK -> HtmlBlock(html = node.getText(content), modifier = Modifier.fillMaxWidth())
        GFMTokenTypes.CHECK_BOX -> CheckBox(node = node, content = content, copyMarkerMap = copyMarkerMap)
        GFMElementTypes.TABLE -> MarkdownTable(node = node, content = content, copyMarkerMap = copyMarkerMap)
        MarkdownTokenTypes.TEXT -> InlineMarkdownText(text = node.getText(content), copyMarkerMap = copyMarkerMap)
        else -> node.children.fastForEach { child -> MarkdownNode(child, content, copyMarkerMap) }
    }
}

@Composable
private fun Heading(
    node: ASTNode,
    content: String,
    copyMarkerMap: Map<String, CopyMarkerToken>
) {
    val style = when (node.type) {
        MarkdownElementTypes.ATX_1 -> MaterialTheme.typography.headlineLarge
        MarkdownElementTypes.ATX_2 -> MaterialTheme.typography.headlineMedium
        MarkdownElementTypes.ATX_3 -> MaterialTheme.typography.headlineSmall
        MarkdownElementTypes.ATX_4 -> MaterialTheme.typography.titleLarge
        MarkdownElementTypes.ATX_5 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    val topPadding = when (node.type) {
        MarkdownElementTypes.ATX_1 -> 22.dp
        MarkdownElementTypes.ATX_2 -> 20.dp
        MarkdownElementTypes.ATX_3 -> 18.dp
        MarkdownElementTypes.ATX_4 -> 16.dp
        MarkdownElementTypes.ATX_5 -> 14.dp
        else -> 12.dp
    }
    val bottomPadding = when (node.type) {
        MarkdownElementTypes.ATX_1 -> 12.dp
        MarkdownElementTypes.ATX_2 -> 10.dp
        MarkdownElementTypes.ATX_3 -> 10.dp
        MarkdownElementTypes.ATX_4 -> 8.dp
        MarkdownElementTypes.ATX_5 -> 8.dp
        else -> 6.dp
    }
    ProvideTextStyle(style) {
        node.children.fastForEach { child ->
            if (child.type == MarkdownTokenTypes.ATX_CONTENT) {
                Paragraph(
                    node = child,
                    content = content,
                    copyMarkerMap = copyMarkerMap,
                    trim = true,
                    topPadding = topPadding,
                    bottomPadding = bottomPadding
                )
            }
        }
    }
}

@Composable
private fun QuoteBlock(
    node: ASTNode,
    content: String,
    copyMarkerMap: Map<String, CopyMarkerToken>
) {
    Column(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(start = 10.dp, top = 8.dp, end = 12.dp, bottom = 8.dp)
    ) {
        node.children.fastForEach { child -> MarkdownNode(node = child, content = content, copyMarkerMap = copyMarkerMap) }
    }
}

@Composable
private fun LinkNode(
    node: ASTNode,
    content: String,
    copyMarkerMap: Map<String, CopyMarkerToken>
) {
    val linkText = node.findChildRecursive(MarkdownElementTypes.LINK_TEXT)
        ?.findChildRecursive(GFMTokenTypes.GFM_AUTOLINK, MarkdownTokenTypes.TEXT)
        ?.getText(content)
        .orEmpty()
    val linkDest = node.findChildRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getText(content).orEmpty()
    val context = LocalContext.current
    Text(
        text = linkText.ifBlank { linkDest },
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, linkDest.toUri()))
            }
        }
    )
}

@Composable
private fun CodeSpan(
    node: ASTNode,
    content: String,
    copyMarkerMap: Map<String, CopyMarkerToken>
) {
    Text(
        text = node.getText(content).trim('`'),
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun CodeFence(
    node: ASTNode,
    content: String,
    copyMarkerMap: Map<String, CopyMarkerToken>
) {
    val code = extractCodeFenceContent(node, content)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun ImageNode(
    node: ASTNode,
    content: String,
    copyMarkerMap: Map<String, CopyMarkerToken>
) {
    val altText = node.findChildRecursive(MarkdownElementTypes.LINK_TEXT)?.getText(content).orEmpty()
    val imageUrl = node.findChildRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getText(content).orEmpty()
    if (imageUrl.isNotBlank()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = altText,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
private fun CheckBox(
    node: ASTNode,
    content: String,
    copyMarkerMap: Map<String, CopyMarkerToken>
) {
    val isChecked = node.getText(content).trim().equals("[x]", ignoreCase = true)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isChecked) "☑" else "☐",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MarkdownTable(
    node: ASTNode,
    content: String,
    copyMarkerMap: Map<String, CopyMarkerToken>
) {
    val headerNode = node.children.find { it.type == GFMElementTypes.HEADER }
    val rows = node.children.filter { it.type == GFMElementTypes.ROW }
    val columnCount = headerNode?.children?.count { it.type == GFMTokenTypes.CELL } ?: 0
    if (columnCount == 0) return

    val headerCells = headerNode?.children.orEmpty()
        .filter { it.type == GFMTokenTypes.CELL }
        .map { it.getText(content).trim() }

    val dataRows = rows.map { row ->
        row.children.filter { it.type == GFMTokenTypes.CELL }.map { it.getText(content).trim() }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TableRow(
                cells = List(columnCount) { index -> headerCells.getOrNull(index).orEmpty() },
                header = true,
                copyMarkerMap = copyMarkerMap
            )
            dataRows.forEachIndexed { rowIndex, row ->
                if (rowIndex == 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                }
                TableRow(
                    cells = List(columnCount) { index -> row.getOrNull(index).orEmpty() },
                    header = false,
                    copyMarkerMap = copyMarkerMap
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    header: Boolean,
    copyMarkerMap: Map<String, CopyMarkerToken>
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            Box(
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .padding(horizontal = 10.dp, vertical = if (header) 10.dp else 8.dp)
            ) {
                if (header) {
                    Text(
                        text = cell,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    InlineMarkdownText(text = cell, copyMarkerMap = copyMarkerMap)
                }
            }
        }
    }
}

@Composable
private fun HtmlBlock(
    html: String,
    modifier: Modifier = Modifier
) {
    val contentColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val bodyTextSize = MaterialTheme.typography.bodyMedium.fontSize.value
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(contentColor)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                textSize = bodyTextSize
            }
        },
        update = { textView ->
            textView.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        }
    )
}

@Composable
private fun ListNode(
    node: ASTNode,
    content: String,
    ordered: Boolean,
    copyMarkerMap: Map<String, CopyMarkerToken>
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        var index = 1
        node.children.fastForEach { child ->
            if (child.type != MarkdownElementTypes.LIST_ITEM) return@fastForEach
            val bullet = if (ordered) {
                child.findChildRecursive(MarkdownTokenTypes.LIST_NUMBER)?.getText(content) ?: "${index++}. "
            } else {
                "• "
            }
            ListItem(node = child, content = content, bullet = bullet, copyMarkerMap = copyMarkerMap)
        }
    }
}

@Composable
private fun ListItem(
    node: ASTNode,
    content: String,
    bullet: String,
    copyMarkerMap: Map<String, CopyMarkerToken>
) {
    val (directContent, nestedLists) = splitListItem(node)
    Column {
        if (directContent.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = bullet,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    directContent.fastForEach { child -> MarkdownNode(node = child, content = content, copyMarkerMap = copyMarkerMap) }
                }
            }
        }
        nestedLists.fastForEach { nested -> MarkdownNode(node = nested, content = content, copyMarkerMap = copyMarkerMap) }
    }
}

@Composable
private fun Paragraph(
    node: ASTNode,
    content: String,
    trim: Boolean = false,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    copyMarkerMap: Map<String, CopyMarkerToken> = emptyMap()
) {
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val textStyle = androidx.compose.material3.LocalTextStyle.current
    val inlineContents = remember(node) {
        mutableMapOf<String, InlineTextContent>()
    }
    val hasInlineMath = remember(node) {
        node.findChildRecursive(GFMElementTypes.INLINE_MATH) != null
    }

    val annotated = remember(content, node, trim) {
        buildAnnotatedString {
            node.children.fastForEach { child ->
                appendMarkdownNodeContent(
                    node = child,
                    content = content,
                    trim = trim,
                    inlineContents = inlineContents,
                    copyMarkerMap = copyMarkerMap,
                    colorScheme = colorScheme,
                    density = density,
                    style = textStyle
                )
            }
        }
    }

    Text(
        text = annotated,
        inlineContent = inlineContents,
        softWrap = true,
        overflow = TextOverflow.Visible,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding),
        style = textStyle.copy(
            lineHeight = if (hasInlineMath) TextUnit.Unspecified else textStyle.lineHeight
        )
    )
}

private fun splitListItem(node: ASTNode): Pair<List<ASTNode>, List<ASTNode>> {
    val direct = mutableListOf<ASTNode>()
    val nested = mutableListOf<ASTNode>()
    node.children.fastForEach { child ->
        when (child.type) {
            MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> nested.add(child)
            else -> direct.add(child)
        }
    }
    return direct to nested
}

private fun AnnotatedString.Builder.appendMarkdownNodeContent(
    node: ASTNode,
    content: String,
    trim: Boolean,
    inlineContents: MutableMap<String, InlineTextContent>,
    copyMarkerMap: Map<String, CopyMarkerToken>,
    colorScheme: androidx.compose.material3.ColorScheme,
    density: Density,
    style: TextStyle
) {
    when {
        node.type == GFMTokenTypes.GFM_AUTOLINK -> {
            val link = node.getText(content)
            withLink(LinkAnnotation.Url(link)) {
                withStyle(SpanStyle(color = colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                    append(link)
                }
            }
        }

        node is LeafASTNode -> {
            val text = node.getText(content).let { if (trim) it.trim() else it }.replace(breakLineRegex, "\n")
            if (node.type !in MarkdownInlineDelimiters) {
                appendCopyAwareText(
                    text = text,
                    inlineContents = inlineContents,
                    copyMarkerMap = copyMarkerMap,
                    colorScheme = colorScheme,
                    density = density,
                    style = style
                )
            }
        }

        node.type == MarkdownElementTypes.EMPH -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                node.children.fastForEach {
                    appendMarkdownNodeContent(
                        node = it,
                        content = content,
                        trim = trim,
                        inlineContents = inlineContents,
                        copyMarkerMap = copyMarkerMap,
                        colorScheme = colorScheme,
                        density = density,
                        style = style
                    )
                }
            }
        }

        node.type == MarkdownElementTypes.STRONG -> {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                node.children.fastForEach {
                    appendMarkdownNodeContent(
                        node = it,
                        content = content,
                        trim = trim,
                        inlineContents = inlineContents,
                        copyMarkerMap = copyMarkerMap,
                        colorScheme = colorScheme,
                        density = density,
                        style = style
                    )
                }
            }
        }

        node.type == GFMElementTypes.STRIKETHROUGH -> {
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                node.children.fastForEach {
                    appendMarkdownNodeContent(
                        node = it,
                        content = content,
                        trim = trim,
                        inlineContents = inlineContents,
                        copyMarkerMap = copyMarkerMap,
                        colorScheme = colorScheme,
                        density = density,
                        style = style
                    )
                }
            }
        }

        node.type == MarkdownElementTypes.INLINE_LINK -> {
            val linkDest = node.findChildRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getText(content).orEmpty()
            val linkText = node.findChildRecursive(MarkdownElementTypes.LINK_TEXT)?.getText(content).orEmpty()
            withLink(LinkAnnotation.Url(linkDest)) {
                withStyle(SpanStyle(color = colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                    appendCopyAwareText(
                        text = linkText.ifBlank { linkDest },
                        inlineContents = inlineContents,
                        copyMarkerMap = copyMarkerMap,
                        colorScheme = colorScheme,
                        density = density,
                        style = style
                    )
                }
            }
        }

        node.type == MarkdownElementTypes.AUTOLINK -> {
            val link = node.getText(content)
            withLink(LinkAnnotation.Url(link)) {
                withStyle(SpanStyle(color = colorScheme.primary, textDecoration = TextDecoration.Underline)) {
                    appendCopyAwareText(
                        text = link,
                        inlineContents = inlineContents,
                        copyMarkerMap = copyMarkerMap,
                        colorScheme = colorScheme,
                        density = density,
                        style = style
                    )
                }
            }
        }

        node.type == MarkdownElementTypes.CODE_SPAN -> {
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = colorScheme.surfaceVariant,
                    color = colorScheme.primary
                )
            ) {
                append(" ")
                appendCopyAwareText(
                    text = node.getText(content).trim('`'),
                    inlineContents = inlineContents,
                    copyMarkerMap = copyMarkerMap,
                    colorScheme = colorScheme,
                    density = density,
                    style = style
                )
                append(" ")
            }
        }

        node.type == GFMElementTypes.INLINE_MATH -> {
            val formula = node.getText(content)
            val key = formula
            val placeholder = runCatching {
                with(density) {
                    assumeLatexSize(formula, style.fontSize.toPx()).let { rect ->
                        if (rect.width() <= 0 || rect.height() <= 0) {
                            null
                        } else {
                            Placeholder(
                            width = rect.width().toFloat().toSp(),
                            height = rect.height().toFloat().toSp(),
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                            )
                        }
                    }
                }
            }.getOrNull()
            if (placeholder != null) {
                inlineContents.putIfAbsent(key, InlineTextContent(placeholder = placeholder) {
                    LatexInline(latex = formula)
                })
                appendInlineContent(key, "[math]")
            } else {
                append(formula)
            }
        }

        node.type == MarkdownElementTypes.IMAGE -> {
            val altText = node.findChildRecursive(MarkdownElementTypes.LINK_TEXT)?.getText(content).orEmpty()
            val imageUrl = node.findChildRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getText(content).orEmpty()
            appendCopyAwareText(
                text = altText.ifBlank { imageUrl },
                inlineContents = inlineContents,
                copyMarkerMap = copyMarkerMap,
                colorScheme = colorScheme,
                density = density,
                style = style
            )
        }

        else -> node.children.fastForEach {
            appendMarkdownNodeContent(
                node = it,
                content = content,
                trim = trim,
                inlineContents = inlineContents,
                copyMarkerMap = copyMarkerMap,
                colorScheme = colorScheme,
                density = density,
                style = style
            )
        }
    }
}

private val MarkdownInlineDelimiters = setOf(
    MarkdownTokenTypes.EMPH,
    MarkdownTokenTypes.CODE_FENCE_START,
    MarkdownTokenTypes.CODE_FENCE_END,
    MarkdownTokenTypes.BACKTICK,
    MarkdownTokenTypes.LBRACKET,
    MarkdownTokenTypes.RBRACKET,
    MarkdownTokenTypes.LPAREN,
    MarkdownTokenTypes.RPAREN
)

private data class CopyMarkerOccurrence(
    val start: Int,
    val length: Int,
    val key: String,
    val copyText: String
)

private fun findNextCopyMarkerOccurrence(
    text: String,
    startIndex: Int,
    copyMarkerMap: Map<String, CopyMarkerToken>
): CopyMarkerOccurrence? {
    val placeholderMatch = copyMarkerMap.values
        .mapNotNull { token ->
            val index = text.indexOf(token.placeholder, startIndex = startIndex)
            if (index >= 0) {
                CopyMarkerOccurrence(
                    start = index,
                    length = token.placeholder.length,
                    key = token.placeholder,
                    copyText = token.copyText
                )
            } else {
                null
            }
        }
        .minByOrNull { it.start }

    val rawMatch = copyMarkerRegex.find(text, startIndex)?.let { match ->
        val copyText = match.groupValues[1].trim()
        if (copyText.isBlank()) {
            null
        } else {
            CopyMarkerOccurrence(
                start = match.range.first,
                length = match.value.length,
                key = rawCopyInlineKey(match.range.first, match.value),
                copyText = copyText
            )
        }
    }

    return when {
        placeholderMatch == null -> rawMatch
        rawMatch == null -> placeholderMatch
        placeholderMatch.start <= rawMatch.start -> placeholderMatch
        else -> rawMatch
    }
}

private fun AnnotatedString.Builder.appendCopyAwareText(
    text: String,
    inlineContents: MutableMap<String, InlineTextContent>,
    copyMarkerMap: Map<String, CopyMarkerToken>,
    colorScheme: androidx.compose.material3.ColorScheme,
    density: Density,
    style: TextStyle
) {
    var cursor = 0
    while (cursor < text.length) {
        val nextMarker = findNextCopyMarkerOccurrence(
            text = text,
            startIndex = cursor,
            copyMarkerMap = copyMarkerMap
        )
        val strongStart = text.indexOf("**", startIndex = cursor)
        val nextBoundary = when {
            nextMarker != null -> nextMarker.start
            strongStart >= 0 -> strongStart
            else -> text.length
        }

        if (nextBoundary > cursor) {
            appendTextWithStrongFallback(text.substring(cursor, nextBoundary))
            cursor = nextBoundary
            continue
        }

        if (nextMarker != null && nextMarker.start == cursor) {
            inlineContents.putIfAbsent(nextMarker.key, InlineTextContent(copyPlaceholderFor(nextMarker.copyText)) {
                CopyMarkerInline(copyText = nextMarker.copyText)
            })
            appendInlineContent(nextMarker.key, "copy")
            cursor += nextMarker.length
        } else if (strongStart >= 0) {
            val strongEnd = text.indexOf("**", startIndex = strongStart + 2)
            if (strongEnd < 0) {
                append(text.substring(cursor))
                return
            }
            if (strongStart > cursor) {
                append(text.substring(cursor, strongStart))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(text.substring(strongStart + 2, strongEnd))
            }
            cursor = strongEnd + 2
        } else {
            append(text.substring(cursor))
            return
        }
    }
}

private fun rawCopyInlineKey(cursor: Int, value: String): String {
    return "raw-copy-$cursor-${value.hashCode()}"
}

private fun AnnotatedString.Builder.appendTextWithStrongFallback(text: String) {
    var cursor = 0
    while (cursor < text.length) {
        val strongStart = text.indexOf("**", cursor)
        if (strongStart < 0) {
            append(text.substring(cursor))
            return
        }

        val strongEnd = text.indexOf("**", strongStart + 2)
        if (strongEnd < 0) {
            append(text.substring(cursor))
            return
        }

        if (strongStart > cursor) {
            append(text.substring(cursor, strongStart))
        }

        val strongContent = text.substring(strongStart + 2, strongEnd)
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(strongContent)
        }

        cursor = strongEnd + 2
    }
}
