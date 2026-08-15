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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
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
import androidx.compose.runtime.saveable.Saver
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
import com.quiddity.app.ui.chat.ToolTrace
import com.quiddity.app.ui.chat.OcrState
import com.quiddity.app.ui.chat.components.ChatInputBar
import com.quiddity.app.ui.chat.components.HamburgerMenu
import com.quiddity.app.ui.chat.components.RewriteBottomSheet
import com.quiddity.app.ui.chat.components.ReeditNoticeBubble
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
    val toolTraces by viewModel.toolTraces.collectAsStateWithLifecycle()
    val pendingConfirm by viewModel.pendingToolConfirm.collectAsStateWithLifecycle()
    val pendingWithdraw by viewModel.pendingWithdraw.collectAsStateWithLifecycle()
    val pendingImageUri by viewModel.pendingImageUri.collectAsStateWithLifecycle()
    val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()
    val pendingReedit by viewModel.pendingReedit.collectAsStateWithLifecycle()
    val activeSegmentEnds by viewModel.activeSegmentEnds.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showHamburger by rememberSaveable { mutableStateOf(false) }
    var showCharacterPicker by rememberSaveable { mutableStateOf(false) }
    var characterPickerClosing by rememberSaveable { mutableStateOf(false) }
    // AI 消息改写（改写框弹出中）与撤回后「重新编辑」（编辑框弹出中）
    var rewritingMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    var reeditSheetOpen by rememberSaveable { mutableStateOf(false) }
    // ===== 多选消息 =====
    var multiSelectMode by rememberSaveable { mutableStateOf(false) }
    val selectedIdsSaver = remember {
        Saver<Set<String>, Any>(
            save = { it.toList() },
            restore = { (it as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet() }
        )
    }
    var selectedMessageIds by rememberSaveable(stateSaver = selectedIdsSaver) {
        mutableStateOf(emptySet<String>())
    }
    fun enterMultiSelect(messageId: String) {
        multiSelectMode = true
        selectedMessageIds = setOf(messageId)
    }
    fun toggleSelection(messageId: String) {
        selectedMessageIds = if (messageId in selectedMessageIds) {
            selectedMessageIds - messageId
        } else {
            selectedMessageIds + messageId
        }
    }
    fun exitMultiSelect() {
        multiSelectMode = false
        selectedMessageIds = emptySet()
    }
    fun selectAllMessages() {
        val all = messages.filterNot { it.isNotice }.map { it.id }.toSet()
        selectedMessageIds = if (selectedMessageIds == all) emptySet() else all
    }
    fun deleteSelectedMessages() {
        if (selectedMessageIds.isEmpty()) return
        viewModel.deleteMessages(selectedMessageIds)
        exitMultiSelect()
    }
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
    // 多选 / 改写或重新编辑框打开时禁用整屏滑动，避免误触菜单或拖屏；
    // 生成/加载中仍允许左滑打开汉堡菜单；右滑返回由 backGestureEnabledState 单独禁用。
    // 无障碍模拟手势注入期间同样禁用，避免 AI 操作屏幕时"划退"退出会话
    val swipeEnabled = !multiSelectMode && rewritingMessageId == null && !reeditSheetOpen &&
        !com.quiddity.app.active.ScreenReaderService.injecting
    val swipeEnabledState = rememberUpdatedState(swipeEnabled)
    // 生成中禁用右滑返回（防止误触退出会话），左滑菜单不受影响
    val backGestureEnabledState = rememberUpdatedState(!isGenerating)
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
            // LENGTH_LONG：接口报错原文（如 HTTP 400 的服务端响应体）需要时间阅读
            Toast.makeText(context, it.userMessage, Toast.LENGTH_LONG).show()
            viewModel.consumeChatError()
        }
    }

    // ===== 无障碍服务断开自检：开启过「模拟点击」但服务被系统自动关闭时，
    // 提示一键跳转系统无障碍设置页重开（系统超时/省电机制会自动停用服务） =====
    var accessibilityWarning by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                accessibilityWarning =
                    conversation?.type == com.quiddity.app.data.model.ConversationType.AGENT &&
                        ServiceLocator.agentStore?.snapshot()?.toolSwitches?.simulate_click == true &&
                        !com.quiddity.app.active.ScreenReaderService.isServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 新消息/内容增长时贴底（reverseLayout：index 0 = 最新）
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.any { !it.isNotice }) listState.animateScrollToItem(0)
    }

    // ===== 系统返回键：角色面板 -> 关闭面板；改写/重新编辑框 -> 关框；正常状态 -> 退出会话 =====
    // 合并为单一 BackHandler 状态机，避免多 handler 注册顺序带来的返回误判。
    BackHandler(
        enabled = multiSelectMode || showCharacterPicker || characterPickerClosing ||
            (!isGenerating && !showHamburger)
    ) {
        when {
            multiSelectMode -> exitMultiSelect()
            showCharacterPicker || characterPickerClosing -> closeCharacterPicker()
            rewritingMessageId != null -> rewritingMessageId = null
            reeditSheetOpen -> reeditSheetOpen = false
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
                    onDrag = { totalDx, _ ->
                        if (totalDx <= 0f || backGestureEnabledState.value) {
                            dragController.onDrag(totalDx)
                        }
                    },
                    onDragEnd = { totalDx, velocityDx ->
                        if (totalDx <= 0f || backGestureEnabledState.value) {
                            dragController.onDragEnd(totalDx, velocityDx)
                        }
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
            // ===== 顶栏：多选时显示多选操作栏，否则返回 + 标题 + 汉堡（会话设置） =====
            if (multiSelectMode) {
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
                            .clickable { exitMultiSelect() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "退出多选",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "已选 ${selectedMessageIds.size} 条",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { selectAllMessages() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SelectAll,
                            contentDescription = "全选",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { deleteSelectedMessages() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
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
            }

            // ===== 无障碍服务被系统关闭提示条：点击一键跳转设置重开 =====
            if (accessibilityWarning) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
                                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        ),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "无障碍服务已被系统关闭，AI 无法操作屏幕",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "去开启",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
                    val displayMessages = remember(messages) { messages.reversed() }
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
                        // 撤回后的「重新编辑」提示：reverseLayout 的第一项 = 最底部，
                        // 显示在最后一条消息之下（撤回仅限最后一条用户消息，与流式互斥）
                        pendingReedit?.let {
                            item(key = "agent_reedit_notice", contentType = { "reedit" }) {
                                ReeditNoticeBubble(
                                    onReedit = { reeditSheetOpen = true },
                                    onDismiss = { viewModel.clearPendingReedit() }
                                )
                            }
                        }
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
                                        if (conversation?.thinkingEnabled == true) {
                                            com.quiddity.app.ui.components.ShimmerHighlightText(
                                                text = "思考中…",
                                                icon = Icons.Filled.AutoAwesome
                                            )
                                        } else {
                                            TypingIndicator()
                                        }
                                    }
                                }
                            }
                        }
                        // 工具痕迹：内联到最新 AI 消息正文末尾（生成过程中显示，结束随痕迹清空消失）；
                        // 尚无正文消息可挂载时（纯工具阶段），退化为列表底部占位显示
                        val latestAi = messages.lastOrNull {
                            !it.isNotice && it.role == Role.ASSISTANT && !it.isThinking
                        }
                        if (toolTraces.isNotEmpty() && latestAi == null) {
                            item(key = "agent_tool_trace_fallback", contentType = { "tool_trace" }) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
                                            )
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    // 方案 B' 进度提示：复杂任务（累计 4 次以上工具操作）时
                                    // 显示「已执行 N 次工具操作」，让用户感知整体进度（0~3 次不显示）
                                    if (toolTraces.size >= 4) {
                                        Text(
                                            text = "已执行 ${toolTraces.size} 次工具操作",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                    toolTraces.forEach { trace ->
                                        ToolTraceLine(UiTrace(trace.name, trace.status, trace.summary))
                                    }
                                }
                            }
                        }
                        items(displayMessages, key = { it.id }) { message ->
                            // 撤回仅限最后一条用户消息（与私聊「召回最后一句」语义一致）
                            val lastUserMsgId = messages.lastOrNull {
                                !it.isNotice && it.role == Role.USER
                            }?.id
                            // 段边界：生成中最新消息用实时内存边界（工具痕迹流式中穿插在正文段间），
                            // 历史消息/生成结束用消息持久化边界
                            val effectiveSegmentEnds =
                                if (message.id == latestAi?.id && activeSegmentEnds.isNotEmpty()) {
                                    activeSegmentEnds
                                } else {
                                    message.toolSegmentEnds
                                }
                            AgentMessageLine(
                                message = message,
                                markdownEnabled = settings.markdownEnabled,
                                userAvatarUri = settings.userAvatarUri,
                                aiAvatarUri = conversation?.persona?.aiAvatarUri,
                                aiName = conversation?.let {
                                    it.persona.name.ifBlank { it.title }
                                }.orEmpty(),
                                animateEntry = message.timestamp >= openedAtMs,
                                isLatestAi = message.id == latestAi?.id,
                                segmentEnds = effectiveSegmentEnds,
                                // 工具痕迹只内联到最新 AI 消息（生成中跟随正文流）
                                toolTraces = if (message.id == latestAi?.id) toolTraces else emptyList(),
                                inMultiSelect = multiSelectMode,
                                isSelected = selectedMessageIds.contains(message.id),
                                onEnterMultiSelect = { enterMultiSelect(message.id) },
                                onToggleSelect = { toggleSelection(message.id) },
                                onCopy = { text -> copyToClipboard(context, text) },
                                onRegenerate = { viewModel.regenerate() },
                                onContinue = { viewModel.continueGeneration() },
                                onRewrite = { rewritingMessageId = message.id },
                                onWithdraw = if (message.id == lastUserMsgId) {
                                    { viewModel.requestAgentWithdraw(message.id) }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        // ===== 输入栏（复用 Solo 输入条；多选时隐藏） =====
        if (!multiSelectMode) {
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
    AgentChatDialogs(
        showCharacterPicker = showCharacterPicker,
        conversation = conversation,
        messages = messages,
        pendingConfirm = pendingConfirm,
        pendingWithdraw = pendingWithdraw,
        rewritingMessageId = rewritingMessageId,
        pendingReedit = pendingReedit,
        reeditSheetOpen = reeditSheetOpen,
        viewModel = viewModel,
        onCloseCharacterPicker = { closeCharacterPicker() },
        onDismissRewrite = { rewritingMessageId = null },
        onDismissReeditSheet = { reeditSheetOpen = false }
    )

}
