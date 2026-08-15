package com.quiddity.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quiddity.app.ui.chat.components.StreamingCursor
import com.quiddity.app.util.MarkdownParser


/** Agent 消息正文：Markdown 行内样式 + 代码块；仅列表部分进专用方框，其余正文保持普通样式。 */
@Composable
internal fun AgentMarkdownText(
    content: String,
    isStreaming: Boolean,
    markdownEnabled: Boolean,
    color: Color
) {
    val colorScheme = MaterialTheme.colorScheme
    val blocks = remember(content) { MarkdownParser.parse(content) }
    if (markdownEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownParser.Block.Text ->
                        AgentMixedText(block.content, color)
                    is MarkdownParser.Block.CodeBlock ->
                        AgentCodeBlock(language = block.language, code = block.code)
                }
            }
            if (isStreaming && content.isNotEmpty()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Spacer(modifier = Modifier.size(2.dp))
                    StreamingCursor()
                }
            }
        }
    } else {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
            if (isStreaming && content.isNotEmpty()) {
                Spacer(modifier = Modifier.size(2.dp))
                StreamingCursor()
            }
        }
    }
}

@Composable
internal fun AgentCodeBlock(language: String, code: String) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (language.isNotBlank()) {
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = colorScheme.onSurface
        )
    }
}

internal fun agentApplyMarkdownStyles(
    parsed: MarkdownParser.ParsedMarkdown,
    baseColor: Color,
    linkColor: Color,
    dimColor: Color
): AnnotatedString {
    if (parsed.spans.isEmpty()) return AnnotatedString(parsed.text)
    val builder = buildAnnotatedString {
        append(parsed.text)
        for (span in parsed.spans) {
            val start = span.start.coerceIn(0, parsed.text.length)
            val end = span.end.coerceIn(start, parsed.text.length)
            if (start >= end) continue
            when (span) {
                is MarkdownParser.MarkdownSpan.Link -> {
                    addLink(
                        LinkAnnotation.Url(
                            url = span.url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        ),
                        start,
                        end
                    )
                }
                is MarkdownParser.MarkdownSpan.Bold ->
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor), start, end)
                is MarkdownParser.MarkdownSpan.Italic ->
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor), start, end)
                is MarkdownParser.MarkdownSpan.Strikethrough ->
                    addStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseColor),
                        start,
                        end
                    )
                is MarkdownParser.MarkdownSpan.Code ->
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFF1E1E2E),
                            color = Color(0xFFCDD6F4)
                        ),
                        start,
                        end
                    )
                is MarkdownParser.MarkdownSpan.Heading ->
                    addStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = baseColor,
                            fontSize = when (span.level) {
                                1 -> 23.sp
                                2 -> 21.sp
                                3 -> 19.sp
                                else -> 18.sp
                            }
                        ),
                        start,
                        end
                    )
                is MarkdownParser.MarkdownSpan.Quote ->
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = dimColor), start, end)
                is MarkdownParser.MarkdownSpan.Bullet ->
                    addStyle(
                        SpanStyle(color = dimColor, fontWeight = FontWeight.SemiBold),
                        start,
                        end
                    )
            }
        }
    }
    return builder
}

@Composable
internal fun AgentMixedText(content: String, baseColor: Color) {    val colorScheme = MaterialTheme.colorScheme
    val segments = remember(content) { splitListSegments(content) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        segments.forEach { (isList, text) ->
            val parsed = remember(text) { MarkdownParser.parseMarkdown(text) }
            val styled = remember(parsed, baseColor) {
                agentApplyMarkdownStyles(
                    parsed,
                    baseColor,
                    colorScheme.primary,
                    colorScheme.onSurfaceVariant
                )
            }
            if (isList) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceVariant.copy(alpha = 0.32f))
                        .border(
                            width = 1.dp,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = styled,
                        style = MaterialTheme.typography.bodyMedium,
                        color = baseColor
                    )
                }
            } else {
                Text(
                    text = styled,
                    style = MaterialTheme.typography.bodyMedium,
                    color = baseColor
                )
            }
        }
    }
}

internal fun splitListSegments(content: String): List<Pair<Boolean, String>> {
    if (content.isBlank()) return emptyList()
    val segments = mutableListOf<Pair<Boolean, MutableList<String>>>()
    content.lines().forEach { rawLine ->
        val line = rawLine.trimEnd()
        val t = line.trim()
        val isList = t.isNotEmpty() && (
            t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") ||
                t.startsWith("• ") || Regex("^\\d+[.)] ").containsMatchIn(t)
            )
        val last = segments.lastOrNull()
        if (last != null && last.first == isList) {
            last.second.add(line)
        } else {
            segments.add(isList to mutableListOf(line))
        }
    }
    return segments.map { (isList, lines) ->
        isList to lines.joinToString("\n").trim()
    }.filter { it.second.isNotEmpty() }
}

internal fun splitToolSegments(content: String, ends: List<Int>): List<String> {
    if (ends.isEmpty() || content.isEmpty()) return listOf(content)
    val segments = mutableListOf<String>()
    var start = 0
    for (end in ends) {
        val e = end.coerceIn(start, content.length)
        if (e > start) {
            segments.add(content.substring(start, e))
            start = e
        }
    }
    if (start < content.length) segments.add(content.substring(start))
    return segments
}
