package com.quiddity.app.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.MarkdownParser

// 当前规则：纯代码块用围栏卡片；混合内容用普通气泡内嵌代码块；流式中只用纯文本。
@Composable
fun MessageBubble(
    message: Message,
    userAvatarUri: String?,
    aiAvatarUri: String?,
    bracketGrayEnabled: Boolean = false,
    isLastAiMessage: Boolean = false,
    onRegenerate: (() -> Unit)? = null,
    onContinue: (() -> Unit)? = null,
    onWithdraw: (() -> Unit)? = null,
    isWithdrawing: Boolean = false,
    onBubbleClick: (() -> Unit)? = null,
    // 长按气泡时触发，进入多选模式
    onLongClick: (() -> Unit)? = null,
    // 单击气泡时触发，用于切换"改写"按钮的显示状态
    onRewriteTrigger: (() -> Unit)? = null,
    // 点击"改写"按钮时触发，进入改写界面
    onRewrite: (() -> Unit)? = null,
    isRewriting: Boolean = false,
    // 打字机效果：UI 层逐字渲染（仅对 streaming AI 消息生效）
    typingDelayEnabled: Boolean = false,
    typingDelayMsPerChar: Int = 0,
    // ===== 多选模式 =====
    multiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null
) {
    val isUser = message.role == Role.USER
    val isStreaming = message.isStreaming
    val isError = message.isError
    val isAiNotStreaming = !isUser && !isStreaming

    val bubbleColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val grayColor = textColor.copy(alpha = 0.55f)

    val avatarUri = if (isUser) userAvatarUri else aiAvatarUri
    val avatarIcon = if (isUser) Icons.Filled.Person else Icons.Filled.Person

    // ===== 打字机效果：UI 层逐字渲染 =====
    val fullContent = message.content
    val typingActive = isStreaming && typingDelayEnabled && typingDelayMsPerChar > 0
    var displayedLength by remember(message.id) { mutableIntStateOf(0) }

    var wasStreamed by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(isStreaming) {
        if (isStreaming) wasStreamed = true
    }

    // 当前规则：LaunchedEffect key 不含 fullContent，避免每个 token 重启协程；用 snapshotFlow 监听内容增长，逐步推进 displayedLength。
    LaunchedEffect(message.id, isStreaming, typingDelayEnabled, typingDelayMsPerChar) {
        if (!typingActive) {
            displayedLength = fullContent.length
        } else {
            snapshotFlow { fullContent.length }.collect { targetLen ->
                while (displayedLength < targetLen) {
                    kotlinx.coroutines.delay(typingDelayMsPerChar.toLong())
                    displayedLength = (displayedLength + 1).coerceAtMost(targetLen)
                }
            }
        }
    }
    if (displayedLength > fullContent.length) {
        displayedLength = fullContent.length
    }
    val content = if (typingActive) {
        fullContent.substring(0, displayedLength.coerceIn(0, fullContent.length))
    } else {
        fullContent
    }

    val bubbleInteractionSource = remember { MutableInteractionSource() }
    val isBubblePressed by bubbleInteractionSource.collectIsPressedAsState()
    // bubbleScale 用 State 持有而非 by 委托：按压动画期间值变化只在 graphicsLayer draw phase 读取，零重组
    val bubbleScaleState = animateFloatAsState(
        targetValue = if (isBubblePressed && isUser) 0.97f else 1f,
        animationSpec = Motion.SpringSoft,
        label = "bubble_press_scale"
    )

    val aiBubbleClick = if (!isUser && isAiNotStreaming && onRewriteTrigger != null) {
        onRewriteTrigger
    } else null

    val showWithdraw = isWithdrawing

    val withdrawEnter = fadeIn(
        animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)
    ) + slideInHorizontally(
        initialOffsetX = { -it / 4 },
        animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)
    )
    val withdrawExit = fadeOut(
        animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
    ) + slideOutHorizontally(
        targetOffsetX = { -it / 4 },
        animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
    )

    // ===== 内容渲染分流决策 =====
    val canRenderBlocks = !isStreaming && !isError && fullContent.isNotEmpty()
    val blocks = remember(fullContent, canRenderBlocks) {
        if (canRenderBlocks) MarkdownParser.parse(fullContent) else emptyList()
    }
    val renderMode = remember(blocks, canRenderBlocks) {
        if (!canRenderBlocks) RenderMode.PURE_TEXT
        else when {
            blocks.size == 1 && blocks[0] is MarkdownParser.Block.CodeBlock -> RenderMode.PURE_CODE
            blocks.size == 1 && blocks[0] is MarkdownParser.Block.Text &&
                !MarkdownParser.hasCodeBlocks(fullContent) -> RenderMode.PURE_TEXT
            blocks.any { it is MarkdownParser.Block.CodeBlock } -> RenderMode.MIXED
            else -> RenderMode.PURE_TEXT
        }
    }

    val annotatedContent = remember(content, bracketGrayEnabled) {
        grayifyBrackets(content, bracketGrayEnabled, grayColor)
    }

    // ===== 气泡主体 =====
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            if (isUser) {
                Spacer(modifier = Modifier.size(48.dp))
            }

            // ===== 多选模式：AI 消息左侧显示选择圈 =====
            if (!isUser && multiSelectMode) {
                SelectionCircle(isSelected = isSelected)
                Spacer(modifier = Modifier.size(8.dp))
            }

            if (!isUser) {
                AvatarSlot(avatarUri, avatarIcon)
                Spacer(modifier = Modifier.size(8.dp))
            }

            if (isUser && onWithdraw != null && !multiSelectMode) {
                AnimatedVisibility(
                    visible = showWithdraw,
                    enter = withdrawEnter,
                    exit = withdrawExit
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Top)
                            .padding(end = 8.dp)
                    ) {
                        BubbleActionChip(
                            icon = Icons.AutoMirrored.Filled.Undo,
                            label = "撤回",
                            onClick = { onWithdraw() }
                        )
                    }
                }
            }

            // ===== 渲染分流 =====
            when (renderMode) {
                RenderMode.PURE_CODE -> {
                    val codeBlock = blocks[0] as MarkdownParser.Block.CodeBlock
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = 400.dp)
                            .graphicsLayer {
                                scaleX = bubbleScaleState.value
                                scaleY = bubbleScaleState.value
                            }
                            .let { mod ->
                                if (multiSelectMode && onSelectToggle != null) {
                                    mod.clickable(
                                        interactionSource = bubbleInteractionSource,
                                        indication = null,
                                        onClick = onSelectToggle
                                    )
                                } else {
                                    mod
                                }
                            }
                    ) {
                        CodeBlockView(
                            language = codeBlock.language,
                            code = codeBlock.code,
                            initiallyExpanded = wasStreamed
                        )
                    }
                }
                RenderMode.MIXED -> {
                    // 改用 Box+background+clip 替代 Surface，避免每次滚动都触发
                    // CompositionLocalProvider / elevation 处理，大幅降低重组开销
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = 300.dp)
                            .graphicsLayer {
                                scaleX = bubbleScaleState.value
                                scaleY = bubbleScaleState.value
                            }
                            .let { mod ->
                                when {
                                    multiSelectMode && onSelectToggle != null -> {
                                        mod.clickable(
                                            interactionSource = bubbleInteractionSource,
                                            indication = null,
                                            onClick = onSelectToggle
                                        )
                                    }
                                    isUser && onBubbleClick != null -> {
                                        mod.clickable(
                                            interactionSource = bubbleInteractionSource,
                                            indication = null,
                                            onClick = onBubbleClick
                                        )
                                    }
                                    aiBubbleClick != null -> {
                                        mod.clickable(
                                            interactionSource = bubbleInteractionSource,
                                            indication = null,
                                            onClick = aiBubbleClick
                                        )
                                    }
                                    else -> mod
                                }
                            }
                            .clip(
                                if (isUser) {
                                    RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                                } else {
                                    RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                                }
                            )
                            .background(bubbleColor)
                    ) {
                        // ===== 三条开发规范（位于文件中间位置） =====
                        // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
                        //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
                        // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
                        //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
                        // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
                        //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 流式输出中且内容为空时显示三点脉冲 TypingIndicator
                            if (isStreaming && content.isEmpty()) {
                                TypingIndicator()
                            } else {
                                blocks.forEach { block ->
                                    when (block) {
                                        is MarkdownParser.Block.Text -> {
                                            val textAnnotated = remember(block.content, bracketGrayEnabled) {
                                                grayifyBrackets(block.content, bracketGrayEnabled, grayColor)
                                            }
                                            SelectableMessageText(
                                                text = textAnnotated,
                                                textColor = textColor,
                                                onBubbleClick = if (multiSelectMode) onSelectToggle else (if (isUser) onBubbleClick else null),
                                                onLongClick = if (multiSelectMode) null else onLongClick,
                                                modifier = Modifier.widthIn(max = 272.dp)
                                            )
                                        }
                                        is MarkdownParser.Block.CodeBlock -> {
                                            InlineCodeBlock(
                                                language = block.language,
                                                code = block.code,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                if (isStreaming) {
                                    Spacer(modifier = Modifier.size(2.dp))
                                    StreamingCursor()
                                }
                            }
                        }
                    }
                }
                RenderMode.PURE_TEXT -> {
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = 300.dp)
                            .graphicsLayer {
                                scaleX = bubbleScaleState.value
                                scaleY = bubbleScaleState.value
                            }
                            .let { mod ->
                                when {
                                    multiSelectMode && onSelectToggle != null -> {
                                        mod.clickable(
                                            interactionSource = bubbleInteractionSource,
                                            indication = null,
                                            onClick = onSelectToggle
                                        )
                                    }
                                    isUser && onBubbleClick != null -> {
                                        mod.clickable(
                                            interactionSource = bubbleInteractionSource,
                                            indication = null,
                                            onClick = onBubbleClick
                                        )
                                    }
                                    aiBubbleClick != null -> {
                                        mod.clickable(
                                            interactionSource = bubbleInteractionSource,
                                            indication = null,
                                            onClick = aiBubbleClick
                                        )
                                    }
                                    else -> mod
                                }
                            }
                            .clip(
                                if (isUser) {
                                    RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                                } else {
                                    RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
                                }
                            )
                            .background(bubbleColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (isStreaming && content.isEmpty()) {
                                TypingIndicator()
                            } else {
                                SelectableMessageText(
                                    text = annotatedContent,
                                    textColor = textColor,
                                    onBubbleClick = if (multiSelectMode) onSelectToggle else (if (isUser) onBubbleClick else null),
                                    onLongClick = if (multiSelectMode) null else onLongClick,
                                    modifier = Modifier.widthIn(max = 272.dp)
                                )
                                if (isStreaming) {
                                    Spacer(modifier = Modifier.size(2.dp))
                                    StreamingCursor()
                                }
                            }
                        }
                    }
                }
            }

            if (isUser) {
                Spacer(modifier = Modifier.size(8.dp))
                AvatarSlot(avatarUri, avatarIcon)
            }

            // ===== 多选模式：用户消息右侧显示选择圈 =====
            if (isUser && multiSelectMode) {
                Spacer(modifier = Modifier.size(8.dp))
                SelectionCircle(isSelected = isSelected)
            }

            // AI 消息在对侧预留头像空间（40dp 头像 + 8dp 间距）
            if (!isUser) {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        // AI 消息（非 streaming）的"继续说 / 重说 / 改写"操作栏（多选模式下隐藏）
        if (!multiSelectMode && isAiNotStreaming && (onRegenerate != null || onContinue != null || onRewrite != null)) {
            Row(
                modifier = Modifier
                    .padding(start = 48.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 改写按钮（淡出动画，类似撤回按钮的浮现效果）
                if (onRewrite != null) {
                    AnimatedVisibility(
                        visible = isRewriting,
                        enter = fadeIn(
                            animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)
                        ) + slideInHorizontally(
                            initialOffsetX = { -it / 4 },
                            animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)
                        ),
                        exit = fadeOut(
                            animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                        ) + slideOutHorizontally(
                            targetOffsetX = { -it / 4 },
                            animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                        )
                    ) {
                        BubbleActionChip(
                            icon = Icons.Filled.Edit,
                            label = "改写",
                            onClick = onRewrite
                        )
                    }
                }
                if (onRegenerate != null) {
                    BubbleActionChip(
                        icon = Icons.Filled.Refresh,
                        label = "重说",
                        onClick = onRegenerate
                    )
                }
                if (onContinue != null) {
                    BubbleActionChip(
                        icon = Icons.Filled.PlayArrow,
                        label = "继续说",
                        onClick = onContinue
                    )
                }
            }
        }
    }
}

/**
 * 消息渲染模式。
 */
private enum class RenderMode {
    /** 纯文本消息：普通气泡渲染。 */
    PURE_TEXT,
    /** 纯代码块消息：全宽围栏卡片，不包裹气泡。 */
    PURE_CODE,
    /** 混合内容（文本 + 代码块）：气泡内嵌入代码块。 */
    MIXED
}

/**
 * 气泡下方的小型操作按钮（"重说" / "继续说" / "撤回"）。
 */
@Composable
private fun BubbleActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // scale 用 State 持有而非 by 委托：按压动画期间值变化只在 graphicsLayer draw phase 读取，零重组
    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = Motion.SpringSoft,
        label = "chip_press_scale"
    )
    // Box 替代 Surface：无 elevation 需求，Box+background+clip 跳过 Surface 的 CompositionLocalProvider 开销
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AvatarSlot(
    avatarUri: String?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUri != null) {
            AsyncImage(
                model = avatarUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * 多选模式下的选择圈指示器。
 *
 * - 选中：实心圆 + 白色对勾
 * - 未选中：透明圆 + 边框
 * - 尺寸与头像一致（40dp），视觉上与头像行对齐
 */
@Composable
private fun SelectionCircle(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * 消息文本（已移除 SelectionContainer 文字提取器）。
 *
 * 当前规则：
 * - 长按气泡进入多选模式（由父级 combinedClickable 处理），不再触发系统文本选择
 * - 保留括号灰化（AnnotatedString 原生 color span）
 * - 短按事件冒泡给父组件（USER 气泡的 onBubbleClick 仍能触发）
 *
 * @param text 已渲染的 AnnotatedString（含括号灰化 span）
 * @param textColor 文本主色
 * @param onBubbleClick USER 消息的点击回调；AI 消息传 null
 * @param modifier 外部 modifier
 */
@Composable
private fun SelectableMessageText(
    text: androidx.compose.ui.text.AnnotatedString,
    textColor: androidx.compose.ui.graphics.Color,
    onBubbleClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = textColor,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 24.sp,
            lineHeight = 32.sp
        ),
        modifier = modifier
            .widthIn(max = 360.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onBubbleClick?.invoke() },
                onLongClick = { onLongClick?.invoke() }
            )
    )
}

/**
 * 居中灰色提示气泡（isNotice=true 消息专用）。
 *
 * 用于快速设定后的场景/世界类型提示，让用户直观了解当前场景状态。
 * - 居中显示，无头像，无交互（不可撤回/改写/点击）
 * - 灰色半透明背景，视觉上与对话气泡区分
 * - 不发送给 LLM、不参与压缩、不导出
 * - 字体显著大于普通对话气泡，确保用户一眼可见当前场景/世界类型
 *
 * @param content 提示内容（如"都市世界 · 黄昏时森林中的小木屋"）
 */
@Composable
fun NoticeBubble(
    content: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = content,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
