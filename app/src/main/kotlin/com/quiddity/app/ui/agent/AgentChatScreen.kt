package com.quiddity.app.ui.agent

import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.quiddity.app.data.model.Character
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.chat.OcrState
import com.quiddity.app.ui.chat.components.ChatInputBar
import com.quiddity.app.ui.chat.components.HamburgerMenu
import com.quiddity.app.ui.chat.components.StreamingCursor
import com.quiddity.app.ui.chat.components.TypingIndicator
import com.quiddity.app.ui.chat.gesture.ChatDragController
import com.quiddity.app.ui.chat.gesture.detectNativeHorizontalSwipe
import com.quiddity.app.domain.agent.AgentToolRegistry
import com.quiddity.app.util.ImageUtils
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
 * - 会话内设置仅角色卡（选择角色：从角色库点选，复用群聊同款角色列表 UI）；
 * - 输入栏复用 [ChatInputBar]，支持发图（选图 → OCR → 发送）。
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
    val toolUseName by viewModel.toolUse.collectAsStateWithLifecycle()
    val pendingConfirm by viewModel.pendingToolConfirm.collectAsStateWithLifecycle()
    val pendingImageUri by viewModel.pendingImageUri.collectAsStateWithLifecycle()
    val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showHamburger by rememberSaveable { mutableStateOf(false) }
    var showCharacterPicker by rememberSaveable { mutableStateOf(false) }
    var characterPickerClosing by rememberSaveable { mutableStateOf(false) }
    // 关闭选择角色面板的统一切口：先关面板并标记「退出动画中」，
    // 动画期间系统返回键只消费不退出会话，避免连续两次返回直接跳回会话列表。
    fun closeCharacterPicker() {
        if (showCharacterPicker) {
            showCharacterPicker = false
            characterPickerClosing = true
        }
    }
    // 打开会话内设置 / 选择角色等覆盖层时立即收起输入法，焦点已不在输入框
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(showHamburger, showCharacterPicker) {
        if (showHamburger || showCharacterPicker) keyboardController?.hide()
    }
    // 会话打开时刻：只有此后新到达的消息播放入场动画（历史消息滚动回来不重放）
    val openedAtMs = rememberSaveable { System.currentTimeMillis() }

    // ===== 发图：选图 → 复制到内部存储（规避临时授权丢失） → 挂载待发送 =====
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val copied = runCatching {
                ImageUtils.copyToInternalStorage(context, uri, "chat_images")
            }.getOrNull()
            if (copied != null) {
                viewModel.setPendingImage(copied.toString())
            } else {
                Toast.makeText(context, "图片读取失败，请重新选择", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var lastAttachedImageUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingImageUri) {
        val previous = lastAttachedImageUri
        lastAttachedImageUri = pendingImageUri
        if (pendingImageUri == null && previous != null) {
            ImageUtils.deleteTempFile(Uri.parse(previous))
        }
    }

    // ===== 滑动手势：与私聊/群聊同一套 ChatDragController =====
    // 右滑 1:1 跟手滑出会话（松手判定返回），左滑淡入角色卡；角色卡打开后右滑跟手关闭。
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
            onMenuVisibilityChange = { open -> showHamburger = open },
            onCharacterPickerDismiss = { closeCharacterPicker() }
        )
    }

    LaunchedEffect(showCharacterPicker) {
        dragController.updateCharacterPickerOpen(showCharacterPicker)
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

    // ===== 系统返回键：角色面板 -> 关闭面板；正常状态 -> 退出会话 =====
    // 合并为单一 BackHandler 状态机，避免多 handler 注册顺序带来的返回误判。
    BackHandler(
        enabled = showCharacterPicker || characterPickerClosing ||
            (!isGenerating && !showHamburger)
    ) {
        when {
            showCharacterPicker || characterPickerClosing -> closeCharacterPicker()
            else -> dragController.animateBackAndExit()
        }
    }

    LaunchedEffect(characterPickerClosing) {
        if (characterPickerClosing) {
            kotlinx.coroutines.delay(Motion.DurationPageTransition + 80L)
            characterPickerClosing = false
        }
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
                        // 工具使用报告条：AI 调用工具时显示（图标 + 工具名 + 在干的事，滑动高亮）
                        item(key = "agent_tool_use", contentType = { "tool_use" }) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                AnimatedVisibility(
                                    visible = toolUseName != null,
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
                                    toolUseName?.let { name ->
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            // 工具报告与正文之间的细淡分割线
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                            .copy(alpha = 0.14f)
                                                    )
                                            )
                                            Spacer(modifier = Modifier.size(8.dp))
                                            ToolUseShimmerLine(toolName = name)
                                        }
                                    }
                                }
                            }
                        }
                        items(messages.reversed(), key = { it.id }) { message ->
                            val latestAi = messages.lastOrNull {
                                !it.isNotice && it.role == Role.ASSISTANT && !it.isThinking
                            }
                            AgentMessageLine(
                                message = message,
                                markdownEnabled = settings.markdownEnabled,
                                userAvatarUri = settings.userAvatarUri,
                                aiAvatarUri = conversation?.persona?.aiAvatarUri,
                                aiName = conversation?.persona?.name.orEmpty(),
                                animateEntry = message.timestamp >= openedAtMs,
                                isLatestAi = message.id == latestAi?.id,
                                onCopy = { text -> copyToClipboard(context, text) },
                                onRegenerate = { viewModel.regenerate() }
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
            onSend = { text ->
                val imageUri = pendingImageUri
                if (imageUri != null) {
                    viewModel.sendMessageWithImage(context, text, imageUri)
                } else {
                    viewModel.sendMessage(text)
                }
            },
            onStop = { viewModel.stopGeneration() },
            onTextChange = { text -> viewModel.updateInputText(text) },
            transparent = conversation?.wallpaperUri != null,
            enabled = conversation != null,
            onPickImage = { imagePickerLauncher.launch("image/*") },
            pendingImageUri = pendingImageUri,
            onRemoveImage = { viewModel.clearPendingImage() },
            ocrBusy = ocrState is OcrState.Recognizing
        )
    }
    }

    // ===== 会话内设置：与私聊/群聊同一套完整汉堡菜单 =====
    HamburgerMenu(
        visible = showHamburger,
        menuAlphaState = dragController.menuAlphaState,
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        onDismiss = { dragController.closeMenu() },
        onDeleteConversation = {
            viewModel.deleteCurrentConversation()
            onBack()
        },
        // Agent 的「AI 人设」行 = 选择角色（角色库点选），不进入从零编辑表单
        onPersonaOverride = {
            // 保留汉堡菜单作为上一级：角色面板关闭（返回）后回到菜单
            characterPickerClosing = false
            showCharacterPicker = true
        },
        // 角色面板打开时禁用菜单自身 BackHandler，避免抢先消费返回键
        backHandlerEnabled = !showCharacterPicker && !characterPickerClosing
    )

    // ===== 选择角色浮层（与设置面板同款写法：遮罩淡入淡出，340dp 侧栏单独滑入） =====
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showCharacterPicker,
            enter = fadeIn(tween(Motion.DurationMedium)),
            exit = fadeOut(tween(Motion.DurationShort)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) {
                        detectTapGestures { closeCharacterPicker() }
                    }
            )
        }
        AnimatedVisibility(
            visible = showCharacterPicker,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard)
            ) + fadeIn(tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard)
            ) + fadeOut(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(340.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp, 0.dp, 0.dp, 20.dp),
                shadowElevation = 8.dp
            ) {
                AgentCharacterPicker(
                    conversation = conversation,
                    viewModel = viewModel,
                    onDismiss = { closeCharacterPicker() }
                )
            }
        }
    }

    // ===== 危险工具确认弹窗：每次执行写入类工具前必须用户确认 =====
    pendingConfirm?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.confirmTool(false) },
            title = { Text("确认执行危险操作") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Agent 请求执行「${AgentToolRegistry.displayName(pending.toolName)}」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val detail = buildString {
                        pending.args["pkg"]?.let { append("应用包名：$it\n") }
                        pending.args["op"]?.let { append("权限操作：$it\n") }
                        pending.args["mode"]?.let { append("权限模式：$it\n") }
                        if (isEmpty()) append(pending.args.toString())
                    }.trimEnd()
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "该操作每次都会要求你确认，且只对白名单内的应用生效。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmTool(true) }) { Text("确认执行") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmTool(false) }) { Text("取消") }
            }
        )
    }
}

/** 单条 Agent 消息：无气泡容器，用户右对齐 / AI 左对齐，时间戳小号灰色。 */
@Composable
private fun AgentMessageLine(
    message: Message,
    markdownEnabled: Boolean,
    userAvatarUri: String?,
    aiAvatarUri: String?,
    aiName: String,
    isLatestAi: Boolean = false,
    onCopy: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
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
        // 头像与第一行文字顶部齐平（无气泡直排风格）
        verticalAlignment = Alignment.Top
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
            // AI 消息操作：复制 / 重说（仅简单按钮；重说只出现在最新一条 AI 消息）
            if (!isUser && !message.isThinking && !message.isStreaming) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AgentActionButton(
                        icon = Icons.Filled.ContentCopy,
                        contentDescription = "复制",
                        onClick = { onCopy(message.content) }
                    )
                    if (isLatestAi) {
                        AgentActionButton(
                            icon = Icons.Filled.Refresh,
                            contentDescription = "重说",
                            onClick = onRegenerate
                        )
                    }
                }
            }
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

/** Agent 消息正文：Markdown 行内样式 + 代码块；仅列表部分进专用方框，其余正文保持普通样式。 */
@Composable
private fun AgentMarkdownText(
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

/** AI 消息下方的简单操作按钮：仅图标，无文字（与私聊操作一致的轻量样式）。 */
@Composable
private fun AgentActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.size(17.dp)
        )
    }
}

/** 文本块按行拆分：连续列表行合成“列表段”进方框，其余行合成“正文段”普通显示。 */
@Composable
private fun AgentMixedText(content: String, baseColor: Color) {
    val colorScheme = MaterialTheme.colorScheme
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

/** 把文本拆成 (是否列表段, 文本) 序列：列表 = 连续以 - / * / + / • / 数字序号 开头的行。 */
private fun splitListSegments(content: String): List<Pair<Boolean, String>> {
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

/** 工具使用报告条：图标 + 工具名 + 正在做的事；滑动高亮只作用于文字本身。 */
@Composable
private fun ToolUseShimmerLine(toolName: String) {
    val colorScheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "tool_use_shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing)
        ),
        label = "tool_use_progress"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Build,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val brush = Brush.linearGradient(
                colors = listOf(
                    colorScheme.onSurfaceVariant,
                    colorScheme.primary,
                    colorScheme.onSurfaceVariant
                ),
                start = Offset(widthPx * (progress - 0.5f), 0f),
                end = Offset(widthPx * (progress + 0.5f), 0f)
            )
            Text(
                text = "${AgentToolRegistry.displayName(toolName)} --- ${AgentToolRegistry.actionFor(toolName)}",
                style = MaterialTheme.typography.bodySmall.copy(brush = brush),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("消息", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

/** 选择角色卡：角色库头像列表，点选即应用到当前 Agent 会话（与群聊同款角色选择 UI）。 */
@Composable
private fun AgentCharacterPicker(
    conversation: Conversation?,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var candidates by remember { mutableStateOf<List<AgentCharacterCandidate>?>(null) }
    LaunchedEffect(Unit) {
        // 角色 = 角色库角色 + 私聊会话人设（与现有角色列表同一口径）。
        // - 只展示被活跃会话引用的角色卡，或能按名字找回私聊会话的孤儿卡（旧数据
        //   角色卡未回填引用时按名匹配补全），避免「已删除会话的角色卡」残留；
        // - 未回填角色卡的历史私聊仍按会话人设合成候选，保证名单完整。
        val library = runCatching {
            ServiceLocator.characterRepository.listCharacters()
        }.getOrDefault(emptyList())
        val libraryIds = library.map { it.id }.toSet()
        val allConversations = ServiceLocator.conversationRepository.conversations.value
        val solos = allConversations
            .filter {
                it.type == com.quiddity.app.data.model.ConversationType.SOLO &&
                    it.id != conversation?.id
            }
        val referencedIds = allConversations.mapNotNull { it.characterId }.toSet()
        val unlinkedSolos = solos.filter { it.characterId.isNullOrBlank() }
        // 孤儿卡按名字匹配未绑定会话：card id -> 匹配的会话 id
        val cardToConv = buildMap {
            library.forEach { c ->
                if (c.id in referencedIds) return@forEach
                val matched = unlinkedSolos.firstOrNull { conv -> characterNameMatches(c, conv) }
                if (matched != null) put(c.id, matched.id)
            }
        }
        candidates = buildList {
            library.forEach { c ->
                // 无内容 / 无引用且名字找不到对应会话 → 视为已删除，不展示
                if (!characterHasContent(c)) return@forEach
                if (c.id !in referencedIds && c.id !in cardToConv) return@forEach
                add(
                    AgentCharacterCandidate(
                        id = c.id,
                        name = c.persona.name.ifBlank { "未命名角色" },
                        subtitle = buildString {
                            val aiDesc = c.persona.character.ifBlank { c.persona.persona }
                            if (aiDesc.isNotBlank()) append(aiDesc)
                            val userName = c.userPersona.name
                            if (userName.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append("用户：$userName")
                            }
                            if (isEmpty()) append("点击选用")
                        },
                        avatarUri = c.aiAvatarUri ?: c.persona.aiAvatarUri,
                        library = c,
                        persona = c.persona,
                        userPersona = c.userPersona,
                        memory = c.memory,
                        selected = conversation?.characterId == c.id
                    )
                )
            }
            solos.forEach { conv ->
                // 已被角色卡按名字匹配（该卡已在角色列表中展示），避免重复
                if (cardToConv.values.contains(conv.id)) return@forEach
                if (conv.characterId != null && conv.characterId in libraryIds) return@forEach
                if (!conversationHasContent(conv)) return@forEach
                val p = conv.persona
                add(
                    AgentCharacterCandidate(
                        id = "conv:${conv.id}",
                        name = p.name.ifBlank { conv.title.ifBlank { "未命名角色" } },
                        subtitle = buildString {
                            val aiDesc = p.character.ifBlank { p.persona }
                            if (aiDesc.isNotBlank()) append(aiDesc)
                            val userName = conv.userPersona.name
                            if (userName.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append("用户：$userName")
                            }
                            if (isEmpty()) append("点击选用")
                        },
                        avatarUri = p.aiAvatarUri,
                        library = null,
                        persona = p,
                        userPersona = conv.userPersona,
                        memory = conv.memory,
                        selected = false
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // ===== 顶栏：关闭 + 标题 =====
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
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "选择角色",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        when {
            candidates == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
            candidates.orEmpty().isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "暂无可选角色\n可先到私聊里设置人设",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (conversation?.characterId != null) {
                        item(key = "clear_character") {
                            CharacterSelectRow(
                                name = "清除角色",
                                subtitle = "移除角色绑定（保留已写入的人设）",
                                avatarUri = null,
                                selected = false,
                                onClick = {
                                    viewModel.clearCharacter()
                                    onDismiss()
                                }
                            )
                        }
                    }
                    items(candidates.orEmpty(), key = { it.id }) { candidate ->
                        CharacterSelectRow(
                            name = candidate.name,
                            subtitle = candidate.subtitle,
                            avatarUri = candidate.avatarUri,
                            selected = candidate.selected,
                            onClick = {
                                val lib = candidate.library
                                if (lib != null) {
                                    viewModel.bindCharacter(lib)
                                } else {
                                    viewModel.bindPersona(
                                        persona = candidate.persona,
                                        userPersona = candidate.userPersona,
                                        memory = candidate.memory
                                    )
                                }
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 角色卡是否含人设内容（与私聊同步角色卡的口径一致，含用户人设与记忆）。 */
private fun characterHasContent(c: com.quiddity.app.data.model.Character): Boolean {
    val p = c.persona
    val u = c.userPersona
    return p.name.isNotBlank() || p.desired.isNotBlank() || p.persona.isNotBlank() ||
        p.character.isNotBlank() || p.appearance.isNotBlank() || p.worldBackground.isNotBlank() ||
        u.name.isNotBlank() || u.identity.isNotBlank() || u.gender.isNotBlank() ||
        u.age.isNotBlank() || u.appearance.isNotBlank() || c.memory.isNotBlank()
}

/** 私聊会话是否含人设内容（AI 人设 / 用户人设 / 记忆任一非空）。 */
private fun conversationHasContent(conv: Conversation): Boolean {
    val p = conv.persona
    val u = conv.userPersona
    return p.name.isNotBlank() || p.desired.isNotBlank() || p.persona.isNotBlank() ||
        p.character.isNotBlank() || p.appearance.isNotBlank() || p.worldBackground.isNotBlank() ||
        u.name.isNotBlank() || u.identity.isNotBlank() || u.gender.isNotBlank() ||
        u.age.isNotBlank() || u.appearance.isNotBlank() || conv.memory.isNotBlank()
}

/** 孤儿角色卡与私聊会话按名字匹配：角色卡名 等于 会话人设名或会话标题。 */
private fun characterNameMatches(
    c: com.quiddity.app.data.model.Character,
    conv: Conversation
): Boolean {
    val name = c.persona.name.trim()
    if (name.isBlank()) return false
    val convName = conv.persona.name.trim().ifBlank { conv.title.trim() }
    return convName.isNotBlank() && convName == name
}

/** 角色候选项：角色库角色或私聊会话合成的人设。 */
private data class AgentCharacterCandidate(
    val id: String,
    val name: String,
    val subtitle: String,
    val avatarUri: String?,
    val library: Character?,
    val persona: com.quiddity.app.data.model.Persona,
    val userPersona: com.quiddity.app.data.model.UserPersona,
    val memory: String,
    val selected: Boolean
)

/** 角色选择行：头像 + 名字 + 简介，选中打勾。 */
@Composable
private fun CharacterSelectRow(
    name: String,
    subtitle: String,
    avatarUri: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .background(
                if (selected) colorScheme.primaryContainer.copy(alpha = 0.45f)
                else colorScheme.surfaceContainerLow
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AiAvatar(
            avatarUri = avatarUri,
            name = if (selected) name else name,
            size = 44.dp
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选用",
                tint = colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
