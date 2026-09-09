package com.quiddity.app.ui.chat.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.quiddity.app.util.MarkdownParser


/**
 * 消息内容的一次性解析结果：围栏代码块拆分 + 行内 Markdown 样式。
 * 两者都只在消息流结束后按 message.id 解析一次，随后保持稳定。
 */
internal data class ParsedMessageContent(
    val blocks: List<MarkdownParser.Block>,
    val markdown: MarkdownParser.ParsedMarkdown
)

internal enum class RenderMode {
    /** 纯文本消息：普通气泡渲染。 */
    PURE_TEXT,
    /** 纯代码块消息：全宽围栏卡片，不包裹气泡。 */
    PURE_CODE,
    /** 混合内容（文本 + 代码块）：气泡内嵌入代码块。 */
    MIXED
}

internal fun applyMarkdownStyles(
    parsed: MarkdownParser.ParsedMarkdown,
    base: AnnotatedString,
    linkColor: Color,
    dimColor: Color
): AnnotatedString {
    if (parsed.spans.isEmpty()) return base
    val spanStyles = mutableListOf<AnnotatedString.Range<SpanStyle>>()
    val linkStyles = mutableListOf<AnnotatedString.Range<LinkAnnotation.Url>>()
    val length = base.text.length
    for (span in parsed.spans) {
        val start = span.start.coerceIn(0, length)
        val end = span.end.coerceIn(start, length)
        if (start >= end) continue
        when (span) {
            is MarkdownParser.MarkdownSpan.Link -> {
                linkStyles += AnnotatedString.Range(
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
                spanStyles += AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            is MarkdownParser.MarkdownSpan.Italic ->
                spanStyles += AnnotatedString.Range(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            is MarkdownParser.MarkdownSpan.Strikethrough ->
                spanStyles += AnnotatedString.Range(
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                    start,
                    end
                )
            is MarkdownParser.MarkdownSpan.Code ->
                spanStyles += AnnotatedString.Range(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = InlineCodeBackground,
                        color = InlineCodeForeground
                    ),
                    start,
                    end
                )
            is MarkdownParser.MarkdownSpan.Heading ->
                spanStyles += AnnotatedString.Range(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
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
                spanStyles += AnnotatedString.Range(
                    SpanStyle(fontStyle = FontStyle.Italic, color = dimColor),
                    start,
                    end
                )
            is MarkdownParser.MarkdownSpan.Bullet ->
                spanStyles += AnnotatedString.Range(
                    SpanStyle(color = dimColor, fontWeight = FontWeight.SemiBold),
                    start,
                    end
                )
        }
    }
    // 用 Builder 合并样式：保留 base 的全部 span / 段落样式，再叠加行内 Markdown 样式与链接注解
    val builder = AnnotatedString.Builder(base.text)
    base.spanStyles.forEach { builder.addStyle(it.item, it.start, it.end) }
    spanStyles.forEach { builder.addStyle(it.item, it.start, it.end) }
    linkStyles.forEach { builder.addLink(it.item, it.start, it.end) }
    return builder.toAnnotatedString()
}

@Composable
internal fun SelectableMessageText(
    text: androidx.compose.ui.text.AnnotatedString,
    textColor: androidx.compose.ui.graphics.Color,
    onBubbleClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    // 空 onClick 的 combinedClickable 会吞掉单击事件，导致外层气泡的点击永远收不到；
    // 所以没有可响应的回调时完全不挂载点击处理，让事件落到气泡外框上。
    val clickModifier = if (onBubbleClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { onBubbleClick?.invoke() },
            onLongClick = { onLongClick?.invoke() }
        )
    } else {
        Modifier
    }
    val context = LocalContext.current
    val uriHandler = remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) {
                runCatching {
                    val target = if (uri.contains("://")) uri else "https://$uri"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                }
            }
        }
    }
    // Markdown 链接（LinkAnnotation.Url）依赖 LocalUriHandler 打开浏览器
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 18.sp,
                lineHeight = 27.sp
            ),
            modifier = modifier.then(clickModifier)
        )
    }
}
