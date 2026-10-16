package com.mucephei.app.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mucephei.app.theme.HandwritingTypography

sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Header(val level: Int, val text: String) : MarkdownBlock
    data class ListItem(
        val isOrdered: Boolean,
        val prefix: String,
        val indentLevel: Int,
        val text: String
    ) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    object Divider : MarkdownBlock
}

private val COMBINED_INLINE_REGEX = Regex(
    """```[^`]+```|`[^`\n]+`|\*\*\*([^\*\s][^\*]*?[^\*\s]|[^\*\s])\*\*\*|___([^_\s][^_]*?[^_\s]|[^_\s])___|\*\*([^\*\s][^\*]*?[^\*\s]|[^\*\s])\*\*|__([^_\s][^_]*?[^_\s]|[^_\s])__|(?<!\*)\*([^\*\s][^\*]*?[^\*\s]|[^\*\s])\*(?!\*)|(?<!_)_([^_\s][^_]*?[^_\s]|[^_\s])_(?!_)|~~(~?[^~\s][^~]*?[^~\s]|~?[^~\s])~~|\[([^\]]+)\]\(([^)]+)\)"""
)

fun parseInlineMarkdown(
    text: String,
    codeBgColor: Color = Color.Unspecified,
    codeTextColor: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified
): AnnotatedString {
    val matches = COMBINED_INLINE_REGEX.findAll(text).toList()
    if (matches.isEmpty()) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        var currentIndex = 0
        for (match in matches) {
            if (match.range.first > currentIndex) {
                append(text.substring(currentIndex, match.range.first))
            }

            val value = match.value
            when {
                value.startsWith("```") -> {
                    val content = value.removeSurrounding("```")
                    val start = length
                    append(content)
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBgColor,
                            color = codeTextColor,
                            fontSize = 0.9.em
                        ),
                        start,
                        length
                    )
                }
                value.startsWith("`") && value.endsWith("`") -> {
                    val content = value.substring(1, value.length - 1)
                    val start = length
                    append(content)
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBgColor,
                            color = codeTextColor,
                            fontSize = 0.9.em
                        ),
                        start,
                        length
                    )
                }
                value.startsWith("***") || value.startsWith("___") -> {
                    val content = value.substring(3, value.length - 3)
                    val inner = parseInlineMarkdown(content, codeBgColor, codeTextColor, linkColor)
                    val start = length
                    append(inner)
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                        start,
                        length
                    )
                }
                value.startsWith("**") || value.startsWith("__") -> {
                    val content = value.substring(2, value.length - 2)
                    val inner = parseInlineMarkdown(content, codeBgColor, codeTextColor, linkColor)
                    val start = length
                    append(inner)
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold),
                        start,
                        length
                    )
                }
                value.startsWith("*") || value.startsWith("_") -> {
                    val content = value.substring(1, value.length - 1)
                    val inner = parseInlineMarkdown(content, codeBgColor, codeTextColor, linkColor)
                    val start = length
                    append(inner)
                    addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic),
                        start,
                        length
                    )
                }
                value.startsWith("~~") && value.endsWith("~~") -> {
                    val content = value.substring(2, value.length - 2)
                    val inner = parseInlineMarkdown(content, codeBgColor, codeTextColor, linkColor)
                    val start = length
                    append(inner)
                    addStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough),
                        start,
                        length
                    )
                }
                value.startsWith("[") && value.contains("](") && value.endsWith(")") -> {
                    val labelEnd = value.indexOf("](")
                    val label = value.substring(1, labelEnd)
                    val url = value.substring(labelEnd + 2, value.length - 1)
                    val start = length
                    append(label)
                    addStyle(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        start,
                        length
                    )
                    addStringAnnotation("URL", url, start, length)
                }
                else -> {
                    append(value)
                }
            }
            currentIndex = match.range.last + 1
        }

        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}

fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    if (text.isBlank()) return emptyList()

    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()

    var inCodeBlock = false
    var codeLanguage = ""
    val codeBuffer = StringBuilder()

    var currentParagraph = StringBuilder()

    fun flushParagraph() {
        if (currentParagraph.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(currentParagraph.toString().trimEnd()))
            currentParagraph.clear()
        }
    }

    val unorderedListRegex = Regex("""^(\s*)[*+-]\s+(.*)$""")
    val orderedListRegex = Regex("""^(\s*)(\d+[\.\)])\s+(.*)$""")
    val headerRegex = Regex("""^(#{1,6})\s+(.*)$""")
    val dividerRegex = Regex("""^\s*([-*_]\s*){3,}\s*$""")
    val blockquoteRegex = Regex("""^\s*>\s*(.*)$""")

    for (line in lines) {
        if (inCodeBlock) {
            if (line.trim().startsWith("```")) {
                blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeBuffer.toString().trimEnd()))
                codeBuffer.clear()
                codeLanguage = ""
                inCodeBlock = false
            } else {
                if (codeBuffer.isNotEmpty()) codeBuffer.append("\n")
                codeBuffer.append(line)
            }
            continue
        }

        if (line.trim().startsWith("```")) {
            flushParagraph()
            inCodeBlock = true
            codeLanguage = line.trim().removePrefix("```").trim()
            codeBuffer.clear()
            continue
        }

        if (dividerRegex.matches(line)) {
            flushParagraph()
            blocks.add(MarkdownBlock.Divider)
            continue
        }

        val headerMatch = headerRegex.find(line)
        if (headerMatch != null) {
            flushParagraph()
            val level = headerMatch.groupValues[1].length
            val content = headerMatch.groupValues[2]
            blocks.add(MarkdownBlock.Header(level, content))
            continue
        }

        val unorderedMatch = unorderedListRegex.find(line)
        if (unorderedMatch != null) {
            flushParagraph()
            val indentSpaces = unorderedMatch.groupValues[1].length
            val indentLevel = indentSpaces / 2
            val content = unorderedMatch.groupValues[2]
            blocks.add(
                MarkdownBlock.ListItem(
                    isOrdered = false,
                    prefix = "•",
                    indentLevel = indentLevel,
                    text = content
                )
            )
            continue
        }

        val orderedMatch = orderedListRegex.find(line)
        if (orderedMatch != null) {
            flushParagraph()
            val indentSpaces = orderedMatch.groupValues[1].length
            val indentLevel = indentSpaces / 2
            val prefix = orderedMatch.groupValues[2]
            val content = orderedMatch.groupValues[3]
            blocks.add(
                MarkdownBlock.ListItem(
                    isOrdered = true,
                    prefix = prefix,
                    indentLevel = indentLevel,
                    text = content
                )
            )
            continue
        }

        val blockquoteMatch = blockquoteRegex.find(line)
        if (blockquoteMatch != null) {
            flushParagraph()
            val content = blockquoteMatch.groupValues[1]
            blocks.add(MarkdownBlock.Blockquote(content))
            continue
        }

        if (line.isBlank()) {
            flushParagraph()
            continue
        }

        if (currentParagraph.isNotEmpty()) {
            currentParagraph.append("\n")
        }
        currentParagraph.append(line)
    }

    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeBuffer.toString().trimEnd()))
    } else {
        flushParagraph()
    }

    return blocks
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = HandwritingTypography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    codeBgColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    codeTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    selectable: Boolean = false
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val context = LocalContext.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    val annotated = parseInlineMarkdown(
                        text = block.text,
                        codeBgColor = codeBgColor,
                        codeTextColor = codeTextColor,
                        linkColor = linkColor
                    )
                    if (selectable) {
                        SelectionContainer {
                            Text(
                                text = annotated,
                                style = style,
                                color = color
                            )
                        }
                    } else {
                        Text(
                            text = annotated,
                            style = style,
                            color = color
                        )
                    }
                }

                is MarkdownBlock.Header -> {
                    val headerStyle = when (block.level) {
                        1 -> HandwritingTypography.headlineLarge
                        2 -> HandwritingTypography.headlineMedium
                        3 -> HandwritingTypography.titleLarge
                        4 -> HandwritingTypography.titleMedium
                        else -> HandwritingTypography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    }
                    val annotated = parseInlineMarkdown(
                        text = block.text,
                        codeBgColor = codeBgColor,
                        codeTextColor = codeTextColor,
                        linkColor = linkColor
                    )
                    Text(
                        text = annotated,
                        style = headerStyle,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                is MarkdownBlock.ListItem -> {
                    val annotated = parseInlineMarkdown(
                        text = block.text,
                        codeBgColor = codeBgColor,
                        codeTextColor = codeTextColor,
                        linkColor = linkColor
                    )
                    val startPadding = (block.indentLevel * 16).dp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = startPadding),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = if (block.isOrdered) "${block.prefix} " else "• ",
                            style = style.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = annotated,
                            style = style,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = codeBgColor,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (block.language.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = block.language.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Code", block.code)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy code",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = block.code,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = codeTextColor
                                    )
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    val annotated = parseInlineMarkdown(
                        text = block.text,
                        codeBgColor = codeBgColor,
                        codeTextColor = codeTextColor,
                        linkColor = linkColor
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = annotated,
                            style = style.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
