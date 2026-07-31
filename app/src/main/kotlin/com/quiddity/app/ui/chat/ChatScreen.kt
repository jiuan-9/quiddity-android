package com.quiddity.app.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.quiddity.app.data.model.Role
import com.quiddity.app.ui.chat.components.ChatInputBar
import com.quiddity.app.ui.chat.components.CompressionProgressDialog
import com.quiddity.app.ui.chat.components.HamburgerMenu
import com.quiddity.app.ui.chat.components.MessageBubble
import com.quiddity.app.ui.chat.components.NoticeBubble
import com.quiddity.app.ui.chat.components.RewriteBottomSheet
import com.quiddity.app.ui.chat.components.TypingIndicator
import com.quiddity.app.ui.chat.gesture.ChatDragController
import com.quiddity.app.ui.chat.gesture.detectNativeHorizontalSwipe
import com.quiddity.app.ui.theme.Motion
import kotlinx.coroutines.launch

// 当前规则（重做后）：
// - 手指滑动距离 = 窗口滑动距离（1:1 跟手，无缩放、无视差、无透明度变化）
// - Animatable 驱动 graphicsLayer.translationX，draw phase 读取，零重组
// - mask/scrim 始终存在但 alpha 由 graphicsLayer 驱动（不参与组合阶段）
// - 进入/退出会话由 NavHost slideIn/slideOut(右侧) 接管
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    settingsViewModel: com.quiddity.app.ui.settings.SettingsViewModel,
    onBack: () -> Unit
) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val compressionState by viewModel.compressionState.collectAsStateWithLifecycle()
    val errorEvent by viewModel.errorEvent.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showHamburger by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    var withdrawTargetId by remember { mutableStateOf<String?>(null) }
    var rewriteTargetId by remember { mutableStateOf<String?>(null) }
    var rewritingMessageId by remember { mutableStateOf<String?>(null) }

    // ===== 多选模式状态 =====
    // multiSelectMode=true 时：顶栏切换为多选操作栏、输入栏隐藏、手势禁用、气泡显示选择圈
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // ===== 多选模式：辅助函数 =====
    fun enterMultiSelect(messageId: String) {
        multiSelectMode = true
        selectedMessageIds = setOf(messageId)
    }
    fun toggleSelection(messageId: String) {
        selectedMessageIds = if (selectedMessageIds.contains(messageId)) {
            selectedMessageIds - messageId
        } else {
            selectedMessageIds + messageId
        }
    }
    fun exitMultiSelect() {
        multiSelectMode = false
        selectedMessageIds = emptySet()
    }
    val allSelectableIds = remember(messages) {
        messages.filter { !it.isNotice }.map { it.id }.toSet()
    }
    fun toggleSelectAll() {
        selectedMessageIds = if (selectedMessageIds == allSelectableIds) emptySet() else allSelectableIds
    }
    fun copySelectedMessages() {
        if (selectedMessageIds.isEmpty()) return
        val selected = messages.filter { it.id in selectedMessageIds }
        val text = selected.joinToString("\n\n") { msg ->
            val role = if (msg.role == Role.USER) "我" else "AI"
            "$role: ${msg.content}"
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("消息", text))
        Toast.makeText(context, "已复制 ${selected.size} 条消息", Toast.LENGTH_SHORT).show()
        exitMultiSelect()
    }
    fun deleteSelectedMessages() {
        if (selectedMessageIds.isEmpty()) return
        val count = selectedMessageIds.size
        viewModel.deleteMessages(selectedMessageIds)
        Toast.makeText(context, "已删除 $count 条消息", Toast.LENGTH_SHORT).show()
        exitMultiSelect()
    }

    // ===== 键盘感知：IME 弹起时滚动到底部 =====
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottom) {
        // 当前规则：仅有 isNotice 提示气泡时不滚动（LazyColumn 未渲染）
        if (imeBottom > 0 && messages.any { !it.isNotice }) {
            listState.scrollToItem(messages.size - 1)
            kotlinx.coroutines.delay(300)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // ===== 自动滚动到底部（合并：新消息平滑滚 / 内容增长仅贴底时瞬跟） =====
    // 当前规则：lastMessageId/lastMessageContentLength 跳过 isNotice 提示气泡，避免提示气泡插入触发误滚动
    val lastMessageId by remember(messages) { derivedStateOf { messages.lastOrNull { !it.isNotice }?.id } }
    val lastMessageContentLength by remember(messages) {
        derivedStateOf { messages.lastOrNull { !it.isNotice }?.content?.length ?: 0 }
    }
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val totalItems = info.totalItemsCount
            if (totalItems == 0) return@derivedStateOf true
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= totalItems - 3
        }
    }
    var initialScrollDone by rememberSaveable { mutableStateOf(false) }
    var lastSeenMessageId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lastMessageId, lastMessageContentLength, isAtBottom) {
        // 当前规则：仅有 isNotice 提示气泡时不触发自动滚动（LazyColumn 未渲染）
        if (messages.none { !it.isNotice }) return@LaunchedEffect
        val isNewMessage = lastMessageId != lastSeenMessageId
        lastSeenMessageId = lastMessageId
        if (isNewMessage) {
            if (!initialScrollDone) {
                listState.scrollToItem(messages.size - 1)
                initialScrollDone = true
            } else {
                withFrameNanos { }
                listState.animateScrollToItem(messages.size - 1)
            }
        } else if (isAtBottom) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    // ===== 错误处理 =====
    LaunchedEffect(errorEvent) {
        if (errorEvent != null) viewModel.consumeError()
    }
    LaunchedEffect(chatError) {
        chatError?.let { error ->
            val prefix = when (error) {
                is com.quiddity.app.domain.ChatError.Network -> "网络错误"
                is com.quiddity.app.domain.ChatError.Auth -> "鉴权失败"
                is com.quiddity.app.domain.ChatError.Api -> "接口错误"
                is com.quiddity.app.domain.ChatError.Config -> "配置错误"
                is com.quiddity.app.domain.ChatError.Unknown -> "发生错误"
            }
            val suffix = when (error) {
                is com.quiddity.app.domain.ChatError.Auth -> "，请检查接口密钥"
                is com.quiddity.app.domain.ChatError.Config -> "，请在汉堡菜单的模型配置中添加"
                else -> ""
            }
            Toast.makeText(context, "$prefix：${error.userMessage}$suffix", Toast.LENGTH_LONG).show()
            viewModel.consumeChatError()
        }
    }

    // ===== 压缩结果 Toast 反馈 =====
    LaunchedEffect(compressionState) {
        when (compressionState) {
            CompressionState.Success -> {
                Toast.makeText(context, "压缩成功", Toast.LENGTH_SHORT).show()
                viewModel.consumeCompressionResult()
            }
            CompressionState.Failed -> {
                Toast.makeText(context, "压缩失败，将在下次压缩时一并处理", Toast.LENGTH_LONG).show()
                viewModel.consumeCompressionResult()
            }
            else -> Unit
        }
    }

    val wallpaperUri = conversation?.wallpaperUri
    val wallpaperDarken = conversation?.wallpaperDarken ?: 0f

    // ===== 手势配置 =====
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val isCompressing = compressionState is CompressionState.Compressing
    // swipeEnabled 不含 !showHamburger：菜单打开时手势保持 enabled，由 ChatDragController
    // 根据 menuOpen 状态区分"右滑关菜单"与"右滑返回"。否则菜单打开后无法滑动关闭，只能系统返回键（卡死根因）。
    // 多选模式下禁用横滑，避免误触退出会话。
    val swipeEnabled = !isGenerating && rewritingMessageId == null && !isCompressing && !multiSelectMode
    // rememberUpdatedState：pointerInput 用 Unit key 不重启，通过它读取最新 swipeEnabled，
    // 避免 left-swipe 过程中 showHamburger 翻转导致 pointerInput 重启、手势被打断（左滑卡死根因）。
    val swipeEnabledState = rememberUpdatedState(swipeEnabled)

    val dragController = remember(screenWidthPx) {
        ChatDragController(
            scope = scope,
            screenWidthPx = screenWidthPx,
            onBack = onBack,
            onMenuVisibilityChange = { open -> showHamburger = open }
        )
    }

    // ===== 系统返回键 =====
    // 优先级：菜单 BackHandler > 多选模式 > 改写中状态 > 返回手势动画
    BackHandler(enabled = !isGenerating && !showHamburger) {
        when {
            multiSelectMode -> exitMultiSelect()
            rewritingMessageId != null -> rewritingMessageId = null
            else -> dragController.animateBackAndExit()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ===== 整屏手势：pointerInput 用 Unit key（不随 swipeEnabled 重启），enabled 仅在 down 时刻检查一次 =====
            // 关键：左滑跟手会触发 showHamburger=true→swipeEnabled=false，若用 pointerInput(swipeEnabled) 会重启
            // 协程导致 onDragEnd 丢失、menuAlpha 卡死、遮罩卡在半透明拦截事件（左滑卡死根因）。
            // Unit key 保证手势一旦开始就完整跑完；pointerInput 在 graphicsLayer 外层，命中区域固定不随内容移动。
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
            // ===== 移动层：整个屏幕（背景+壁纸+内容）由 graphicsLayer.translationX 驱动 1:1 跟手滑出 =====
            // draw phase 读取 MutableFloatState.floatValue，零重组；松手后 NavHost popExitTransition 在更外层叠加滑出。
            .graphicsLayer {
                translationX = dragController.contentOffsetXState.floatValue
            }
            .let { mod ->
                if (wallpaperUri == null) mod.background(MaterialTheme.colorScheme.background)
                else mod
            }
    ) {
        if (wallpaperUri != null) {
            AsyncImage(
                model = wallpaperUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = wallpaperDarken))
            )
        }

        // ===== 内容层：顶栏 + 消息列表 + 输入栏（由外层 Box 的 graphicsLayer 统一驱动滑出） =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            // ===== 顶部栏（多选模式下切换为多选操作栏） =====
            if (multiSelectMode) {
                MultiSelectTopBar(
                    selectedCount = selectedMessageIds.size,
                    allSelected = selectedMessageIds == allSelectableIds && allSelectableIds.isNotEmpty(),
                    onClose = { exitMultiSelect() },
                    onSelectAll = { toggleSelectAll() },
                    onCopy = { copySelectedMessages() },
                    onDelete = { deleteSelectedMessages() }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { dragController.animateBackAndExit() },
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
                        text = conversation?.title ?: "新会话",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        textAlign = TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { dragController.toggleMenu() },
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

            // ===== 消息列表区域（手势已挪到外层 Box，整屏生效） =====
            Box(
                modifier = Modifier
                    .weight(1f)
            ) {
                when {
                    isLoading -> Unit
                    // 当前规则：仅有 isNotice 提示气泡时也视为空对话，保留"让AI先说"按钮
                    messages.none { !it.isNotice } -> Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        EmptyChatState(
                            personaName = conversation?.persona?.name.orEmpty(),
                            onLetAiStart = { viewModel.letAiStart() },
                            isGenerating = isGenerating
                        )
                        // 提示气泡显示在顶部，不遮挡居中的"让AI先说"按钮
                        Column(modifier = Modifier.fillMaxWidth()) {
                            messages.filter { it.isNotice }.forEach { msg ->
                                NoticeBubble(content = msg.content)
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                items = messages,
                                key = { it.id },
                                contentType = { if (it.isNotice) "notice" else it.role.name }
                            ) { message ->
                                // 当前规则：isLastAi 跳过 isNotice 消息，避免提示气泡遮挡末位 AI 消息的操作按钮
                                val isLastAi = message.role == Role.ASSISTANT &&
                                    messages.lastOrNull { !it.isNotice }?.id == message.id
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(
                                            placementSpec = tween(
                                                Motion.DurationShort,
                                                easing = Motion.EasingStandard
                                            ),
                                            fadeInSpec = null,
                                            fadeOutSpec = null
                                        )
                                ) {
                                    // 当前规则：isNotice 提示气泡居中渲染，不走 MessageBubble 的对话气泡逻辑
                                    if (message.isNotice) {
                                        NoticeBubble(content = message.content)
                                    } else {
                                        // 多选模式下禁用所有常规操作（撤回/改写/重说/继续说），仅保留选择切换
                                        val inMultiSelect = multiSelectMode
                                        MessageBubble(
                                            message = message,
                                            userAvatarUri = settings.userAvatarUri,
                                            aiAvatarUri = conversation?.persona?.aiAvatarUri,
                                            bracketGrayEnabled = settings.bracketGrayEnabled,
                                            typingDelayEnabled = settings.typingDelayEnabled,
                                            typingDelayMsPerChar = settings.typingDelayMsPerChar,
                                            isLastAiMessage = isLastAi,
                                            onRegenerate = if (!inMultiSelect && isLastAi && !isGenerating) {
                                                { viewModel.regenerate() }
                                            } else null,
                                            onContinue = if (!inMultiSelect && isLastAi && !isGenerating) {
                                                { viewModel.continueGeneration() }
                                            } else null,
                                            onWithdraw = if (!inMultiSelect && message.role == Role.USER && !isGenerating) {
                                                {
                                                    viewModel.withdrawMessage(message.id)
                                                    withdrawTargetId = null
                                                }
                                            } else null,
                                            isWithdrawing = withdrawTargetId == message.id,
                                            onBubbleClick = if (!inMultiSelect && message.role == Role.USER && !isGenerating) {
                                                {
                                                    withdrawTargetId =
                                                        if (withdrawTargetId == message.id) null else message.id
                                                }
                                            } else null,
                                            onLongClick = if (!inMultiSelect && !isGenerating) {
                                                { enterMultiSelect(message.id) }
                                            } else null,
                                            onRewriteTrigger = if (!inMultiSelect && isLastAi && !isGenerating) {
                                                {
                                                    rewriteTargetId =
                                                        if (rewriteTargetId == message.id) null else message.id
                                                }
                                            } else null,
                                            onRewrite = if (!inMultiSelect && isLastAi && !isGenerating && rewriteTargetId == message.id) {
                                                {
                                                    rewritingMessageId = message.id
                                                    rewriteTargetId = null
                                                }
                                            } else null,
                                            isRewriting = rewriteTargetId == message.id,
                                            multiSelectMode = inMultiSelect,
                                            isSelected = selectedMessageIds.contains(message.id),
                                            onSelectToggle = if (inMultiSelect) {
                                                { toggleSelection(message.id) }
                                            } else null
                                        )
                                    }
                                }
                            }

                            val lastMsg = messages.lastOrNull()
                            val showThinking = isGenerating &&
                                (lastMsg == null || !(lastMsg.role == Role.ASSISTANT && lastMsg.isStreaming))
                            if (showThinking) {
                                item(key = "thinking_bubble", contentType = { "thinking" }) {
                                    ThinkingBubble(aiAvatarUri = conversation?.persona?.aiAvatarUri)
                                }
                            }
                        }
                    }
                }
            }

            // 输入栏（多选模式下隐藏）
            if (!multiSelectMode) {
                ChatInputBar(
                    enterToSend = settings.enterToSend,
                    isGenerating = isGenerating,
                    onSend = { text -> viewModel.sendMessage(text) },
                    onStop = { viewModel.stopGeneration() },
                    enabled = !showHamburger,
                    transparent = wallpaperUri != null,
                    onTextChange = { text -> viewModel.updateInputText(text) },
                    isCompressing = isCompressing
                )
            }
        }

        // 遮罩由 HamburgerMenu 内部自己管理（alpha 跟菜单同步，0.4s 淡入淡出），
        // 这里不再单独放一个 Box 避免跟消息列表抢事件。
    }

    // ===== 汉堡菜单 =====
    HamburgerMenu(
        visible = showHamburger,
        menuAlphaState = dragController.menuAlphaState,
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        onDismiss = { dragController.closeMenu() }
    )

    // ===== 压缩进度弹窗 =====
    CompressionProgressDialog(visible = isCompressing)

    // ===== 消息改写底部弹出框 =====
    rewritingMessageId?.let { msgId ->
        val targetMsg = messages.firstOrNull { it.id == msgId }
        if (targetMsg != null) {
            RewriteBottomSheet(
                initialText = targetMsg.content,
                onSave = { newContent ->
                    viewModel.rewriteMessage(msgId, newContent)
                    rewritingMessageId = null
                },
                onDismiss = { rewritingMessageId = null }
            )
        } else {
            rewritingMessageId = null
        }
    }
}

// ===== 三条开发规范（位于文件中间位置） =====
// 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
//    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
// 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
//    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
// 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
//    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

@Composable
private fun EmptyChatState(
    personaName: String,
    onLetAiStart: () -> Unit,
    isGenerating: Boolean
) {
    val aiName = personaName.ifBlank { "AI" }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLetAiStart
                ),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Text(
                text = if (isGenerating) "$aiName 正在说话..." else "让 $aiName 先发消息",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun ThinkingBubble(aiAvatarUri: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            if (aiAvatarUri != null) {
                AsyncImage(
                    model = aiAvatarUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(8.dp))
        Surface(
            modifier = Modifier.widthIn(max = 360.dp),
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypingIndicator()
            }
        }
    }
}

/**
 * 多选模式顶部操作栏。
 *
 * 布局：关闭按钮 | 已选 N 项 | 全选 + 复制 + 删除
 * - 关闭按钮退出多选模式（清空选择）
 * - "全选"图标在已全选时切换为取消全选
 * - 复制/删除在选中数为 0 时仍可点击但无操作（由调用方判断）
 */
@Composable
private fun MultiSelectTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
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
            text = "已选 $selectedCount 项",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSelectAll
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                contentDescription = if (allSelected) "取消全选" else "全选",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCopy
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "复制",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDelete
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
