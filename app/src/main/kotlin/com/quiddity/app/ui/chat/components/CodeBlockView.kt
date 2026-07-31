package com.quiddity.app.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.CodeSyntaxHighlighter

/**
 *
 * 对齐 PC 端 `quiddity-code-block` 的视觉与交互：
 * - 顶部 header bar：品牌名 Quiddity + 语言标签 + 复制按钮 + 展开/收起按钮
 * - 主体：水平+垂直可滚动的代码区，应用语法高亮
 * - 默认展开；超过 [COLLAPSE_THRESHOLD_LINES] 行时自动折叠，显示"展开 N 行"按钮
 * - 圆角 + 暗色背景（即使在亮色主题下，代码块也使用暗色背景以匹配 IDE 体验）
 *
 * 即纯代码块消息会以全宽固定卡片显示，与普通文本气泡形成视觉区分。
 *
 * @param language 语言标识（如 "kotlin"、"python"）；空字符串或 "text" 表示纯文本无高亮
 * @param code 代码内容
 * @param modifier 外部 modifier
 * @param forceDarkBackground 强制使用暗色背景（默认 true，代码块无论主题如何都用暗色背景）
 */
@Composable
fun CodeBlockView(
    language: String,
    code: String,
    modifier: Modifier = Modifier,
    forceDarkBackground: Boolean = true,
    // 长代码的初始展开状态。默认 false（折叠），传 true 时长代码默认展开。
    // 用于刚流式完成的消息：用户刚看完流式输出，不希望代码"消失"成折叠预览。
    initiallyExpanded: Boolean = false
) {
    val context = LocalContext.current
    val lineCount = remember(code) { code.count { it == '\n' } + 1 }
    val isLongCode = lineCount > COLLAPSE_THRESHOLD_LINES

    // 展开状态：短代码默认展开；长代码根据 initiallyExpanded 决定
    // 使用 rememberSaveable 让展开状态在旋转/主题切换后保留
    var expanded by rememberSaveable(code, isLongCode) {
        mutableStateOf(!isLongCode || initiallyExpanded)
    }

    // 语法高亮（缓存，避免每次重组重算）
    val highlighted: AnnotatedString = remember(code, language) {
        if (language.isBlank() || language.equals("text", ignoreCase = true) ||
            language.equals("plain", ignoreCase = true)
        ) {
            AnnotatedString(code)
        } else {
            CodeSyntaxHighlighter.highlight(code, language)
        }
    }

    // 代码块容器：暗色背景（IDE 风格）
    val backgroundColor = if (forceDarkBackground) {
        Color(0xFF1E1E2E) // 暗色背景（与 IDE 一致）
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val headerBackgroundColor = if (forceDarkBackground) {
        Color(0xFF181825) // 略深的 header 背景
    } else {
        MaterialTheme.colorScheme.surface
    }
    val headerTextColor = if (forceDarkBackground) {
        Color(0xFFCDD6F4)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val langTagColor = if (forceDarkBackground) {
        Color(0xFF89B4FA)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val buttonColor = if (forceDarkBackground) {
        Color(0xFFCDD6F4)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(
                    Motion.DurationMedium,
                    easing = Motion.EasingEmphasizedDecelerate
                )
            ),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        tonalElevation = 0.dp
    ) {
        Column {
            // ===== Header bar：品牌名 + 语言 + 复制 + 展开/收起 =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBackgroundColor)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 品牌名 Quiddity
                Text(
                    text = "Quiddity",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    color = headerTextColor.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.size(6.dp))
                // 语言标签
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = langTagColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = language.ifBlank { "text" },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = langTagColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // 行数指示（折叠时显示）
                if (isLongCode && !expanded) {
                    Text(
                        text = "$lineCount 行",
                        style = MaterialTheme.typography.labelSmall,
                        color = headerTextColor.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                // 复制按钮
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            copyToClipboard(context, code)
                            Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制代码",
                        tint = buttonColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
                // 展开/收起按钮（仅长代码显示）
                if (isLongCode) {
                    Spacer(modifier = Modifier.size(4.dp))
                    val toggleInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = toggleInteraction,
                                indication = null
                            ) { expanded = !expanded },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = buttonColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ===== 代码内容区 =====
            // 折叠时显示前 N 行 + 渐变遮罩；展开时显示全部
            // 注意：不使用 verticalScroll —— 它在无界高度的 Column/Surface 中会触发
            // IllegalStateException(infinity maximum height constraints)。
            // 代码块的长度控制由 expand/collapse 机制处理：
            // - 长代码默认折叠，仅显示预览
            // - 展开后完整显示，由外层 LazyColumn 负责滚动
            AnimatedVisibility(
                visible = expanded || !isLongCode,
                enter = expandVertically(
                    animationSpec = tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate)
                ) + fadeIn(tween(Motion.DurationMedium)),
                exit = shrinkVertically(
                    animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                ) + fadeOut(tween(Motion.DurationShort))
            ) {
                SelectionContainer(
                    modifier = Modifier
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = highlighted,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        ),
                        color = CodeSyntaxHighlighter.COLOR_PLAIN
                    )
                }
            }

            // 折叠时的预览：前几行 + "点击展开"按钮
            if (!expanded && isLongCode) {
                val previewLines = remember(code) {
                    code.split("\n").take(COLLAPSE_PREVIEW_LINES).joinToString("\n")
                }
                val previewHighlighted = remember(previewLines, language) {
                    if (language.isBlank() || language.equals("text", ignoreCase = true)) {
                        AnnotatedString(previewLines)
                    } else {
                        CodeSyntaxHighlighter.highlight(previewLines, language)
                    }
                }
                SelectionContainer(
                    modifier = Modifier
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = previewHighlighted,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        ),
                        color = CodeSyntaxHighlighter.COLOR_PLAIN
                    )
                }
                // 展开按钮
                val expandInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = expandInteraction,
                            indication = null
                        ) { expanded = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = langTagColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "展开全部 $lineCount 行",
                        style = MaterialTheme.typography.labelSmall,
                        color = langTagColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 *
 * 用于渲染"纯文本/Markdown 内容"，作为独立卡片显示，与气泡做视觉区分。
 *
 * 与 [CodeBlockView] 的差异：
 * - 不应用语法高亮（按原文本显示）
 * - 不带语言标签
 * - 仍保留复制按钮和展开/收起功能
 *
 * @param content 纯文本内容
 * @param formatLabel 格式标签（如 "txt"、"markdown"、"text"），显示在 header
 * @param modifier 外部 modifier
 */
@Composable
fun FencedTextView(
    content: String,
    formatLabel: String = "text",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lineCount = remember(content) { content.count { it == '\n' } + 1 }
    val isLongText = lineCount > COLLAPSE_THRESHOLD_LINES
    var expanded by rememberSaveable(content, isLongText) {
        mutableStateOf(!isLongText)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(
                    Motion.DurationMedium,
                    easing = Motion.EasingEmphasizedDecelerate
                )
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quiddity",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = formatLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isLongText && !expanded) {
                    Text(
                        text = "$lineCount 行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                // 复制按钮
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            copyToClipboard(context, content)
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (isLongText) {
                    Spacer(modifier = Modifier.size(4.dp))
                    val toggleInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = toggleInteraction,
                                indication = null
                            ) { expanded = !expanded },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 内容区
            AnimatedVisibility(
                visible = expanded || !isLongText,
                enter = expandVertically(
                    animationSpec = tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate)
                ) + fadeIn(tween(Motion.DurationMedium)),
                exit = shrinkVertically(
                    animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                ) + fadeOut(tween(Motion.DurationShort))
            ) {
                SelectionContainer(
                    modifier = Modifier
                        .padding(12.dp)
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!expanded && isLongText) {
                val preview = remember(content) {
                    content.split("\n").take(COLLAPSE_PREVIEW_LINES).joinToString("\n")
                }
                SelectionContainer(
                    modifier = Modifier
                        .padding(12.dp)
                ) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                val expandInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = expandInteraction,
                            indication = null
                        ) { expanded = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "展开全部 $lineCount 行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 内联代码块渲染（用于普通气泡内部混合内容中的代码块）。
 *
 * 与 [CodeBlockView] 的差异：
 * - 不带 header（节省气泡内空间）
 * - 默认折叠长代码
 * - 仍保留复制按钮和语法高亮
 */
@Composable
fun InlineCodeBlock(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lineCount = remember(code) { code.count { it == '\n' } + 1 }
    val isLongCode = lineCount > COLLAPSE_THRESHOLD_LINES
    var expanded by rememberSaveable(code, isLongCode) {
        mutableStateOf(!isLongCode)
    }

    val highlighted: AnnotatedString = remember(code, language) {
        if (language.isBlank() || language.equals("text", ignoreCase = true)) {
            AnnotatedString(code)
        } else {
            CodeSyntaxHighlighter.highlight(code, language)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(
                    Motion.DurationMedium,
                    easing = Motion.EasingEmphasizedDecelerate
                )
            ),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E1E2E),
        tonalElevation = 0.dp
    ) {
        Column {
            // 简化 header：仅语言标签 + 复制 + 展开
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "text" },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF89B4FA),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF89B4FA).copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isLongCode && !expanded) {
                    Text(
                        text = "$lineCount 行",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCDD6F4).copy(alpha = 0.5f),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) {
                            copyToClipboard(context, code)
                            Toast.makeText(context, "代码已复制", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "复制代码",
                        tint = Color(0xFFCDD6F4),
                        modifier = Modifier.size(12.dp)
                    )
                }
                if (isLongCode) {
                    val toggleInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(
                                interactionSource = toggleInteraction,
                                indication = null
                            ) { expanded = !expanded },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = Color(0xFFCDD6F4),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded || !isLongCode,
                enter = expandVertically(
                    animationSpec = tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate)
                ) + fadeIn(tween(Motion.DurationMedium)),
                exit = shrinkVertically(
                    animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                ) + fadeOut(tween(Motion.DurationShort))
            ) {
                SelectionContainer(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = highlighted,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        color = CodeSyntaxHighlighter.COLOR_PLAIN
                    )
                }
            }

            if (!expanded && isLongCode) {
                val previewLines = remember(code) {
                    code.split("\n").take(COLLAPSE_PREVIEW_LINES).joinToString("\n")
                }
                val previewHighlighted = remember(previewLines, language) {
                    if (language.isBlank() || language.equals("text", ignoreCase = true)) {
                        AnnotatedString(previewLines)
                    } else {
                        CodeSyntaxHighlighter.highlight(previewLines, language)
                    }
                }
                SelectionContainer(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = previewHighlighted,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        color = CodeSyntaxHighlighter.COLOR_PLAIN
                    )
                }
                val expandInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = expandInteraction,
                            indication = null
                        ) { expanded = true }
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF89B4FA),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.size(3.dp))
                    Text(
                        text = "展开 $lineCount 行",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF89B4FA),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 复制文本到系统剪贴板。
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("code", text))
}

/** 折叠阈值：超过此行数的代码块默认折叠。 */
private const val COLLAPSE_THRESHOLD_LINES = 15

/** 折叠时预览的行数。 */
private const val COLLAPSE_PREVIEW_LINES = 8
