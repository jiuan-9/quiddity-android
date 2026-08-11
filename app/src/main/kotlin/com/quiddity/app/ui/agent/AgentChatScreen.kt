package com.quiddity.app.ui.agent

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.chat.components.ChatInputBar
import com.quiddity.app.ui.chat.components.HamburgerMenu
import com.quiddity.app.ui.chat.components.StreamingCursor
import com.quiddity.app.ui.chat.components.TypingIndicator
import com.quiddity.app.ui.chat.gesture.ChatDragController
import com.quiddity.app.ui.chat.gesture.detectNativeHorizontalSwipe
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.DateUtils
import com.quiddity.app.util.MarkdownParser
import kotlinx.coroutines.launch

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，保证代码逻辑的连贯性、可维护性和可扩展性。
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
 * Agent 会话聊天页：纯文本直排、无气泡（Claude/ChatGPT 风格）。
 *
 * - 用户消息右对齐、AI 消息左对齐，时间戳小号灰色；
 * - Markdown 行内样式与代码块保留，但不套气泡容器；
 * - 汉堡菜单与私聊/群聊同一套 [HamburgerMenu]（会话设置/人设/模型配置）；
 * - 输入栏复用 [ChatInputBar]。
 */
@Composable
fun AgentChatScreen(
    viewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onConversationExit: () -> Unit = {}
) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showHamburger by rememberSaveable { mutableStateOf(false) }
    // 会话打开时刻：只有此后新到达的消息播放入场动画（历史消息滚动回来不重放）
    val openedAtMs = rememberSaveable { System.currentTimeMillis() }

    // ===== 滑动手势：与私聊/群聊同一套 ChatDragController =====
    // 右滑 1:1 跟手滑出会话（松手判定返回），左滑淡入菜单；菜单打开后右滑跟手关闭。
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val screenWidthPx = with(LocalDensity.current) {
        configuration.screenWidthDp.dp.toPx()
    }
    val swipeEnabled = !isGenerating
    val swipeEnabledState = rememberUpdatedState(swipeEnabled)
    val dragController = remember(screenWidthPx) {
        ChatDragController(
            scope = scope,
            screenWidthPx = screenWidthPx,
            onBack = onBack,
            onMenuVisibilityChange = { open -> showHamburger = open }
        )
    }

    DisposableEffect(Unit) {
        onDispose { onConversationExit() }
    }

    LaunchedEffect(chatError) {
        chatError?.let {
            Toast.makeText(context, it.userMessage, Toast.LENGTH_SHORT).show()
            viewModel.consumeChatError()
        }
    }

    // 新消息/内容增长时贴底（reverseLayout：index 0 = 最新）
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.any { !it.isNotice }) listState.animateScrollToItem(0)
    }

    BackHandler(enabled = !isGenerating && !showHamburger) {
        dragController.animateBackAndExit()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectNativeHorizontalSwipe(
                    enabled = { swipeEnabledState.value },
                    onDrag = { totalDx, _ -> dragController.onDrag(totalDx) },
                    onDragEnd = { totalDx, velocityDx ->
                        dragController.onDragEnd(totalDx, velocityDx)
                    },
                    onDragCancel = { dragController.onDragCancel() }
                )
            }
            .graphicsLayer {
                translationX = dragController.contentOffsetXState.floatValue
            }
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .imePadding()
        ) {
            // ===== 顶栏：返回 + 标题 + 汉堡（会话设置） =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { dragController.animateBackAndExit() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = conversation?.persona?.name?.takeIf { it.isNotBlank() }
                        ?: conversation?.title
                        ?: "Agent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { dragController.toggleMenu() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "菜单",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // ===== 消息列表（纯文本直排） =====
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                conversation == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
                messages.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(
                                    tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate)
                                )
                            ) {
                                // 与私聊空态同款圆角胶囊：浅色容器 + labelLarge 文案
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                        .padding(horizontal = 28.dp, vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "输入指令开始（只读工具默认开启）",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 生成中且尚无流式内容时，底部显示打字指示（紧贴输入栏）
                        val lastMsg = messages.lastOrNull { !it.isNotice }
                        val showTyping = isGenerating &&
                            (lastMsg == null || !(lastMsg.role == Role.ASSISTANT && lastMsg.isStreaming))
                        item(key = "agent_typing", contentType = { "typing" }) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                AnimatedVisibility(
                                    visible = showTyping,
                                    enter = fadeIn(
                                        tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)
                                    ) + expandVertically(
                                        tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)
                                    ),
                                    exit = fadeOut(
                                        tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                                    ) + shrinkVertically(
                                        tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        TypingIndicator()
                                    }
                                }
                            }
                        }
                        items(messages.reversed(), key = { it.id }) { message ->
                            AgentMessageLine(
                                message = message,
                                markdownEnabled = settings.markdownEnabled,
                                userAvatarUri = settings.userAvatarUri,
                                aiAvatarUri = conversation?.persona?.aiAvatarUri,
                                aiName = conversation?.persona?.name.orEmpty(),
                                animateEntry = message.timestamp >= openedAtMs
                            )
                        }
                    }
                }
            }
        }

        // ===== 输入栏（复用 Solo 输入条） =====
        ChatInputBar(
            enterToSend = settings.enterToSend,
            isGenerating = isGenerating,
            onSend = { text -> viewModel.sendMessage(text) },
            onStop = { viewModel.stopGeneration() },
            onTextChange = { text -> viewModel.updateInputText(text) },
            transparent = conversation?.wallpaperUri != null,
            enabled = conversation != null
        )
    }
    }

    // ===== 汉堡菜单：与私聊/群聊同一套（会话设置/人设/模型配置） =====
    HamburgerMenu(
        visible = showHamburger,
        menuAlphaState = dragController.menuAlphaState,
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        onDismiss = { dragController.closeMenu() },
        onDeleteConversation = {
            viewModel.deleteCurrentConversation()
            onBack()
        }
    )
}

/** 单条 Agent 消息：无气泡容器，用户右对齐 / AI 左对齐，时间戳小号灰色。 */
@Composable
private fun AgentMessageLine(
    message: Message,
    markdownEnabled: Boolean,
    userAvatarUri: String?,
    aiAvatarUri: String?,
    aiName: String,
    animateEntry: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    if (message.isNotice) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }

    // ===== 新消息入场动画（与气泡一致：淡入 + 上浮 + 轻微放大） =====
    val entryAlpha = remember(message.id, animateEntry) {
        Animatable(if (animateEntry) 0f else 1f)
    }
    val entryOffsetY = remember(message.id, animateEntry) { Animatable(0f) }
    val entryScale = remember(message.id, animateEntry) {
        Animatable(if (animateEntry) 0.97f else 1f)
    }
    val entryOffsetPx = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(message.id, animateEntry) {
        if (animateEntry) {
            entryAlpha.snapTo(0f)
            entryOffsetY.snapTo(entryOffsetPx)
            entryScale.snapTo(0.97f)
            val spec: FiniteAnimationSpec<Float> =
                tween(Motion.DurationXLong, easing = Motion.EasingEmphasizedDecelerate)
            launch { entryAlpha.animateTo(1f, spec) }
            launch { entryOffsetY.animateTo(0f, spec) }
            launch { entryScale.animateTo(1f, spec) }
        }
    }

    val isUser = message.role == Role.USER
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer {
                alpha = entryAlpha.value
                translationY = entryOffsetY.value
                scaleX = entryScale.value
                scaleY = entryScale.value
            },
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            AiAvatar(
                avatarUri = aiAvatarUri,
                name = aiName,
                size = 40.dp
            )
            Spacer(modifier = Modifier.size(10.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 460.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (message.isThinking) {
                Text(
                    text = if (message.isStreaming) "思考中" else "思考",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            AgentMarkdownText(
                content = message.content,
                isStreaming = message.isStreaming,
                markdownEnabled = markdownEnabled,
                color = if (isUser) colorScheme.onSurface else colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(3.dp))
            Text(
                text = DateUtils.formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        if (isUser) {
            Spacer(modifier = Modifier.size(10.dp))
            AiAvatar(
                avatarUri = userAvatarUri,
                name = "",
                size = 40.dp
            )
        }
    }
}

/** Agent 消息正文：Markdown 行内样式 + 代码块，无气泡容器。 */
@Composable
private fun AgentMarkdownText(
    content: String,
    isStreaming: Boolean,
    markdownEnabled: Boolean,
    color: Color
) {
    val colorScheme = MaterialTheme.colorScheme
    val parsed by androidx.compose.runtime.produceState(
        initialValue = MarkdownParser.ParsedMarkdown(content, emptyList()),
        key1 = content
    ) {
        if (isStreaming || content.isEmpty()) {
            value = MarkdownParser.ParsedMarkdown(content, emptyList())
        } else {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                MarkdownParser.parseMarkdown(content)
            }
        }
    }
    val annotated = remember(parsed, color) {
        agentApplyMarkdownStyles(parsed, color, colorScheme.primary, colorScheme.onSurfaceVariant)
    }
    if (markdownEnabled && !isStreaming) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MarkdownParser.parse(content).forEach { block ->
                when (block) {
                    is MarkdownParser.Block.Text -> {
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyMedium,
                            color = color
                        )
                    }
                    is MarkdownParser.Block.CodeBlock -> {
                        AgentCodeBlock(language = block.language, code = block.code)
                    }
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

/** 代码块：深色圆角容器 + 等宽字体，无气泡尾巴。 */
@Composable
private fun AgentCodeBlock(language: String, code: String) {
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

/** 把 Markdown 行内样式区间叠加到文本上（与气泡渲染同一套区间坐标）。 */
private fun agentApplyMarkdownStyles(
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
