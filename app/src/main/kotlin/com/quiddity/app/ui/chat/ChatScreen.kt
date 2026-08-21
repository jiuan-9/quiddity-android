package com.quiddity.app.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
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
import com.quiddity.app.data.model.hasPersonaContent
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
import com.quiddity.app.ui.chat.components.ReeditNoticeBubble
import com.quiddity.app.ui.chat.components.TypingIndicator
import com.quiddity.app.ui.chat.gesture.ChatDragController
import com.quiddity.app.ui.chat.gesture.detectNativeHorizontalSwipe
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.ChatImageExporter
import com.quiddity.app.util.DateUtils
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
    val overlayActiveReplies by viewModel.overlayActiveReplies.collectAsStateWithLifecycle()
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
    val pendingReedit by viewModel.pendingReedit.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ===== 图片发送：选择图片 → 复制到内部存储（规避临时授权丢失） → 挂载待发送 =====
    val imagePickerLauncher = rememberChatImagePicker(context, pendingImageUri) { uri ->
        viewModel.setPendingImage(uri)
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
    // 仅当会话已有角色卡内容（AI 人设 / 用户人设 / 场景 / 记忆任一非空）时弹窗；
    // 无角色卡的新会话（没有任何设定）不弹窗，也不参与群聊 / Agent 角色检测。
    var showUserNameDialog by rememberSaveable { mutableStateOf(false) }
    var userNameInput by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(conversation?.id) {
        val conv = conversation
        if (conv != null && conv.type != ConversationType.GROUP &&
            conv.userPersona.name.isBlank() && conv.hasPersonaContent
        ) {
            userNameInput = ""
            showUserNameDialog = true
        }
    }

    // 当前展开操作项的消息 id（用户消息=撤回；AI 消息=重说/继续说/改写/删除面板）。
    // 全局只保留一个展开项，点击其他气泡自动切换。
    var expandedActionId by rememberSaveable { mutableStateOf<String?>(null) }
    var rewritingMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    // 「重新编辑」编辑框是否打开（撤回后点击灰色提示气泡进入）
    var reeditSheetOpen by rememberSaveable { mutableStateOf(false) }

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
            } else if (isAtBottom) {
                // 用户正在底部时新消息才自动跟随到最底；已上滑浏览历史时
                // 不强制回底，避免后台/定时回复追加消息把阅读位置拽回底部。
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
    // 悬浮窗/后台触发的回复在本 ViewModel 外运行：只要有进行中回复就显示加载提示
    val overlayReplying = conversation?.id in overlayActiveReplies
    // swipeEnabled 不含 !showHamburger：菜单打开时手势保持 enabled，由 ChatDragController
    // 根据 menuOpen 状态区分"右滑关菜单"与"右滑返回"。否则菜单打开后无法滑动关闭，只能系统返回键（卡死根因）。
    // 多选模式下禁用横滑，避免误触退出会话。
    // 生成/加载中仍允许左滑打开汉堡菜单；右滑返回由 backGestureEnabledState 单独禁用。
    val swipeEnabled = rewritingMessageId == null && !reeditSheetOpen &&
        !isCompressing && !multiSelectMode && !searchActive &&
        // 无障碍模拟手势注入期间禁用应用内左右滑手势，避免 AI 操作屏幕时"划退"退出会话
        !com.quiddity.app.active.ScreenReaderService.injecting
    // rememberUpdatedState：pointerInput 用 Unit key 不重启，通过它读取最新 swipeEnabled，
    // 避免 left-swipe 过程中 showHamburger 翻转导致 pointerInput 重启、手势被打断（左滑卡死根因）。
    val swipeEnabledState = rememberUpdatedState(swipeEnabled)
    // 生成/压缩中禁用右滑返回（防止误触退出会话），左滑菜单不受影响
    val backGestureEnabledState = rememberUpdatedState(!isGenerating && !isCompressing)

    val dragController = remember(screenWidthPx) {
        ChatDragController(
            scope = scope,
            screenWidthPx = screenWidthPx,
            onBack = onBack,
            onMenuVisibilityChange = { open -> showHamburger = open }
        )
    }

    // ===== 系统返回键 =====
    // 优先级：菜单 BackHandler > 多选模式 > 改写/重新编辑中状态 > 返回手势动画
    BackHandler(enabled = !isGenerating && !showHamburger) {
        when {
            searchActive -> {
                searchActive = false
                searchQuery = ""
            }
            multiSelectMode -> exitMultiSelect()
            rewritingMessageId != null -> rewritingMessageId = null
            reeditSheetOpen -> reeditSheetOpen = false
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
        ChatWallpaperLayer(
            wallpaperUri = wallpaperUri,
            wallpaperScrim = wallpaperScrim
        )

        // ===== 内容层：顶栏 + 消息列表 + 输入栏（由外层 Box 的 graphicsLayer 统一驱动滑出） =====
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {

            ChatTopBarArea(
                conversation = conversation,
                messages = messages,
                multiSelectMode = multiSelectMode,
                selectedMessageIds = selectedMessageIds,
                allSelectableIds = allSelectableIds,
                searchActive = searchActive,
                searchQuery = searchQuery,
                isGroupChat = isGroupChat,
                groupEmpty = groupMembers.isEmpty(),
                onExitMultiSelect = { exitMultiSelect() },
                onSelectAll = { toggleSelectAll() },
                onBack = { dragController.animateBackAndExit() },
                onOpenMenu = { dragController.toggleMenu() },
                onSearchQueryChange = { searchQuery = it },
                onSearchClose = {
                    searchActive = false
                    searchQuery = ""
                },
                onOpenHamburger = { showHamburger = true }
            )

            // ===== 消息列表区域（手势已挪到外层 Box，整屏生效） =====
            ChatMessageListArea(
                conversation = conversation,
                messages = messages,
                isLoading = isLoading,
                isGenerating = isGenerating,
                overlayReplying = overlayReplying,
                isGroupChat = isGroupChat,
                searchActive = searchActive,
                searchQuery = searchQuery,
                sceneNoticeContent = sceneNoticeContent,
                pendingReedit = pendingReedit,
                selectedMessageIds = selectedMessageIds,
                multiSelectMode = multiSelectMode,
                listState = listState,
                keyboardOffset = keyboardOffset,
                density = density,
                settings = settings,
                senderNameMap = senderNameMap,
                senderAvatarMap = senderAvatarMap,
                highlightMessageId = highlightMessageId,
                expandedActionId = expandedActionId,
                openedAtMs = openedAtMs,
                viewModel = viewModel,
                onOpenMiniApp = onOpenMiniApp,
                onEnterMultiSelect = { enterMultiSelect(it) },
                onToggleSelection = { toggleSelection(it) },
                onSearchResultClick = {
                    searchActive = false
                    searchQuery = ""
                    pendingJumpMessageId = it
                },
                onReedit = { reeditSheetOpen = true },
                onDismissReedit = { viewModel.clearPendingReedit() },
                onStartRewrite = {
                    rewritingMessageId = it
                    expandedActionId = null
                },
                onToggleActions = { messageId ->
                    expandedActionId = if (expandedActionId == messageId) null else messageId
                }
            )
            // 输入栏区域：随键盘升起/降下（与消息列表同步偏移），顶栏与背景不动
            ChatInputBarArea(
                viewModel = viewModel,
                context = context,
                settings = settings,
                isGenerating = isGenerating,
                isCompressing = isCompressing,
                isGroupChat = isGroupChat,
                multiSelectMode = multiSelectMode,
                searchActive = searchActive,
                selectedMessageIds = selectedMessageIds,
                pendingImageUri = pendingImageUri,
                ocrState = ocrState,
                wallpaperUri = wallpaperUri,
                showHamburger = showHamburger,
                groupMembers = groupMembers,
                groupQueue = groupQueue,
                imagePickerLauncher = imagePickerLauncher,
                keyboardOffset = keyboardOffset,
                onCopy = { copySelectedMessages() },
                onExportImage = { exportSelectedAsImage() },
                onDelete = { deleteSelectedMessages() }
            )
        }

        // 遮罩由 HamburgerMenu 内部自己管理（alpha 跟菜单同步，0.4s 淡入淡出），
        // 这里不再单独放一个 Box 避免跟消息列表抢事件。
    }

    ChatScreenDialogs(
        showHamburger = showHamburger,
        menuAlphaState = dragController.menuAlphaState,
        isCompressing = isCompressing,
        showUserNameDialog = showUserNameDialog,
        userNameInput = userNameInput,
        conversation = conversation,
        messages = messages,
        rewritingMessageId = rewritingMessageId,
        pendingReedit = pendingReedit,
        reeditSheetOpen = reeditSheetOpen,
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        onBack = onBack,
        onCloseMenu = { dragController.closeMenu() },
        onJumpToMessage = { pendingJumpMessageId = it },
        onUserNameInputChange = { userNameInput = it },
        onUserNameConfirm = {
            val conv = conversation
            if (conv != null) {
                viewModel.updateUserPersona(
                    conv.userPersona.copy(name = userNameInput.trim())
                )
            }
            showUserNameDialog = false
        },
        onDismissUserNameDialog = { showUserNameDialog = false },
        onDismissRewrite = { rewritingMessageId = null },
        onDismissReeditSheet = { reeditSheetOpen = false }
    )
}

private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
    (alpha * 255f).roundToInt() shl 24 or
        ((red * 255f).roundToInt() shl 16) or
        ((green * 255f).roundToInt() shl 8) or
        (blue * 255f).roundToInt()
