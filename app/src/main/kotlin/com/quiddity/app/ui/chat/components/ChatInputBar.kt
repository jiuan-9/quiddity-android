package com.quiddity.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释等）。
 *
 * 3. 构建交付要求
 *    在完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK 文件。
 *    APK 文件需满足以下条件：
 *    - 签名有效且符合发布标准
 *    - 包含完整的功能模块
 *    - 经过基础性能测试和兼容性测试
 *    以便在真实设备环境中进行功能验证和性能评估。
 *
 * ============================================================================
 */

/**
 * 群聊头像栏交互上下文（由 [ChatInputBar] 提供给 header）。
 *
 * - [isMentionPending]：当前输入是否处于"打出 @ 待选人"状态（输入末尾为 @）；
 * - [insertMention]：把 `@名字`（蓝色高亮）插入输入框。
 */
class MentionInputScope internal constructor(
    private val isMentionPendingState: () -> Boolean,
    private val insert: (String) -> Unit
) {
    val isMentionPending: Boolean get() = isMentionPendingState()
    fun insertMention(name: String) = insert(name)
}

/** 匹配 `@名字` 提及片段（@ 后到空白/下一个 @ 为止）。 */
private val MentionPattern = Regex("@[^\\s@]+")

// 当前规则：圆角 24dp 输入框；输入框统一固定 2 行高度（不随输入行数变化，超出内容在框内滚动）；
// 回车发送策略由 enterToSend 决定；壁纸模式下玻璃质感；
// 发送按钮三态（正常/停止/压缩置灰）；群聊成员头像栏并入输入框容器顶部（方案十一）。
@Composable
fun ChatInputBar(
    enterToSend: Boolean,
    isGenerating: Boolean,
    modifier: Modifier = Modifier,
    allowSendWhileGenerating: Boolean = false,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    enabled: Boolean = true,
    transparent: Boolean = false,
    onTextChange: ((String) -> Unit)? = null,
    isCompressing: Boolean = false,
    // 图片发送：待发送图片 URI（file:// 或 content://）+ 选择/移除回调
    onPickImage: (() -> Unit)? = null,
    pendingImageUri: String? = null,
    onRemoveImage: (() -> Unit)? = null,
    // 图片正在 OCR 识别中（按钮位置显示加载圈，阻止重复发送）
    ocrBusy: Boolean = false,
    // 输入框容器内的顶部内容（群聊成员头像栏，随输入框一起动）
    header: (@Composable (MentionInputScope) -> Unit)? = null
) {
    var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

    val density = LocalDensity.current
    // 固定 2 行输入框：行高 × 2 + 内部留白，不参与成员栏高度（成员栏是独立的顶部区域）
    val lineHeightPx = with(density) { MaterialTheme.typography.bodyMedium.lineHeight.roundToPx() }
    val fieldInternalPadPx = with(density) { 24.dp.roundToPx() }
    val fixedFieldHeightPx = (lineHeightPx * 2 + fieldInternalPadPx)
        .coerceAtLeast(with(density) { 48.dp.roundToPx() })
    val effectiveMaxLines = 2

    val text = textFieldValue.text

    fun insertMention(name: String) {
        if (name.isBlank()) return
        val current = textFieldValue.text
        val base = current.trimEnd()
        val newText = if (base.endsWith("@")) {
            base.dropLast(1) + "@$name "
        } else {
            "$base @$name "
        }
        textFieldValue = TextFieldValue(newText, selection = TextRange(newText.length))
        onTextChange?.invoke(newText)
    }

    val mentionScope = remember {
        MentionInputScope(
            isMentionPendingState = { textFieldValue.text.trimEnd().endsWith("@") },
            insert = { name -> insertMention(name) }
        )
    }

    fun trySend() {
        if (!enabled) return
        val v = textFieldValue.text.trim()
        // 有文字或已挂载图片均可发送（图片会先 OCR 识图）
        if ((v.isNotEmpty() || pendingImageUri != null) &&
            !isCompressing &&
            (allowSendWhileGenerating || !isGenerating)
        ) {
            onSend(v)
            textFieldValue = TextFieldValue("")
            onTextChange?.invoke("")
        }
    }

    // @ 提及蓝色高亮：从纯文本重建 AnnotatedString，长度不变，不影响光标定位
    val annotatedValue = remember(text) {
        buildAnnotatedString {
            append(text)
            MentionPattern.findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(
                        color = MentionHighlightColor,
                        fontWeight = FontWeight.SemiBold
                    ),
                    match.range.first,
                    match.range.last + 1
                )
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        color = if (transparent) Color.Transparent else MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (transparent) {
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                )
        ) {
            if (header != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                ) {
                    header(mentionScope)
                }
            }
            // 待发送图片预览（选中图片后显示，可移除）
            if (pendingImageUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = pendingImageUri,
                        contentDescription = "待发送图片",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (ocrBusy) "正在识别图片内容…" else "将识图后发送",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (onRemoveImage != null) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "移除图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onRemoveImage() }
                                .padding(3.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = textFieldValue.copy(annotatedString = annotatedValue),
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onTextChange?.invoke(newValue.text)
                    },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(with(density) { fixedFieldHeightPx.toDp() }),
                    placeholder = {
                        Text(
                            text = "输入消息…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    maxLines = effectiveMaxLines,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { trySend() }
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                // 图片附件按钮：点击选择图片；OCR 进行中显示加载圈
                if (onPickImage != null) {
                    Spacer(modifier = Modifier.size(2.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!ocrBusy) onPickImage()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (ocrBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AddPhotoAlternate,
                                contentDescription = "选择图片",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.size(4.dp))

                // ===== 发送按钮三态：isGenerating→停止(红)/isCompressing→置灰不可点/否则→正常发送 =====
                val buttonColor = when {
                    isGenerating -> MaterialTheme.colorScheme.error
                    isCompressing -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    text.isNotBlank() || pendingImageUri != null -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(buttonColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            when {
                                isGenerating -> onStop()
                                isCompressing -> { /* 压缩中：禁用发送，no-op */ }
                                else -> trySend()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isGenerating) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "停止",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = when {
                                isCompressing -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                text.isNotBlank() || pendingImageUri != null -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}