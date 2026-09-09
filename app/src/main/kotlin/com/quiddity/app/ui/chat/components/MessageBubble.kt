package com.quiddity.app.ui.chat.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.DateUtils
import com.quiddity.app.util.MarkdownParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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


// ===== 气泡统一样式常量（跨设备/跨渲染模式一致） =====
// 气泡外层最大宽度：与代码块一致使用 400dp 保持宽窄统一
internal val BubbleMaxWidth = 400.dp
// 气泡内部文本最大宽度：400 - 14*2 横向内边距 = 372，向上取整到 372dp 保证内边距准确
internal val BubbleInnerMaxWidth = 372.dp

// 气泡形状：用户消息右下角尖角 4dp；AI 消息左下角尖角 4dp
internal fun BubbleShape(isUser: Boolean) =
    if (isUser) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

// 当前规则：纯代码块用围栏卡片；混合内容用普通气泡内嵌代码块；流式中只用纯文本。
@Composable
fun MessageBubble(
    message: Message,
    isGroupChat: Boolean = false,
    userAvatarUri: String?,
    aiAvatarUri: String?,
    aiName: String? = null,
    senderName: String? = null,
    senderAvatarUri: String? = null,
    bracketGrayEnabled: Boolean = false,
    markdownEnabled: Boolean = false,
    isLastAiMessage: Boolean = false,
    onRegenerate: (() -> Unit)? = null,
    onContinue: (() -> Unit)? = null,
    onWithdraw: (() -> Unit)? = null,
    isWithdrawing: Boolean = false,
    onBubbleClick: (() -> Unit)? = null,
    // 长按气泡时触发，进入多选模式
    onLongClick: (() -> Unit)? = null,
    // 单击 AI 气泡展开/收起操作面板（重说/继续说/改写/删除），全局只保留一个展开项
    isActionsExpanded: Boolean = false,
    onToggleActions: (() -> Unit)? = null,
    // 点击"改写"按钮时触发，进入改写界面
    onRewrite: (() -> Unit)? = null,
    // 查找聊天记录跳转高亮：命中消息气泡短暂变色
    isHighlighted: Boolean = false,
    // 新消息入场动画：仅对"本会话打开后新到达"的消息播放淡入上浮；
    // 历史消息滚动回来时不重放，避免整屏反复闪动
    animateEntry: Boolean = true,
    // ===== 多选模式 =====
    multiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null
) {
    val isUser = message.role == Role.USER
    val isStreaming = message.isStreaming
    val isError = message.isError
    val isAiNotStreaming = !isUser && !isStreaming
    val isThinking = message.isThinking

    val colorScheme = MaterialTheme.colorScheme
    val bubbleColor = remember(isUser, isError, isHighlighted, isThinking, colorScheme) {
        // 毛玻璃半透明：透出壁纸背景，文字保持不透明可读
        when {
            isThinking -> colorScheme.surfaceContainerHigh
            isError -> colorScheme.errorContainer
            isHighlighted -> colorScheme.primaryContainer
            isUser -> colorScheme.secondary
            else -> colorScheme.surfaceVariant
        }.copy(alpha = 0.60f)
    }
    // 玻璃边缘：1dp 半透明描边，模拟磨砂玻璃高光边界
    val bubbleBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val textColor = remember(isUser, isError, isHighlighted, isThinking, colorScheme) {
        when {
            isThinking -> colorScheme.onSurfaceVariant
            isError -> colorScheme.onErrorContainer
            isHighlighted -> colorScheme.onPrimaryContainer
            isUser -> colorScheme.onSecondary
            else -> colorScheme.onSurfaceVariant
        }
    }
    val grayColor = remember(textColor) { textColor.copy(alpha = 0.55f) }

    val avatarUri = if (isUser) userAvatarUri else (senderAvatarUri ?: aiAvatarUri)
    // ===== 加载动画：流式文字自然显示，光标随 isStreaming 实时出现/停止 =====
    val fullContent = message.content
    val content = fullContent

    var wasStreamed by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(isStreaming) {
        if (isStreaming) wasStreamed = true
    }

    // ===== 新消息入场动画（明显可感知：400ms 淡入 + 上浮 + 轻微放大） =====
    // 流式 delta 很快，逐字/逐段淡入感知不到；改为整条消息出现时一次性淡入，
    // 文字随气泡一起柔和显现。
    val entryAlpha = remember(message.id, animateEntry) {
        Animatable(if (animateEntry) 0f else 1f)
    }
    val entryOffsetY = remember(message.id, animateEntry) { Animatable(0f) }
    val entryScale = remember(message.id, animateEntry) {
        Animatable(if (animateEntry) 0.97f else 1f)
    }
    val entryOffsetPx = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(message.id, animateEntry) {
        if (animateEntry) {
            entryAlpha.snapTo(0f)
            entryOffsetY.snapTo(entryOffsetPx)
            entryScale.snapTo(0.97f)
            val spec: androidx.compose.animation.core.FiniteAnimationSpec<Float> =
                tween(Motion.DurationXLong, easing = Motion.EasingEmphasizedDecelerate)
            launch { entryAlpha.animateTo(1f, spec) }
            launch { entryOffsetY.animateTo(0f, spec) }
            launch { entryScale.animateTo(1f, spec) }
        }
    }

    val bubbleInteractionSource = remember { MutableInteractionSource() }
    val isBubblePressed by bubbleInteractionSource.collectIsPressedAsState()
    // bubbleScale 用 State 持有而非 by 委托：按压动画期间值变化只在 graphicsLayer draw phase 读取，零重组
    val bubbleScaleState = animateFloatAsState(
        targetValue = if (isBubblePressed && isUser) 0.97f else 1f,
        animationSpec = Motion.SpringSoft,
        label = "bubble_press_scale"
    )

    val aiBubbleClick = if (!isUser && isAiNotStreaming && onToggleActions != null) {
        onToggleActions
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
    // 关键性能优化：流式中用纯文本渲染（避免每 token 都重跑 MarkdownParser.parse），
    // 流结束后用 message.id 作 key 解析一次，之后保持稳定。
    // 改写/撤回等「原地替换内容」场景 id 不变，必须以内容作 key 才能让解析结果刷新，
    // 否则气泡会一直显示旧文本；流式中 key 固定为 null 避免每个 token 都重启解析协程。
    // 性能：Markdown 解析移到后台线程（完成消息时不再占主线程掉帧）
    // Lint 规则 ProduceStateDoesNotAssignValue 无法识别 if/else 双分支赋值（误报）；
    // 实际两条路径均赋值 value，解析结果随 message.id / 稳定内容（非流式全文）正确更新。
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val parsedContent by produceState(
        initialValue = ParsedMessageContent(emptyList(), MarkdownParser.ParsedMarkdown("", emptyList())),
        key1 = message.id,
        key2 = if (isStreaming) null else fullContent
    ) {
        if (isStreaming || fullContent.isEmpty()) {
            value = ParsedMessageContent(emptyList(), MarkdownParser.ParsedMarkdown(fullContent, emptyList()))
        } else {
            value = withContext(Dispatchers.Default) {
                ParsedMessageContent(
                    MarkdownParser.parse(fullContent),
                    MarkdownParser.parseMarkdown(fullContent)
                )
            }
        }
    }
    val blocks = parsedContent.blocks
    val parsedMarkdown = parsedContent.markdown
    val renderMode = remember(blocks, isStreaming, fullContent) {
        when {
            isStreaming || fullContent.isEmpty() -> RenderMode.PURE_TEXT
            blocks.size == 1 && blocks[0] is MarkdownParser.Block.CodeBlock -> RenderMode.PURE_CODE
            blocks.any { it is MarkdownParser.Block.CodeBlock } -> RenderMode.MIXED
            else -> RenderMode.PURE_TEXT
        }
    }

    // 括号灰化：流式中用 content 实时计算（轻量级 substring 操作，可接受）；
    // 流结束后用稳定的 AnnotatedString 缓存（避免每次重组重算）。
    val annotatedContent = remember(message.id, isStreaming, content, bracketGrayEnabled) {
        if (isStreaming) grayifyBrackets(content, bracketGrayEnabled, grayColor)
        else null
    }
    val stableAnnotated = remember(message.id, bracketGrayEnabled, parsedMarkdown) {
        if (!isStreaming) grayifyBrackets(parsedMarkdown.text, bracketGrayEnabled, grayColor) else null
    }
    val effectiveAnnotated = annotatedContent
        ?: stableAnnotated
        ?: grayifyBrackets(parsedMarkdown.text, bracketGrayEnabled, grayColor)
    // 群聊：@提及蓝色高亮（叠加在括号灰化之上）
    val displayAnnotated = remember(parsedMarkdown.text, effectiveAnnotated, isGroupChat) {
        if (isGroupChat) highlightMentions(parsedMarkdown.text, effectiveAnnotated, MentionHighlightColor)
        else effectiveAnnotated
    }
    // Markdown 渲染：流式中保持纯文本（避免每 token 重跑解析与样式闪烁），
    // 流结束后叠加标题/加粗/斜体/链接等样式；关闭开关时退化为纯文本。
    val displayWithMarkdown = remember(parsedMarkdown, displayAnnotated, markdownEnabled, colorScheme) {
        if (isStreaming || !markdownEnabled) displayAnnotated
        else applyMarkdownStyles(parsedMarkdown, displayAnnotated, colorScheme.primary, grayColor)
    }

    // ===== 气泡主体 =====
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .graphicsLayer {
                alpha = entryAlpha.value
                translationY = entryOffsetY.value
                scaleX = entryScale.value
                scaleY = entryScale.value
            }
    ) {
        // 群聊发言者名字（方案十二.1-2：气泡上方显示名字；用户显示「我」）
        if (senderName != null && !multiSelectMode) {
            Text(
                text = senderName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isUser) TextAlign.End else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    // 群聊用户消息的「我」对齐到气泡右缘，不落在右侧头像上方
                    .then(if (isUser) Modifier.padding(end = 48.dp) else Modifier.padding(start = 48.dp))
                    .padding(bottom = 2.dp)
            )
        }
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
                AvatarSlot(avatarUri, name = senderName ?: aiName)
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
                            .align(Alignment.CenterVertically)
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

            // ===== 图片消息：固定卡片样式（缩略图 + 标签 + 文字，参考 DeepSeek 网页版） =====
            if (message.imageUri?.isNotBlank() == true) {
                ImageMessageBubble(
                    imageUri = message.imageUri.orEmpty(),
                    text = displayWithMarkdown,
                    isUser = isUser,
                    isStreaming = isStreaming,
                    textColor = textColor,
                    multiSelectMode = multiSelectMode,
                    onSelectToggle = onSelectToggle,
                    onBubbleClick = if (isUser) onBubbleClick else null,
                    onLongClick = onLongClick,
                    bubbleScaleState = bubbleScaleState,
                    bubbleInteractionSource = bubbleInteractionSource,
                    bubbleColor = bubbleColor,
                    bubbleBorderColor = bubbleBorderColor
                )
            } else {
                // ===== 渲染分流 =====
                when (renderMode) {
                RenderMode.PURE_CODE -> {
                    val codeBlock = blocks[0] as MarkdownParser.Block.CodeBlock
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = BubbleMaxWidth)
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
                                        mod.combinedClickable(
                                            interactionSource = bubbleInteractionSource,
                                            indication = null,
                                            onClick = aiBubbleClick,
                                            onLongClick = onLongClick
                                        )
                                    }
                                    else -> mod
                                }
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            BubbleThinkingHeader(
                                thinking = message.thinking,
                                isStreaming = isStreaming,
                                contentEmpty = content.isBlank(),
                                isThinking = isThinking
                            )
                            CodeBlockView(
                                language = codeBlock.language,
                                code = codeBlock.code,
                                initiallyExpanded = wasStreamed
                            )
                        }
                    }
                }
                RenderMode.MIXED -> {
                    // 改用 Box+background+clip 替代 Surface，避免每次滚动都触发
                    // CompositionLocalProvider / elevation 处理，大幅降低重组开销
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = BubbleMaxWidth)
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
                                        mod.combinedClickable(
                                            interactionSource = bubbleInteractionSource,
                                            indication = null,
                                            onClick = aiBubbleClick,
                                            onLongClick = onLongClick
                                        )
                                    }
                                    else -> mod
                                }
                            }
                            .clip(BubbleShape(isUser))
                            .border(1.dp, bubbleBorderColor, BubbleShape(isUser))
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
                            // ===== 应用内本地思考：气泡内可展开/收起（思考中为高亮滑块） =====
                            BubbleThinkingHeader(
                                thinking = message.thinking,
                                isStreaming = isStreaming,
                                contentEmpty = content.isBlank(),
                                isThinking = isThinking
                            )
                            if (isStreaming && content.isEmpty()) {
                                TypingIndicator()
                            } else {
                                blocks.forEach { block ->
                                    when (block) {
                                        is MarkdownParser.Block.Text -> {
                                            val parsedBlock = remember(block.content) {
                                                MarkdownParser.parseMarkdown(block.content)
                                            }
                                            val textAnnotated = remember(
                                                parsedBlock,
                                                block.content,
                                                bracketGrayEnabled,
                                                markdownEnabled,
                                                colorScheme
                                            ) {
                                                val base =
                                                    grayifyBrackets(parsedBlock.text, bracketGrayEnabled, grayColor)
                                                if (!markdownEnabled) base
                                                else applyMarkdownStyles(
                                                    parsedBlock,
                                                    base,
                                                    colorScheme.primary,
                                                    grayColor
                                                )
                                            }
                                            SelectableMessageText(
                                                text = textAnnotated,
                                                textColor = textColor,
                                                onBubbleClick = if (multiSelectMode) onSelectToggle else (if (isUser) onBubbleClick else null),
                                                onLongClick = if (multiSelectMode || !isUser) null else onLongClick,
                                                modifier = Modifier.widthIn(max = BubbleInnerMaxWidth)
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
                            .widthIn(max = BubbleMaxWidth)
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
                                        mod.combinedClickable(
                                            interactionSource = bubbleInteractionSource,
                                            indication = null,
                                            onClick = aiBubbleClick,
                                            onLongClick = onLongClick
                                        )
                                    }
                                    else -> mod
                                }
                            }
                            .clip(BubbleShape(isUser))
                            .border(1.dp, bubbleBorderColor, BubbleShape(isUser))
                            .background(bubbleColor)
                    ) {
                        // 内容自适应宽度（不 fillMaxWidth）：短消息窄气泡、长消息顶到上限，
                        // 修复「气泡宽度固定」——由文本实际宽度决定，而不是永远占满可用宽度
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            // ===== 应用内本地思考：气泡内可展开/收起（思考中为高亮滑块） =====
                            BubbleThinkingHeader(
                                thinking = message.thinking,
                                isStreaming = isStreaming,
                                contentEmpty = content.isBlank(),
                                isThinking = isThinking
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                if (isStreaming && content.isEmpty()) {
                                    TypingIndicator()
                                } else {
                                    SelectableMessageText(
                                        text = displayWithMarkdown,
                                        textColor = textColor,
                                        onBubbleClick = if (multiSelectMode) onSelectToggle else (if (isUser) onBubbleClick else null),
                                        onLongClick = if (multiSelectMode || !isUser) null else onLongClick,
                                        modifier = Modifier.widthIn(max = BubbleInnerMaxWidth)
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
                }
            }

            // ===== 私聊改写按钮（点击 AI 气泡展开；全局只保留一个展开项） =====
            if (!isGroupChat && !isUser) {
                AnimatedVisibility(
                    visible = isActionsExpanded && !multiSelectMode && onRewrite != null,
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
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(start = 8.dp)
                    ) {
                        BubbleActionChip(
                            icon = Icons.Filled.Edit,
                            label = "改写",
                            onClick = { onRewrite?.invoke() }
                        )
                    }
                }
            }

            if (isUser) {
                Spacer(modifier = Modifier.size(8.dp))
                AvatarSlot(avatarUri)
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

        // ===== 发送时间（24 小时制小字，显示在气泡下方） =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Text(
                text = DateUtils.formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(
                    start = if (isUser) 0.dp else 48.dp,
                    end = if (isUser) 48.dp else 0.dp,
                    top = 2.dp
                )
            )
        }

        // ===== 私聊：重说 / 继续说 常驻在最后一条 AI 消息下方（淡入 + 撑开动画） =====
        AnimatedVisibility(
            visible = !isGroupChat && !isUser && !multiSelectMode && isAiNotStreaming &&
                (onRegenerate != null || onContinue != null),
            enter = fadeIn(
                animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)
            ) + expandVertically(
                animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate),
                expandFrom = Alignment.Top
            ),
            exit = fadeOut(
                animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
            ) + shrinkVertically(
                animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate),
                shrinkTowards = Alignment.Top
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

        // ===== 群聊：AI 操作面板（点击气泡展开；全局只保留一个展开项） =====
        // 面板参与布局，展开/收起时把后面的消息平滑挤下去/收上来，不与气泡重叠。
        AnimatedVisibility(
            visible = isGroupChat && !isUser && isActionsExpanded && !multiSelectMode && isAiNotStreaming &&
                (onRegenerate != null || onRewrite != null),
            enter = fadeIn(
                animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)
            ) + expandVertically(
                animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate),
                expandFrom = Alignment.Top
            ),
            exit = fadeOut(
                animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
            ) + shrinkVertically(
                animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate),
                shrinkTowards = Alignment.Top
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onRegenerate != null) {
                    BubbleActionChip(
                        icon = Icons.Filled.Refresh,
                        label = "重说",
                        onClick = onRegenerate
                    )
                }
                if (onRewrite != null) {
                    BubbleActionChip(
                        icon = Icons.Filled.Edit,
                        label = "改写",
                        onClick = onRewrite
                    )
                }
            }
        }
    }
}

/**
 * 消息内容的一次性解析结果：围栏代码块拆分 + 行内 Markdown 样式。
 * 两者都只在消息流结束后按 message.id 解析一次，随后保持稳定。
 */
