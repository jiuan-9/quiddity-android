package com.quiddity.app.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.view.ViewTreeObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.model.UserPersona
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.domain.ChatRecordSearch
import com.quiddity.app.ui.chat.components.ChatInputBar
import com.quiddity.app.ui.chat.components.CompressionProgressDialog
import com.quiddity.app.ui.chat.components.GroupAvatarBar
import com.quiddity.app.ui.chat.components.GameLogBubble
import com.quiddity.app.ui.chat.components.HamburgerMenu
import com.quiddity.app.ui.chat.components.MessageBubble
import com.quiddity.app.ui.chat.components.NoticeBubble
import com.quiddity.app.ui.chat.components.MiniAppInviteCard
import com.quiddity.app.ui.chat.components.RewriteBottomSheet
import com.quiddity.app.ui.chat.components.TypingIndicator
import com.quiddity.app.ui.chat.gesture.ChatDragController
import com.quiddity.app.ui.chat.gesture.detectNativeHorizontalSwipe
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.ChatImageExporter
import com.quiddity.app.util.DateUtils
import com.quiddity.app.util.ImageUtils
import com.quiddity.app.util.QuiddityConstants
import com.quiddity.app.util.WallpaperContrast
import kotlin.math.roundToInt
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


// 当前规则（重做后）：
// - 手指滑动距离 = 窗口滑动距离（1:1 跟手，无缩放、无视差、无透明度变化）
// - Animatable 驱动 graphicsLayer.translationX，draw phase 读取，零重组
// - mask/scrim 始终存在但 alpha 由 graphicsLayer 驱动（不参与组合阶段）
// - 进入/退出会话由 NavHost slideIn/slideOut(右侧) 接管
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    settingsViewModel: com.quiddity.app.ui.settings.SettingsViewModel,
    initialMessageId: String? = null,
    onBack: () -> Unit,
    onConversationExit: () -> Unit = {},
    onOpenMiniApp: (String) -> Unit = {}
) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val compressionState by viewModel.compressionState.collectAsStateWithLifecycle()
    val errorEvent by viewModel.errorEvent.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()
    val timeLibraryHint by viewModel.timeLibraryHint.collectAsStateWithLifecycle()
    val groupQueue by viewModel.groupQueue.collectAsStateWithLifecycle()
    val senderNameMap by viewModel.senderNameMap.collectAsStateWithLifecycle()
    val senderAvatarMap by viewModel.senderAvatarMap.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val pendingImageUri by viewModel.pendingImageUri.collectAsStateWithLifecycle()
    val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ===== 图片发送：选择图片 → 复制到内部存储（规避临时授权丢失） → 挂载待发送 =====
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
    // 待发送图片清除（发送成功 / 用户移除）后回收内部临时文件
    var lastAttachedImageUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingImageUri) {
        val previous = lastAttachedImageUri
        lastAttachedImageUri = pendingImageUri
        if (pendingImageUri == null && previous != null) {
            ImageUtils.deleteTempFile(Uri.parse(previous))
        }
    }

    var showHamburger by rememberSaveable { mutableStateOf(false) }
    // 打开会话内设置（汉堡菜单）等覆盖层时立即收起输入法，焦点已不在输入框
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(showHamburger) {
        if (showHamburger) keyboardController?.hide()
    }
    val listState = rememberLazyListState()
    // 会话打开时刻：只有此后新到达的消息播放入场动画（历史消息滚动回来不重放）
    val openedAtMs = rememberSaveable { System.currentTimeMillis() }

    // ===== 群聊信息（方案十二：消息按发送者显示头像与名字） =====
    val isGroupChat = conversation?.type == ConversationType.GROUP
    val groupMembers = remember(conversation?.id, conversation?.memberConversationIds) {
        viewModel.groupMembers()
    }

    // ===== 场景/世界提示气泡（派生自会话数据，长存且随设置实时更新） =====
    // - 内容 = 世界类型（仅当世界背景前4字为"xx世界"时）+ 当前场景；
    // - 用户未在世界背景前写"xx世界"时只显示场景；
    // - 不落库为消息：删除聊天记录、清空消息都不会影响它。
    val sceneNoticeContent = remember(
        conversation?.scene,
        conversation?.persona?.worldBackground
    ) {
        val scene = conversation?.scene?.trim().orEmpty()
        val world = conversation?.persona?.worldBackground?.trim().orEmpty()
        val worldType = if (world.length >= 4 && world.take(4).endsWith("世界")) {
            world.take(4)
        } else {
            ""
        }
        when {
            worldType.isNotBlank() && scene.isNotBlank() -> "$worldType · $scene"
            worldType.isNotBlank() -> worldType
            scene.isNotBlank() -> scene
            else -> ""
        }
    }

    // ===== 私聊用户名强制（方案九.4/6：未设置用户名不能发送，进入会话先弹窗） =====
    var showUserNameDialog by rememberSaveable { mutableStateOf(false) }
    var userNameInput by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(conversation?.id) {
        val conv = conversation
        if (conv != null && conv.type != ConversationType.GROUP && conv.userPersona.name.isBlank()) {
            userNameInput = ""
            showUserNameDialog = true
        }
    }

    // 当前展开操作项的消息 id（用户消息=撤回；AI 消息=重说/继续说/改写/删除面板）。
    // 全局只保留一个展开项，点击其他气泡自动切换。
    var expandedActionId by rememberSaveable { mutableStateOf<String?>(null) }
    var rewritingMessageId by rememberSaveable { mutableStateOf<String?>(null) }

    // ===== 多选模式状态 =====
    // multiSelectMode=true 时：顶栏切换为多选操作栏、输入栏隐藏、手势禁用、气泡显示选择圈
    var multiSelectMode by rememberSaveable { mutableStateOf(false) }
    val selectedIdsSaver = remember {
        androidx.compose.runtime.saveable.Saver<Set<String>, String>(
            save = { ids -> ids.sorted().joinToString(",") },
            restore = { saved -> saved.split(",").filter { it.isNotEmpty() }.toSet() }
        )
    }
    var selectedMessageIds by rememberSaveable(stateSaver = selectedIdsSaver) {
        mutableStateOf<Set<String>>(emptySet())
    }
    // 查找聊天记录跳转高亮：记录要定位的消息 id，滚动过去并短暂高亮
    var highlightMessageId by remember { mutableStateOf<String?>(null) }

    // ===== 会话内搜索 =====
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // 待跳转定位的消息 id（来自搜索点击或外部跳转）；由自动滚动协程消费
    var pendingJumpMessageId by remember { mutableStateOf(initialMessageId) }

    // 退出组合（返回主页/滑出会话）时通知宿主：无未完结任务立即释放，有任务等完结再释放
    DisposableEffect(Unit) {
        onDispose { onConversationExit() }
    }

    LaunchedEffect(highlightMessageId) {
        if (highlightMessageId != null) {
            kotlinx.coroutines.delay(2_500)
            highlightMessageId = null
        }
    }

    // 展开操作项/面板时，把对应消息滚到可见位置，避免被输入法键盘或输入栏遮住
    LaunchedEffect(expandedActionId) {
        val id = expandedActionId ?: return@LaunchedEffect
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            // reverseLayout：原始 index 需映射到反转列表的 index
            listState.animateScrollToItem(messages.size - 1 - index)
        }
    }

    // ===== 多选模式：辅助函数 =====
    fun enterMultiSelect(messageId: String) {
        multiSelectMode = true
        selectedMessageIds = setOf(messageId)
        expandedActionId = null
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
            val role = when {
                isGroupChat && msg.role == Role.USER -> "我"
                isGroupChat -> msg.senderId?.let {
                    senderNameMap[it]?.takeIf { name -> name.isNotBlank() }
                } ?: "未知成员"
                msg.role == Role.USER -> "我"
                else -> "AI"
            }
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

    // ===== 键盘感知：窗口可见区域测量键盘高度 + 消息列表/输入栏平滑跟随 =====
    // edge-to-edge 下窗口不被系统压缩，键盘高度用窗口可见区域测量（不依赖 IME insets 派发）。
    // 键盘弹起时消息列表与输入栏从原位平滑升到键盘上方，收起时降回；顶栏与背景不参与偏移。
    val composeView = LocalView.current
    val imeHeight = remember { mutableFloatStateOf(0f) }
    val keyboardOffset = remember { Animatable(0f) }
    var lastImeHeight by remember { mutableFloatStateOf(0f) }
    DisposableEffect(composeView) {
        val frame = Rect()
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            composeView.getWindowVisibleDisplayFrame(frame)
            val fullHeight = composeView.rootView.height
            val frameKeyboardHeight = (fullHeight - frame.bottom).coerceAtLeast(0)
            val insetKeyboardHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                composeView.rootWindowInsets
                    ?.getInsets(android.view.WindowInsets.Type.ime())
                    ?.bottom ?: 0
            } else {
                0
            }
            val keyboardHeight = maxOf(frameKeyboardHeight, insetKeyboardHeight)
            imeHeight.floatValue = keyboardHeight.toFloat()
        }
        composeView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            composeView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
    val density = LocalDensity.current
    val imeBottom = imeHeight.floatValue
    LaunchedEffect(imeHeight.floatValue) {
        val current = imeHeight.floatValue
        if (current == lastImeHeight) return@LaunchedEffect
        val prev = lastImeHeight
        lastImeHeight = current
        if (current > prev) {
            keyboardOffset.animateTo(
                targetValue = -current,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
            )
        } else {
            keyboardOffset.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
            )
        }
    }
    // 仅在 IME 由隐藏变为可见时触发一次，避免 IME 收起动画期间反复把列表拉回底部，
    // 覆盖搜索结果的跳转定位（“还没划上去就又回到最底下”的根因）。
    var wasImeVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeBottom) {
        val opened = imeBottom > 0 && !wasImeVisible
        wasImeVisible = imeBottom > 0
        // 当前规则：仅有 isNotice 提示气泡时不滚动（LazyColumn 未渲染）
        if (opened && messages.any { !it.isNotice }) {
            listState.scrollToItem(0)
            kotlinx.coroutines.delay(300)
            listState.animateScrollToItem(0)
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
            // reverseLayout：底部（最新一条）对应 index 0，贴底 = 可见项包含 0/1
            info.visibleItemsInfo.any { it.index <= 1 }
        }
    }
    var initialScrollDone by rememberSaveable { mutableStateOf(false) }
    var lastSeenMessageId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lastMessageId, lastMessageContentLength, isAtBottom, pendingJumpMessageId) {
        // 当前规则：仅有 isNotice 提示气泡时不触发自动滚动（LazyColumn 未渲染）
        if (messages.none { !it.isNotice }) return@LaunchedEffect
        // 跳转定位优先：来自搜索点击或外部跳转（首页消息结果）的目标消息
        val jumpId = pendingJumpMessageId
        if (jumpId != null) {
            pendingJumpMessageId = null
            lastSeenMessageId = lastMessageId
            initialScrollDone = true
            val index = messages.indexOfFirst { it.id == jumpId }
            if (index >= 0) {
                highlightMessageId = jumpId
                withFrameNanos { }
                // reverseLayout：映射到反转列表的 index
                listState.scrollToItem(messages.size - 1 - index)
            }
            return@LaunchedEffect
        }
        val isNewMessage = lastMessageId != lastSeenMessageId
        lastSeenMessageId = lastMessageId
        if (isNewMessage) {
            if (!initialScrollDone) {
                listState.scrollToItem(0)
                initialScrollDone = true
            } else {
                withFrameNanos { }
                listState.animateScrollToItem(0)
            }
        } else if (isAtBottom) {
            listState.scrollToItem(0)
        }
    }

    // ===== 错误处理 =====
    LaunchedEffect(errorEvent) {
        // errorEvent 兼作信息提示通道（思考降级 / 未返回思考内容等）
        errorEvent?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.consumeError()
        }
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

    // ===== 主动消息：时间库整理提示（对应算法文档 3.1） =====
    LaunchedEffect(timeLibraryHint) {
        timeLibraryHint?.let { hint ->
            Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
            viewModel.consumeTimeLibraryHint()
        }
    }

    // ===== 主动消息：每天首次打开该会话时触发生成 =====
    LaunchedEffect(conversation?.id) {
        if (conversation != null) {
            viewModel.ensureTimeLibraryGenerated()
        }
    }

    val wallpaperUri = conversation?.wallpaperUri
    val wallpaperDarken = conversation?.wallpaperDarken ?: 0f
    // ===== 壁纸自动对比度 =====
    // 采样壁纸亮度（Coil 小尺寸解码，64px 足够判定明暗），
    // 自动叠加保证文字可读的遮罩基线；只调背景遮罩，不触碰任何文字渲染。
    val imageLoader = LocalContext.current.imageLoader
    var wallpaperBrightness by remember(wallpaperUri) {
        mutableFloatStateOf(WallpaperContrast.DEFAULT_BRIGHTNESS)
    }
    LaunchedEffect(wallpaperUri) {
        if (wallpaperUri == null) {
            wallpaperBrightness = WallpaperContrast.DEFAULT_BRIGHTNESS
            return@LaunchedEffect
        }
        val brightness = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(wallpaperUri)
                    .size(64)
                    .allowHardware(false)
                    .build()
                val drawable = imageLoader.execute(request).drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    ?: return@runCatching WallpaperContrast.DEFAULT_BRIGHTNESS
                WallpaperContrast.sampleBrightness(bitmap)
            }.getOrDefault(WallpaperContrast.DEFAULT_BRIGHTNESS)
        }
        wallpaperBrightness = brightness
    }
    val wallpaperScrim = remember(wallpaperBrightness, wallpaperDarken, settings.darkMode) {
        val alpha = WallpaperContrast.effectiveScrimAlpha(
            wallpaperBrightness,
            wallpaperDarken,
            settings.darkMode
        )
        if (settings.darkMode) Color.Black.copy(alpha = alpha)
        else Color.White.copy(alpha = alpha)
    }
    val colorScheme = MaterialTheme.colorScheme
    // 导出长图样式：跟随当前主题（浅色/深色），背景与气泡配色与聊天界面一致
    val exportStyle = remember(colorScheme) {
        ChatImageExporter.ExportStyle(
            background = colorScheme.background.toArgbInt(),
            headerTitle = colorScheme.onSurface.toArgbInt(),
            headerSub = colorScheme.onSurfaceVariant.toArgbInt(),
            headerDivider = colorScheme.outlineVariant.toArgbInt(),
            aiBubble = colorScheme.surfaceVariant.toArgbInt(),
            aiText = colorScheme.onSurfaceVariant.toArgbInt(),
            aiAvatar = colorScheme.surfaceVariant.toArgbInt(),
            userBubble = colorScheme.secondary.toArgbInt(),
            userText = colorScheme.onSecondary.toArgbInt(),
            time = colorScheme.onSurfaceVariant.copy(alpha = 0.5f).toArgbInt(),
            footer = colorScheme.onSurfaceVariant.toArgbInt()
        )
    }

    // ===== 手势配置 =====
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    // ===== 多选导出长图 =====
    var exportingImage by remember { mutableStateOf(false) }
    // Android 8/9 保存到相册需要存储权限：授权后通过标志位重试导出
    var retryExportAfterPermission by remember { mutableStateOf(false) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            retryExportAfterPermission = true
        } else {
            Toast.makeText(context, "需要存储权限才能保存到相册", Toast.LENGTH_LONG).show()
        }
    }
    fun exportSelectedAsImage() {
        if (selectedMessageIds.isEmpty() || exportingImage) return
        val conv = conversation ?: return
        val selected = messages.filter { it.id in selectedMessageIds }
        val exportSenderNames = if (isGroupChat) {
            selected.mapNotNull { it.senderId }
                .distinct()
                .associateWith { id ->
                    senderNameMap[id]?.takeIf { name -> name.isNotBlank() } ?: "未知成员"
                }
        } else {
            emptyMap()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        exportingImage = true
        scope.launch {
            try {
                val file = ChatImageExporter.exportToFile(
                    context = context,
                    messages = selected,
                    aiName = conv.persona.name,
                    conversationTitle = conv.title,
                    style = exportStyle,
                    wallpaperUri = conv.wallpaperUri,
                    wallpaperDarken = conv.wallpaperDarken,
                    senderNames = exportSenderNames
                )
                val galleryUri = ChatImageExporter.saveToGallery(context, file)
                val shared = ChatImageExporter.share(context, file)
                when {
                    galleryUri != null && shared ->
                        Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                    galleryUri != null ->
                        Toast.makeText(context, "已保存到相册（无法打开分享面板）", Toast.LENGTH_LONG).show()
                    shared ->
                        Toast.makeText(context, "长图已生成，但保存相册失败", Toast.LENGTH_LONG).show()
                    else ->
                        Toast.makeText(context, "无法打开分享面板", Toast.LENGTH_LONG).show()
                }
            } catch (e: Throwable) {
                Toast.makeText(context, "生成长图失败：${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                exportingImage = false
                exitMultiSelect()
            }
        }
    }
    LaunchedEffect(retryExportAfterPermission) {
        if (retryExportAfterPermission) {
            retryExportAfterPermission = false
            exportSelectedAsImage()
        }
    }

    val isCompressing = compressionState is CompressionState.Compressing
    // swipeEnabled 不含 !showHamburger：菜单打开时手势保持 enabled，由 ChatDragController
    // 根据 menuOpen 状态区分"右滑关菜单"与"右滑返回"。否则菜单打开后无法滑动关闭，只能系统返回键（卡死根因）。
    // 多选模式下禁用横滑，避免误触退出会话。
    val swipeEnabled = !isGenerating && rewritingMessageId == null &&
        !isCompressing && !multiSelectMode && !searchActive
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
            searchActive -> {
                searchActive = false
                searchQuery = ""
            }
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
                // API 31+ 对壁纸做轻模糊：透过半透明气泡/面板看到磨砂壁纸（毛玻璃质感）；
                // 低版本无 RenderEffect，自动降级为纯半透明，不影响功能。
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(3.dp)
                        } else {
                            Modifier
                        }
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(wallpaperScrim)
            )
        }

        // ===== 内容层：顶栏 + 消息列表 + 输入栏（由外层 Box 的 graphicsLayer 统一驱动滑出） =====
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // ===== 顶部栏（多选模式下切换为多选操作栏） =====
            if (multiSelectMode) {
                MultiSelectTopBar(
                    selectedCount = selectedMessageIds.size,
                    allSelected = selectedMessageIds == allSelectableIds && allSelectableIds.isNotEmpty(),
                    onClose = { exitMultiSelect() },
                    onSelectAll = { toggleSelectAll() }
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
                        text = conversation?.persona?.name?.takeIf { it.isNotBlank() }
                            ?: conversation?.title
                            ?: "新会话",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        textAlign = TextAlign.Center
                    )
                    // 头部不显示放大镜（私聊/群聊均无），查找聊天记录入口统一在会话设置内
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

            // ===== 会话内搜索条（顶栏搜索图标展开） =====
            if (searchActive) {
                ChatSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        searchActive = false
                        searchQuery = ""
                    }
                )
            }

            // ===== 群聊 0 成员横幅（方案十.7：不能点名回复，点击跳成员管理） =====
            if (isGroupChat && groupMembers.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showHamburger = true }
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "请添加成员",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // ===== 消息列表区域（手势已挪到外层 Box，整屏生效） =====
            Box(
                modifier = Modifier
                    .weight(1f)
                    // 键盘弹起时列表区域收缩（底部让位给键盘），气泡不进入顶栏、不与输入栏脱节
                    .padding(bottom = with(density) { (-keyboardOffset.value).coerceAtLeast(0f).toDp() })
            ) {
                when {
                    searchActive && searchQuery.isNotBlank() -> {
                        val searchResults = remember(messages, searchQuery) {
                            val q = searchQuery.trim()
                            if (q.isEmpty()) emptyList()
                            else ChatRecordSearch.searchResults(
                                messages.filterNot { it.isNotice },
                                q
                            )
                        }
                        if (searchResults.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "未找到与“${searchQuery}”相关的消息",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(searchResults, key = { it.id }) { msg ->
                                    ChatSearchResultRow(
                                        message = msg,
                                        query = searchQuery,
                                        onClick = {
                                            searchActive = false
                                            searchQuery = ""
                                            pendingJumpMessageId = msg.id
                                        }
                                    )
                                }
                            }
                        }
                    }
                    isLoading -> Unit
                    // 当前规则：仅有 isNotice 提示气泡时也视为空对话，保留"让AI先说"按钮
                    messages.none { !it.isNotice } -> Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isGroupChat) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无消息\n发送后点击下方成员头像，让 TA 回复",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            EmptyChatState(
                                personaName = conversation?.persona?.name.orEmpty(),
                                onLetAiStart = { viewModel.letAiStart() },
                                isGenerating = isGenerating
                            )
                        }
                        // 场景/世界提示气泡显示在顶部，不遮挡居中的"让AI先说"按钮
                        Column(modifier = Modifier.fillMaxWidth()) {
                            messages.filter { it.isNotice && !it.miniAppId.isNullOrBlank() }
                                .forEach { invite ->
                                    MiniAppInviteCard(
                                        title = invite.miniAppTitle?.takeIf { it.isNotBlank() }
                                            ?: "小应用",
                                        content = invite.content,
                                        onClick = { invite.miniAppId?.let(onOpenMiniApp) }
                                    )
                                }
                            if (sceneNoticeContent.isNotBlank()) {
                                NoticeBubble(content = sceneNoticeContent)
                            }
                            if (!isGroupChat && isGenerating) {
                                ThinkingBubble(aiAvatarUri = conversation?.persona?.aiAvatarUri)
                            }
                        }
                    }
                    else -> {
                        val lastMsg = messages.lastOrNull()
                        // 当前会话是否启用思考（内部思考任意模型可用，思考期间动画气泡显示"思考中"）
                        val thinkingActive = conversation?.thinkingEnabled == true
                        // 群聊用头像栏三点表示正在回复，不显示私聊的思考气泡
                        val showThinking = !isGroupChat && isGenerating &&
                            (lastMsg == null || !(lastMsg.role == Role.ASSISTANT && lastMsg.isStreaming))
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            // 消息从底部排列：最新一条贴近输入栏，消息少时气泡不挤在顶部
                            reverseLayout = true,
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 思考气泡：reverseLayout 的第一项 = 最底部，紧贴输入栏（标准"正在输入"位置）
                            if (!isGroupChat) {
                                item(key = "thinking_bubble", contentType = { "thinking" }) {
                                    // 常驻 item + AnimatedVisibility：出现淡入、消失淡出，不再硬插硬删
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        AnimatedVisibility(
                                            visible = showThinking,
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
                                            ThinkingBubble(
                                                aiAvatarUri = conversation?.persona?.aiAvatarUri,
                                                thinkingLabel = if (thinkingActive) "思考中" else null
                                            )
                                        }
                                    }
                                }
                            }

                            items(
                                items = messages.asReversed().filterNot { it.isNotice },
                                key = { it.id },
                                contentType = { it.role.name }
                            ) { message ->
                                // 关键性能优化：key(message.id) + 独立 composable 让 ChatScreen 重组时
                                // message 内容未变的气泡完全跳过重组（流式每个 token 触发 messages 变化，
                                // 原实现会让所有气泡都重组，因为 lambda 参数每帧都是新实例）
                                val rowSelected = selectedMessageIds.contains(message.id)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (multiSelectMode && !message.isNotice && !message.isGameLog) {
                                                // 多选：点击整行勾选，选中行整行高亮
                                                Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (rowSelected) {
                                                            MaterialTheme.colorScheme.primaryContainer
                                                                .copy(alpha = 0.35f)
                                                        } else {
                                                            Color.Transparent
                                                        }
                                                    )
                                                    .clickable(
                                                        interactionSource = remember {
                                                            MutableInteractionSource()
                                                        },
                                                        indication = null
                                                    ) { toggleSelection(message.id) }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(
                                            horizontal = if (multiSelectMode && !message.isNotice && !message.isGameLog) 4.dp else 0.dp
                                        )
                                ) {
                                    key(message.id) {
                                        if (message.isGameLog) {
                                            GameLogBubble(
                                                content = message.content,
                                                miniAppTitle = message.miniAppTitle
                                            )
                                        } else {
                                            MessageBubbleItem(
                                                message = message,
                                                isLastAi = message.role == Role.ASSISTANT &&
                                                    !message.isThinking &&
                                                    messages.lastOrNull { !it.isNotice && !it.isGameLog }?.id == message.id,
                                                isGroupChat = isGroupChat,
                                                inMultiSelect = multiSelectMode,
                                                isGenerating = isGenerating,
                                                userAvatarUri = settings.userAvatarUri,
                                                aiAvatarUri = conversation?.persona?.aiAvatarUri,
                                                aiName = conversation?.persona?.name,
                                                senderName = if (message.role == Role.USER) {
                                                    if (isGroupChat) "我" else null
                                                } else {
                                                    if (isGroupChat) {
                                                        message.senderId?.let {
                                                            senderNameMap[it]
                                                                ?.takeIf { name -> name.isNotBlank() }
                                                                ?: "未知成员"
                                                        }
                                                    } else null
                                                },
                                                senderAvatarUri = if (isGroupChat) {
                                                    message.senderId?.let { senderAvatarMap[it] }
                                                } else null,
                                                bracketGrayEnabled = settings.bracketGrayEnabled,
                                                markdownEnabled = settings.markdownEnabled,
                                                typingDelayEnabled = settings.typingDelayEnabled,
                                                typingDelayMsPerChar = settings.typingDelayMsPerChar,
                                                isSelected = selectedMessageIds.contains(message.id),
                                                isHighlighted = highlightMessageId == message.id,
                                                isWithdrawing = expandedActionId == message.id,
                                                isActionsExpanded = expandedActionId == message.id,
                                                animateEntry = message.timestamp >= openedAtMs,
                                                viewModel = viewModel,
                                                onEnterMultiSelect = ::enterMultiSelect,
                                                onToggleSelection = ::toggleSelection,
                                                onToggleActions = {
                                                    if (!message.isThinking) {
                                                        expandedActionId = if (expandedActionId == message.id) {
                                                            null
                                                        } else {
                                                            message.id
                                                        }
                                                    }
                                                },
                                                onStartRewrite = { rewritingMessageId = it; expandedActionId = null }
                                            )
                                        }
                                    }
                                }

                            }

                            // 小应用邀请卡片：按时间倒序固定在列表顶部区域，点击跳回对应小应用
                            items(
                                items = messages.filter { it.isNotice && !it.miniAppId.isNullOrBlank() },
                                key = { it.id }
                            ) { invite ->
                                MiniAppInviteCard(
                                    title = invite.miniAppTitle?.takeIf { it.isNotBlank() }
                                        ?: "小应用",
                                    content = invite.content,
                                    onClick = { invite.miniAppId?.let(onOpenMiniApp) }
                                )
                            }

                            // 场景/世界提示气泡：固定在消息列表最顶部（reverseLayout 的最后一项），
                            // 长存、随场景设置实时更新
                            if (sceneNoticeContent.isNotBlank()) {
                                item(key = "scene_notice_bubble", contentType = { "notice" }) {
                                    NoticeBubble(content = sceneNoticeContent)
                                }
                            }
                        }
                    }
                }
            }

            // 输入栏区域：随键盘升起/降下（与消息列表同步偏移），顶栏与背景不动
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = keyboardOffset.value
                    }
            ) {
                // 输入栏：多选模式下切换为底部操作栏（复制/导出长图/删除），搜索时隐藏
                if (multiSelectMode) {
                    MultiSelectActionBar(
                        selectedCount = selectedMessageIds.size,
                        onCopy = { copySelectedMessages() },
                        onExportImage = { exportSelectedAsImage() },
                        onDelete = { deleteSelectedMessages() }
                    )
                } else if (!searchActive) {
                    ChatInputBar(
                        enterToSend = settings.enterToSend,
                        isGenerating = isGenerating,
                        allowSendWhileGenerating = isGroupChat,
                        onSend = { text ->
                            val imageUri = pendingImageUri
                            if (imageUri != null) {
                                viewModel.sendMessageWithImage(context, text, imageUri)
                            } else {
                                viewModel.sendMessage(text)
                            }
                        },
                        onStop = { viewModel.stopGeneration() },
                        enabled = !showHamburger,
                        transparent = wallpaperUri != null,
                        onTextChange = { text -> viewModel.updateInputText(text) },
                        isCompressing = isCompressing,
                        onPickImage = { imagePickerLauncher.launch("image/*") },
                        pendingImageUri = pendingImageUri,
                        onRemoveImage = { viewModel.clearPendingImage() },
                        ocrBusy = ocrState is com.quiddity.app.ui.chat.OcrState.Recognizing,
                        // 群聊成员头像栏（方案十一：并入输入框容器、靠左、随键盘一起动）
                        header = if (isGroupChat) {
                            { mentionScope ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    GroupAvatarBar(
                                        members = groupMembers,
                                        queue = groupQueue,
                                        onTap = { member ->
                                            val memberName = member.persona?.name.orEmpty()
                                            if (mentionScope.isMentionPending && memberName.isNotBlank()) {
                                                // @ 点名：把「@名字」（蓝色）插入输入框
                                                mentionScope.insertMention(memberName)
                                            } else {
                                                // 普通点名回复：头像点击触发该成员回复
                                                viewModel.enqueueGroupMember(member.id)
                                            }
                                        }
                                    )
                                }
                            }
                        } else {
                            null
                        }
                    )
                }
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
        onDismiss = { dragController.closeMenu() },
        onDeleteConversation = {
            viewModel.deleteCurrentConversation()
            onBack()
        },
        onJumpToMessage = { id ->
            // 走统一的 pendingJumpMessageId 定位机制（自动滚动协程消费），
            // 避免与 IME 收起/菜单关闭动画竞争导致跳转被拉回底部
            pendingJumpMessageId = id
        }
    )

    // ===== 压缩进度弹窗 =====
    CompressionProgressDialog(visible = isCompressing)

    // ===== 私聊用户名强制弹窗（方案九.6） =====
    if (showUserNameDialog) {
        AlertDialog(
            onDismissRequest = { showUserNameDialog = false },
            title = { Text("设置用户名") },
            text = {
                OutlinedTextField(
                    value = userNameInput,
                    onValueChange = { userNameInput = it },
                    label = { Text("用户名（未填写不能发送消息）") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = userNameInput.isNotBlank(),
                    onClick = {
                        val conv = conversation
                        if (conv != null) {
                            viewModel.updateUserPersona(
                                conv.userPersona.copy(name = userNameInput.trim())
                            )
                        }
                        showUserNameDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUserNameDialog = false }) {
                    Text("暂不设置")
                }
            }
        )
    }

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

/**
 * 将 Compose Color 转换为 ARGB Int（导出长图样式使用）。
 */
private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
    (alpha * 255f).roundToInt() shl 24 or
        ((red * 255f).roundToInt() shl 16) or
        ((green * 255f).roundToInt() shl 8) or
        (blue * 255f).roundToInt()

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
        // Box+background+clip 替代 Surface：避免 CompositionLocalProvider 与 elevation 处理开销
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLetAiStart
                )
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 28.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isGenerating) "$aiName 正在说话..." else "让 $aiName 先发消息",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ThinkingBubble(
    aiAvatarUri: String?,
    thinkingLabel: String? = null
) {
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
        // Box+background+clip 替代 Surface：去除 CompositionLocalProvider 开销
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (thinkingLabel != null) {
                    com.quiddity.app.ui.components.ShimmerHighlightText(
                        text = thinkingLabel,
                        icon = Icons.Filled.AutoAwesome
                    )
                }
                TypingIndicator()
            }
        }
    }
}

/**
 * 多选模式顶部操作栏（精简版）。
 *
 * 布局：关闭按钮 | 已选 N 项 | 全选（文字按钮）
 * - 复制 / 导出长图 / 删除 操作移到底部操作栏，避免遮挡聊天内容
 */
@Composable
private fun MultiSelectTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
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
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = "已选 $selectedCount 项",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        TextButton(onClick = onSelectAll) {
            Text(
                text = if (allSelected) "取消全选" else "全选",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 多选模式底部操作栏：复制 / 导出长图 / 删除。
 * 放置在原输入栏位置，选中数为 0 时置灰不可点。
 */
@Composable
private fun MultiSelectActionBar(
    selectedCount: Int,
    onCopy: () -> Unit,
    onExportImage: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MultiSelectActionButton(
                icon = Icons.Filled.ContentCopy,
                label = "复制",
                enabled = selectedCount > 0,
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onCopy
            )
            MultiSelectActionButton(
                icon = Icons.Filled.IosShare,
                label = "导出长图",
                enabled = selectedCount > 0,
                tint = MaterialTheme.colorScheme.primary,
                onClick = onExportImage
            )
            MultiSelectActionButton(
                icon = Icons.Filled.Delete,
                label = "删除",
                enabled = selectedCount > 0,
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun MultiSelectActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) tint else tint.copy(alpha = 0.35f),
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 单条消息气泡的包装层。
 *
 * 性能关键：把 ChatScreen 内的 lambda 计算下沉到这里 + 用 `remember` 缓存。
 * 与父级 `key(message.id)` 配合，确保 ChatScreen 重组时（流式 token 触发 messages 变化）：
 * - 不同 message.id 的气泡完全跳过（key 阻断）
 * - 同 message.id 的气泡：其 lambdas 因 remember 引用稳定，MessageBubble 可跳过重组
 *
 * 关键技巧：lambda 的 remember key 全部为稳定来源（viewModel / message.id / 状态枚举），
 * lambda body 内部直接调用 viewModel.xxx() 或用父级函数引用 (::fun) 避免捕获新 lambda 实例。
 */
@Composable
private fun MessageBubbleItem(
    message: Message,
    isLastAi: Boolean,
    isGroupChat: Boolean,
    inMultiSelect: Boolean,
    isGenerating: Boolean,
    userAvatarUri: String?,
    aiAvatarUri: String?,
    aiName: String?,
    senderName: String?,
    senderAvatarUri: String?,
    bracketGrayEnabled: Boolean,
    markdownEnabled: Boolean,
    typingDelayEnabled: Boolean,
    typingDelayMsPerChar: Int,
    isSelected: Boolean,
    isHighlighted: Boolean,
    isWithdrawing: Boolean,
    isActionsExpanded: Boolean,
    animateEntry: Boolean,
    viewModel: ChatViewModel,
    onEnterMultiSelect: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onToggleActions: () -> Unit,
    onStartRewrite: (String) -> Unit
) {
    val mid = message.id
    val isUserMsg = message.role == Role.USER

    // ===== 缓存 viewModel 直接回调（稳定来源：viewModel）=====
    val regen = remember(viewModel, mid, isGroupChat) {
        if (isGroupChat) {
            { viewModel.regenerateGroupMemberMessage(mid) }
        } else {
            { viewModel.regenerate() }
        }
    }
    // 继续说仅私聊（继续最后一条 AI 回复）；群聊继续接话靠点头像点名，不提供继续说
    val cont = remember(viewModel) { { viewModel.continueGeneration() } }
    val withdraw = remember(viewModel, mid) {
        {
            viewModel.withdrawMessage(mid)
            onToggleActions()
        }
    }

    // ===== 缓存依赖状态的回调（key 用稳定的状态枚举）=====
    // lambda body 用 { ... } 包裹成 () -> Unit 表达式，避免 Kotlin 把单语句函数调用当成 Unit 返回值
    // （推断出 Unit 而非 () -> Unit，类型不匹配）。
    // 重说：私聊=重说 AI 这一整轮；群聊=仅限最后一条成员消息（方案：群聊重说仅末条）
    val onRegenFinal: (() -> Unit)? = if (!inMultiSelect && !isGenerating && !isUserMsg && isLastAi) {
        remember<() -> Unit>(inMultiSelect, isLastAi, isGenerating, regen) { { regen() } }
    } else null
    val onContFinal: (() -> Unit)? = if (!inMultiSelect && isLastAi && !isGenerating && !isGroupChat) {
        remember<() -> Unit>(inMultiSelect, isLastAi, isGenerating, cont) { { cont() } }
    } else null
    val onWithdrawFinal: (() -> Unit)? = if (!inMultiSelect && isUserMsg && !isGenerating) {
        remember<() -> Unit>(inMultiSelect, isUserMsg, isGenerating, withdraw) { { withdraw() } }
    } else null
    val onBubbleClickFinal: (() -> Unit)? = if (!inMultiSelect && isUserMsg && !isGenerating) {
        remember<() -> Unit>(inMultiSelect, isUserMsg, isGenerating) { { onToggleActions() } }
    } else null
    val onLongClickFinal: (() -> Unit)? = if (!inMultiSelect && !isGenerating) {
        remember<() -> Unit>(inMultiSelect, isGenerating) { { onEnterMultiSelect(mid) } }
    } else null
    // AI 消息操作面板：展开时提供改写/删除；重说/继续说按各自可用性显示。
    val onRewriteFinal: (() -> Unit)? = if (!inMultiSelect && !isUserMsg && !isGenerating && isActionsExpanded) {
        remember<() -> Unit>(inMultiSelect, isUserMsg, isGenerating, isActionsExpanded) {
            { onStartRewrite(mid) }
        }
    } else null
    val onSelectFinal: (() -> Unit)? = if (inMultiSelect) {
        remember<() -> Unit>(inMultiSelect) { { onToggleSelection(mid) } }
    } else null

    MessageBubble(
        message = message,
        isGroupChat = isGroupChat,
        userAvatarUri = userAvatarUri,
        aiAvatarUri = aiAvatarUri,
        aiName = aiName,
        senderName = senderName,
        senderAvatarUri = senderAvatarUri,
        bracketGrayEnabled = bracketGrayEnabled,
        markdownEnabled = markdownEnabled,
        typingDelayEnabled = typingDelayEnabled,
        typingDelayMsPerChar = typingDelayMsPerChar,
        isLastAiMessage = isLastAi,
        onRegenerate = onRegenFinal,
        onContinue = onContFinal,
        onWithdraw = onWithdrawFinal,
        isWithdrawing = isWithdrawing,
        onBubbleClick = onBubbleClickFinal,
        onLongClick = onLongClickFinal,
        isActionsExpanded = isActionsExpanded,
        animateEntry = animateEntry,
        onToggleActions = if (!inMultiSelect && !isUserMsg && !isGenerating) {
            remember<() -> Unit>(inMultiSelect, isUserMsg, isGenerating) { { onToggleActions() } }
        } else null,
        onRewrite = onRewriteFinal,
        isHighlighted = isHighlighted,
        multiSelectMode = inMultiSelect,
        isSelected = isSelected,
        onSelectToggle = onSelectFinal
    )
}

/**
 * 会话内搜索条：输入框 + 清除 + 关闭。
 * 由顶栏搜索图标展开，点关闭或系统返回键收起。
 */
@Composable
private fun ChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索本会话消息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "清除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onQueryChange("") }
                        )
                )
            }
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭搜索",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose
                    )
            )
        }
    }
}

/** 会话内搜索结果行：角色 + 内容摘录 + 时间；点击后定位到该消息。 */
@Composable
private fun ChatSearchResultRow(
    message: Message,
    query: String,
    onClick: () -> Unit
) {
    val roleLabel = if (message.role == Role.USER) "我" else "AI"
    val colorScheme = MaterialTheme.colorScheme
    val excerpt = remember(message.content, query) {
        ChatRecordSearch.buildExcerpt(message.content, query)
    }
    val displayText = remember(excerpt, message.content, roleLabel, colorScheme) {
        val fallback = message.content.replace("\n", " ").trim()
            .let { if (it.length > 80) it.take(80) + "…" else it }
        val prefix = "$roleLabel："
        buildAnnotatedString {
            append(prefix)
            append(excerpt?.text ?: fallback)
            if (excerpt != null) {
                excerpt.highlights.forEach { range ->
                    addStyle(
                        SpanStyle(
                            background = colorScheme.primary.copy(alpha = 0.15f),
                            fontWeight = FontWeight.SemiBold
                        ),
                        start = prefix.length + range.first,
                        end = prefix.length + range.last + 1
                    )
                }
            }
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = DateUtils.formatSearchTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
