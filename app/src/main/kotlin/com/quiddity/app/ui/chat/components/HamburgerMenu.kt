package com.quiddity.app.ui.chat.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ExportPayload
import com.quiddity.app.data.model.ImportMode
import com.quiddity.app.data.model.PersonaCard
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.chat.components.panels.ApiEditorPanel
import com.quiddity.app.ui.chat.components.panels.ApiSelectorPanel
import com.quiddity.app.ui.chat.components.panels.CompressionPanel
import com.quiddity.app.ui.chat.components.panels.PersonaPanel
import com.quiddity.app.ui.chat.components.panels.QuickSetupPanel
import com.quiddity.app.ui.chat.components.panels.ScenePanel
import com.quiddity.app.ui.chat.components.panels.TokenStatsPanel
import com.quiddity.app.ui.chat.components.panels.UserPersonaPanel
import com.quiddity.app.ui.chat.components.panels.WallpaperPanel
import com.quiddity.app.ui.components.ActiveMessagePermissionCard
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.components.ApiEditBottomSheet
import com.quiddity.app.ui.components.ApiCatalogEditFormState
import com.quiddity.app.ui.components.ConfirmDialog
import com.quiddity.app.util.DateUtils
import com.quiddity.app.ui.components.ExpandableText
import com.quiddity.app.ui.components.QuiddityToggleSwitch
import com.quiddity.app.ui.components.TemperatureSlider
import com.quiddity.app.util.QuiddityConstants
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.ConversationCodec
import com.quiddity.app.util.DataPorter
import com.quiddity.app.util.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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


// 当前规则：
// - 菜单透明度由 menuAlphaState（MutableFloatState，来自 ChatDragController.menuAlphaState，0~1）提供，
//   在 graphicsLayer 内 draw phase 直接读 .floatValue，零重组且 state 追踪可靠。
// - 半透明遮罩由 HamburgerMenu 内部自己管理（alpha 跟菜单同步），不依赖 ChatScreen 的 Box。
// - 子面板切换用纯 fade，避免嵌套 slide 与外层透明度变化冲突。
@Composable
fun HamburgerMenu(
    visible: Boolean,
    menuAlphaState: MutableFloatState,
    viewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    // 群聊菜单「删除该会话」确认后回调（由 ChatScreen 执行删除并返回首页）
    onDeleteConversation: () -> Unit = {},
    onJumpToMessage: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val apiCatalogManager = remember { ServiceLocator.apiCatalogManager }
    val currentTier = remember(conversation) { viewModel.resolveCurrentTier() }
    val webSearchSupported = remember(conversation, settings) {
        val conv = conversation ?: return@remember false
        val entry = apiCatalogManager.resolveEntry(settings, conv) ?: return@remember false
        apiCatalogManager.supportsServerWebSearch(entry)
    }
    val currentModelId = remember(conversation, settings) {
        settings.catalog
            .firstOrNull { it.id == (conversation?.apiCatalogId ?: settings.activeCatalogId) }
            ?.apiModel
            ?: "未选择"
    }

    // Android 13+ 需要通知权限：开启主动消息时一并请求，保证到点能弹通知
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    // 子面板导航状态
    var currentPanel by remember { mutableStateOf<HamburgerPanel?>(null) }
    var pendingClearSettings by remember { mutableStateOf(false) }
    var pendingClearMessages by remember { mutableStateOf(false) }
    var pendingDeleteConversation by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    // JSON 全量导入时暂存 payload，已有数据则弹窗让用户抉择替换/合并/取消
    var pendingImportPayload by remember { mutableStateOf<ExportPayload?>(null) }
    // 导入后需重填密钥的模型配置名称清单（3.2 解密自检失败项）
    var pendingKeyRefill by remember { mutableStateOf<List<String>?>(null) }
    // 查看时间库流程：0=关闭 1=输密码 2=展示内容
    var timeLibraryViewStep by remember { mutableIntStateOf(0) }
    var timeLibraryPasswordInput by remember { mutableStateOf("") }
    var timeLibraryPasswordError by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (!visible) {
            currentPanel = null
        }
    }

    // ===== 退回行为：子面板 -> 主菜单；主菜单 -> 关闭 =====
    BackHandler(enabled = visible) {
        if (currentPanel != null) {
            currentPanel = null
        } else {
            onDismiss()
        }
    }

    // ===== SAF 文件传输 =====
    val personaExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val card = viewModel.exportPersonaCard()
            if (card != null) {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.Default) {
                            val text = Json.encodeToString(PersonaCard.serializer(), card)
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openOutputStream(uri)
                                    ?.use { it.write(text.toByteArray()) }
                            }
                        }
                    }.onSuccess { toastMsg = "人设卡已导出" }
                        .onFailure { toastMsg = "导出失败：${it.message}" }
                }
            }
        }
    }

    val personaImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            scope.launch {
                runCatching {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)
                            ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    } ?: throw IllegalStateException("无法读取")
                    val card = withContext(Dispatchers.Default) {
                        Json.decodeFromString(PersonaCard.serializer(), text)
                    }
                    viewModel.importPersonaCard(card)
                }.onSuccess { toastMsg = "人设卡已导入" }
                    .onFailure { toastMsg = "导入失败：${it.message}" }
            }
        }
    }

    var pendingExportFormat by remember { mutableStateOf<ExportFormatPicker?>(null) }

    // JSON / Markdown / 纯文本 三种格式各自一个 launcher
    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val conv = conversation ?: return@rememberLauncherForActivityResult
            scope.launch {
                // 过滤 isNotice 提示气泡：不导出（UI 专用，非对话内容）
                val exportableMessages = viewModel.messages.value.filterNot { it.isNotice }
                val payload = ExportPayload(
                    schemaVersion = 1,
                    exportedAt = System.currentTimeMillis(),
                    settings = settings,
                    conversations = listOf(conv),
                    messages = mapOf(conv.id to exportableMessages)
                )
                DataPorter.exportTo(context, uri, payload)
                    .onSuccess { toastMsg = "对话记录已导出（JSON）" }
                    .onFailure { toastMsg = "导出失败：${it.message}" }
            }
        }
    }

    val markdownExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val text = withContext(Dispatchers.Default) {
                        viewModel.exportConversationAsText(ConversationCodec.Format.MARKDOWN)
                            ?: throw IllegalStateException("会话未加载")
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)
                            ?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                            ?: throw IllegalStateException("无法写入文件")
                    }
                }.onSuccess { toastMsg = "对话记录已导出（Markdown）" }
                    .onFailure { toastMsg = "导出失败：${it.message}" }
            }
        }
    }

    val textExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val text = withContext(Dispatchers.Default) {
                        viewModel.exportConversationAsText(ConversationCodec.Format.TEXT)
                            ?: throw IllegalStateException("会话未加载")
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)
                            ?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                            ?: throw IllegalStateException("无法写入文件")
                    }
                }.onSuccess { toastMsg = "对话记录已导出（纯文本）" }
                    .onFailure { toastMsg = "导出失败：${it.message}" }
            }
        }
    }

    // 支持 application/json + text/markdown + text/plain
    // 读取后根据内容自动识别格式并调用对应解析器
    val conversationImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            scope.launch {
                runCatching {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)
                            ?.use { it.readBytes().toString(Charsets.UTF_8) }
                            ?: throw IllegalStateException("无法读取文件")
                    }
                    val trimmed = text.trimStart()
                    when {
                        // JSON 格式：交给 DataPorter 处理（含壁纸等完整数据）
                        trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                            DataPorter.importFrom(context, uri)
                                .onSuccess { plan ->
                                    if (plan.needsKeyRefill.isNotEmpty()) {
                                        pendingKeyRefill = plan.needsKeyRefill
                                    }
                                    // 已有数据时弹窗让用户抉择；无数据时直接合并导入
                                    if (settingsViewModel.hasExistingData()) {
                                        pendingImportPayload = plan.payload
                                    } else {
                                        settingsViewModel.importAllPayload(plan.payload, mode = ImportMode.MERGE)
                                        toastMsg = if (plan.skipItems.isEmpty()) {
                                            "对话记录已导入（JSON）"
                                        } else {
                                            "对话记录已导入（${plan.skipItems.size} 项已跳过）"
                                        }
                                    }
                                }
                                .onFailure { toastMsg = "导入失败：${it.message}" }
                        }
                        // Markdown 或纯文本：交给 ConversationCodec 处理
                        else -> {
                            val result = withContext(Dispatchers.Default) {
                                viewModel.importConversationFromText(text)
                            }
                            result.onSuccess {
                                toastMsg = "对话记录已导入"
                            }.onFailure {
                                toastMsg = "导入失败：${it.message}"
                            }
                        }
                    }
                }.onFailure { toastMsg = "导入失败：${it.message}" }
            }
        }
    }

    toastMsg?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            toastMsg = null
        }
    }

    val hasWallpaper = conversation?.wallpaperUri != null

    // ===== 容器：遮罩 + 侧边栏 =====

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    Box(modifier = Modifier.fillMaxSize()) {
        if (visible) {
            // 半透明遮罩：alpha 跟菜单透明度同步（0~0.4），
            // 放在菜单本体之前（z-order 底部），点遮罩触发关闭。
            // menuAlphaState.floatValue 在 graphicsLayer lambda 内直接读——draw phase，零重组，state 追踪可靠。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = menuAlphaState.floatValue * 0.4f }
                    .background(androidx.compose.ui.graphics.Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            onDismiss()
                        }
                    }
            )
            // 菜单本体：alpha 跟 menuAlpha 同步（0~1 淡入淡出），draw phase 直接读 .floatValue
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(340.dp)
                    .graphicsLayer {
                        alpha = menuAlphaState.floatValue
                    },
                color = if (hasWallpaper) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(20.dp, 0.dp, 0.dp, 20.dp),
                border = if (hasWallpaper) {
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                } else null,
                shadowElevation = 8.dp
            ) {
                AnimatedContent(
                    targetState = currentPanel,
                    transitionSpec = {
                        // 纯 fade，避免嵌套 slide 与外层拖动冲突
                        fadeIn(tween(Motion.DurationShort)) togetherWith
                            fadeOut(tween(Motion.DurationShort))
                    },
                    label = "panel_transition",
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .imePadding()
                ) { panel ->
                    when (panel) {
                        null -> {
                            if (conversation?.type == ConversationType.GROUP) {
                                GroupMenuContent(
                                    conversation = conversation,
                                    onDismiss = onDismiss,
                                    onRename = {
                                        currentPanel = HamburgerPanel.GroupName
                                    },
                                    onContextLimit = {
                                        currentPanel = HamburgerPanel.GroupContextLimit
                                    },
                                    onStopModeChange = { mode ->
                                        viewModel.updateGroupStopMode(mode)
                                    },
                                    onGroupBackground = {
                                        currentPanel = HamburgerPanel.GroupBackground
                                    },
                                    onWallpaper = {
                                        currentPanel = HamburgerPanel.Wallpaper
                                    },
                                    onManageMembers = {
                                        currentPanel = HamburgerPanel.GroupMembers
                                    },
                                    onSearchChat = { currentPanel = HamburgerPanel.SearchChat },
                                    onClearMessages = { pendingClearMessages = true },
                                    onDeleteConversation = { pendingDeleteConversation = true },
                                    darkMode = settings.darkMode,
                                    onDarkModeChange = { dark -> settingsViewModel.setDarkMode(dark) }
                                )
                            } else {
                                MainMenuContent(
                                    conversation = conversation,
                                    messages = messages,
                                    currentTier = currentTier,
                                    settings = settings,
                                    onPanelSelected = { currentPanel = it },
                                    onDismiss = onDismiss,
                                    darkMode = settings.darkMode,
                                    onDarkModeChange = { settingsViewModel.setDarkMode(it) },
                                    onClearSettings = { pendingClearSettings = true },
                                    onExportPersona = { personaExportLauncher.launch("quiddity-persona-${IdGenerator.newUuid()}.json") },
                                    onImportPersona = { personaImportLauncher.launch(arrayOf("application/json")) },
                                    onExportConversation = { pendingExportFormat = ExportFormatPicker() },
                                    onImportConversation = {
                                        conversationImportLauncher.launch(
                                            arrayOf(
                                                "application/json",
                                                "text/markdown",
                                                "text/plain",
                                                "application/octet-stream"
                                            )
                                        )
                                    },
                                    onContextLimitChange = { limit ->
                                        viewModel.updateContextLimit(limit)
                                    },
                                    onResetContextLimit = {
                                        viewModel.resetContextLimitToTierDefault()
                                    },
                                    onMemoryBankEnabledChange = { enabled ->
                                        viewModel.updateMemoryBankEnabled(enabled)
                                    },
                                    onMemoryBankRoundsChange = { rounds ->
                                        viewModel.updateMemoryBankRounds(rounds)
                                    },
                                    onCompressionClick = { currentPanel = HamburgerPanel.Compression },
                                    onClearMessages = { pendingClearMessages = true },
                                    onActiveMessageChange = { enabled ->
                                        if (enabled &&
                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        viewModel.setActiveMessageEnabled(enabled)
                                    },
                                    onViewTimeLibrary = {
                                        timeLibraryViewStep =
                                            if (conversation?.timeLibraryPasswordUnlocked == true) 2 else 1
                                    },
                                    onOpenSearchChat = { currentPanel = HamburgerPanel.SearchChat },
                                    replyStyle = conversation?.replyStyle
                                        ?: QuiddityConstants.REPLY_STYLE_FOLLOW_PERSONA,
                                    onReplyStyleChange = { style ->
                                        viewModel.updateReplyStyle(style)
                                    },
                                    webSearchSupported = webSearchSupported,
                                    currentModelId = currentModelId,
                                    onTemperatureChange = { value ->
                                        viewModel.updateTemperature(value)
                                    },
                                    onWebSearchChange = { enabled ->
                                        viewModel.updateWebSearchEnabled(enabled)
                                    }
                                )
                            }
                        }
                        HamburgerPanel.QuickSetup -> {
                            conversation?.let { conv ->
                                val tier = viewModel.resolveCurrentTier()
                                val hasExisting = conv.persona.name.isNotBlank() ||
                                    conv.persona.persona.isNotBlank() ||
                                    conv.persona.character.isNotBlank() ||
                                    conv.persona.appearance.isNotBlank() ||
                                    conv.persona.worldBackground.isNotBlank() ||
                                    conv.persona.desired.isNotBlank() ||
                                    conv.userPersona.name.isNotBlank() ||
                                    conv.userPersona.identity.isNotBlank() ||
                                    conv.userPersona.gender.isNotBlank() ||
                                    conv.userPersona.age.isNotBlank() ||
                                    conv.userPersona.appearance.isNotBlank() ||
                                    conv.scene.isNotBlank() ||
                                    conv.memory.isNotBlank()
                                QuickSetupPanel(
                                    currentTier = tier,
                                    hasExistingContent = hasExisting,
                                    hasMessages = messages.any { !it.isNotice },
                                    initialDraft = conv.quickSetupDraft,
                                    onDraftChange = { draft ->
                                        viewModel.updateQuickSetupDraft(draft)
                                    },
                                    onGenerate = { description, selectedTier ->
                                        viewModel.quickSetupGenerate(description, selectedTier)
                                    },
                                    onApply = { rawText, selectedTier ->
                                        viewModel.applyQuickSetupResult(rawText, selectedTier)
                                    },
                                    onFinished = {
                                        currentPanel = null
                                        onDismiss()
                                    },
                                    onClearMessages = {
                                        viewModel.clearConversationMessages()
                                    },
                                    onBack = { currentPanel = null }
                                )
                            }
                        }
                        HamburgerPanel.Persona -> {
                            conversation?.let { conv ->
                                val currentTier = viewModel.resolveCurrentTier()
                                PersonaPanel(
                                    initial = conv.persona,
                                    ownerId = conv.id,
                                    compileEnabled = conv.compileEnabled,
                                    modelTier = currentTier,
                                    catalogManager = apiCatalogManager,
                                    onBack = { currentPanel = null },
                                    onSave = { persona, compileEnabled ->
                                        viewModel.updatePersona(persona, compileEnabled)
                                        currentPanel = null
                                    },
                                    onSaveAndExit = { persona, compileEnabled ->
                                        viewModel.updatePersona(persona, compileEnabled)
                                        currentPanel = null
                                        onDismiss()
                                    },
                                    onAutoSave = { persona, compileEnabled ->
                                        viewModel.updatePersona(persona, compileEnabled)
                                    },
                                    onCompile = { persona, maxTokens ->
                                        viewModel.compilePersona(persona, maxTokens)
                                    }
                                )
                            }
                        }
                        HamburgerPanel.UserPersona -> {
                            conversation?.let { conv ->
                                UserPersonaPanel(
                                    initial = conv.userPersona,
                                    initialMemory = conv.memory,
                                    onBack = { currentPanel = null },
                                    onSave = { userPersona, memory ->
                                        viewModel.updateUserPersona(userPersona)
                                        viewModel.updateMemory(memory)
                                        currentPanel = null
                                    },
                                    onAutoSave = { userPersona, memory ->
                                        viewModel.updateUserPersona(userPersona)
                                        viewModel.updateMemory(memory)
                                    }
                                )
                            }
                        }
                        HamburgerPanel.Scene -> {
                            conversation?.let { conv ->
                                ScenePanel(
                                    initialScene = conv.scene,
                                    onBack = { currentPanel = null },
                                    onSave = {
                                        viewModel.updateScene(it)
                                        currentPanel = null
                                    },
                                    onAutoSave = { viewModel.updateScene(it) }
                                )
                            }
                        }
                        HamburgerPanel.ApiSelector -> {
                            ApiSelectorPanel(
                                catalog = settings.catalog,
                                currentSelection = conversation?.apiCatalogId ?: settings.activeCatalogId,
                                onBack = { currentPanel = null },
                                onSelect = { id ->
                                    if (id != null) viewModel.setConversationApi(id)
                                    currentPanel = null
                                }
                            )
                        }
                        HamburgerPanel.ApiEditor -> {
                            ApiEditorPanel(
                                catalog = settings.catalog,
                                catalogManager = apiCatalogManager,
                                onBack = { currentPanel = null },
                                onAddCatalog = { state ->
                                    // UI 层不预生成 id，统一由 SettingsViewModel.upsertCatalog 负责
                                    settingsViewModel.upsertCatalog(
                                        id = null,
                                        name = state.name,
                                        providerId = state.providerId,
                                        apiUrl = state.apiUrl,
                                        apiModel = state.apiModel,
                                        apiKey = state.apiKey
                                    )
                                },
                                onUpdateCatalog = { state ->
                                    if (state.id.isBlank()) {
                                        toastMsg = "更新失败：模型配置 id 为空"
                                    } else {
                                        settingsViewModel.upsertCatalog(
                                            id = state.id,
                                            name = state.name,
                                            providerId = state.providerId,
                                            apiUrl = state.apiUrl,
                                            apiModel = state.apiModel,
                                            apiKey = state.apiKey
                                        )
                                    }
                                },
                                onDeleteCatalog = { id -> settingsViewModel.removeCatalog(id) }
                            )
                        }
                        // - 每次打开都从当前 conversation 读取最新 wallpaperUri / darken
                        // - 修改通过 viewModel.setWallpaperUri / setWallpaperDarken 写回
                        // - 此面板状态独立于其他子面板（每次进入都重新初始化）
                        HamburgerPanel.Wallpaper -> {
                            conversation?.let { conv ->
                                WallpaperPanel(
                                    currentWallpaperUri = conv.wallpaperUri,
                                    currentDarken = conv.wallpaperDarken,
                                    onBack = { currentPanel = null },
                                    onWallpaperChanged = { uri ->
                                        viewModel.setWallpaperUri(uri)
                                    },
                                    onDarkenChanged = { value ->
                                        viewModel.setWallpaperDarken(value)
                                    }
                                )
                            }
                        }
                        HamburgerPanel.Compression -> {
                            conversation?.let { conv ->
                                val contextLimit = conv.contextLimit
                                CompressionPanel(
                                    enabled = conv.memoryBankEnabled,
                                    rounds = conv.memoryBankRounds,
                                    contextLimit = contextLimit,
                                    onBack = { currentPanel = null },
                                    onEnabledChange = { viewModel.updateMemoryBankEnabled(it) },
                                    onRoundsChange = { viewModel.updateMemoryBankRounds(it) }
                                )
                            }
                        }
                        HamburgerPanel.SearchChat -> {
                            SearchChatPanel(
                                conversation = conversation,
                                messages = messages,
                                onBack = { currentPanel = null },
                                onOpenMessage = { id ->
                                    onJumpToMessage?.invoke(id)
                                    onDismiss()
                                }
                            )
                        }
                        HamburgerPanel.GroupName -> {
                            conversation?.let { conv ->
                                GroupNamePanel(
                                    currentName = conv.title,
                                    onBack = { currentPanel = null },
                                    onSave = { name ->
                                        viewModel.renameConversation(name)
                                        currentPanel = null
                                    }
                                )
                            }
                        }
                        HamburgerPanel.GroupContextLimit -> {
                            conversation?.let { conv ->
                                GroupContextLimitPanel(
                                    currentLimit = conv.groupContextLimit,
                                    onBack = { currentPanel = null },
                                    onSave = { limit ->
                                        viewModel.updateGroupContextLimit(limit)
                                        currentPanel = null
                                    }
                                )
                            }
                        }
                        HamburgerPanel.GroupMembers -> {
                            conversation?.let { conv ->
                                GroupMemberManagePanel(
                                    group = conv,
                                    viewModel = viewModel,
                                    settings = settings,
                                    onBack = { currentPanel = null }
                                )
                            }
                        }
                        HamburgerPanel.GroupBackground -> {
                            conversation?.let { conv ->
                                GroupBackgroundPanel(
                                    currentText = conv.groupBackground,
                                    currentMode = conv.groupBackgroundMode,
                                    onBack = { currentPanel = null },
                                    onSave = { text, mode ->
                                        viewModel.updateGroupBackground(text, mode)
                                        currentPanel = null
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== 确认对话框 =====

    if (pendingClearSettings) {
        ConfirmDialog(
            title = "清空会话设置",
            message = "将重置当前会话的 AI 人设、用户人设、场景、记忆（不影响消息记录与会话记录）。此操作不可撤销。",
            confirmText = "清空",
            onConfirm = {
                viewModel.clearConversationSettings()
                pendingClearSettings = false
            },
            onDismiss = { pendingClearSettings = false }
        )
    }

    if (pendingClearMessages) {
        val isGroupConv = conversation?.type == ConversationType.GROUP
        ConfirmDialog(
            title = if (isGroupConv) "清除群聊记录" else "清空会话记录（含压缩对话）",
            message = if (isGroupConv) {
                "将删除本群聊的所有消息，并重置群聊小本本（groupMemory）。群聊设置与成员保持不变。此操作不可撤销。"
            } else {
                "将删除当前会话的所有消息，并重置压缩记忆（compressedMemory / lastCompressedAtRound）。AI 人设 / 用户 / 场景 / 记忆 / 壁纸保留。此操作不可撤销。"
            },
            confirmText = "清空",
            onConfirm = {
                viewModel.clearConversationMessages()
                pendingClearMessages = false
                toastMsg = "会话记录已清空"
            },
            onDismiss = { pendingClearMessages = false }
        )
    }

    if (pendingDeleteConversation) {
        ConfirmDialog(
            title = "删除该会话",
            message = "将删除当前群聊及其全部消息记录，成员私聊不受影响。此操作不可撤销。",
            confirmText = "删除",
            onConfirm = {
                pendingDeleteConversation = false
                onDeleteConversation()
            },
            onDismiss = { pendingDeleteConversation = false }
        )
    }

    // 导入后密钥重填提示
    pendingKeyRefill?.let { names ->
        ConfirmDialog(
            title = "部分接口密钥需重新填写",
            message = "导入的备份中以下模型配置的密钥无法解密（可能来自其他设备），" +
                "请到「模型配置」中重新填写：\n\n" + names.joinToString("\n"),
            confirmText = "知道了",
            cancelText = null,
            onConfirm = { pendingKeyRefill = null },
            onDismiss = { pendingKeyRefill = null }
        )
    }

    // ===== 查看时间库流程：先验密码（如有），再展示内容 =====
    if (timeLibraryViewStep == 1) {
        val conv = conversation
        if (conv != null && conv.timeLibraryPassword.isNotBlank()) {
            TimeLibraryPasswordDialog(
                error = timeLibraryPasswordError,
                input = timeLibraryPasswordInput,
                revealed = conv.timeLibraryPasswordRevealed,
                password = conv.timeLibraryPassword,
                onInputChange = { value ->
                    timeLibraryPasswordInput = value.filter { it.isDigit() }.take(6)
                    timeLibraryPasswordError = false
                },
                onConfirm = {
                    if (timeLibraryPasswordInput == conv.timeLibraryPassword) {
                        viewModel.markTimeLibraryUnlocked()
                        timeLibraryViewStep = 2
                        timeLibraryPasswordInput = ""
                        timeLibraryPasswordError = false
                    } else {
                        timeLibraryPasswordError = true
                    }
                },
                onDismiss = {
                    timeLibraryViewStep = 0
                    timeLibraryPasswordInput = ""
                    timeLibraryPasswordError = false
                }
            )
        } else {
            timeLibraryViewStep = 2
        }
    }
    if (timeLibraryViewStep == 2) {
        TimeLibraryDetailDialog(
            conversation = conversation,
            onDismiss = { timeLibraryViewStep = 0 }
        )
    }

    pendingExportFormat?.let {
        ExportFormatPickerDialog(
            onDismiss = { pendingExportFormat = null },
            onSelect = { format ->
                pendingExportFormat = null
                val idPart = IdGenerator.newUuid()
                when (format) {
                    ConversationCodec.Format.JSON -> {
                        jsonExportLauncher.launch("quiddity-conversation-$idPart.json")
                    }
                    ConversationCodec.Format.MARKDOWN -> {
                        markdownExportLauncher.launch("quiddity-conversation-$idPart.md")
                    }
                    ConversationCodec.Format.TEXT -> {
                        textExportLauncher.launch("quiddity-conversation-$idPart.txt")
                    }
                }
            }
        )
    }

    // JSON 全量导入抉择弹窗：已有数据时让用户选择替换/合并/取消
    pendingImportPayload?.let { payload ->
        Dialog(
            onDismissRequest = { pendingImportPayload = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "导入数据",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = "检测到应用已有对话数据，请选择导入方式：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Text(
                            text = "提示：你也可以在此菜单中单独导入人设卡或对话记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.size(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { pendingImportPayload = null }) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.size(4.dp))
                        TextButton(onClick = {
                            val p = payload
                            pendingImportPayload = null
                            scope.launch {
                                settingsViewModel.importAllPayload(p, mode = ImportMode.MERGE)
                                toastMsg = "导入成功（已合并）"
                            }
                        }) { Text("合并") }
                        Spacer(modifier = Modifier.size(4.dp))
                        TextButton(onClick = {
                            val p = payload
                            pendingImportPayload = null
                            scope.launch {
                                settingsViewModel.importAllPayload(p, mode = ImportMode.CHARACTERS_ONLY)
                                toastMsg = "角色库已导入"
                            }
                        }) { Text("仅导入角色库") }
                        Spacer(modifier = Modifier.size(4.dp))
                        TextButton(onClick = {
                            val p = payload
                            pendingImportPayload = null
                            scope.launch {
                                settingsViewModel.importAllPayload(p, mode = ImportMode.REPLACE)
                                toastMsg = "导入成功（已替换）"
                            }
                        }) { Text("替换", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

/**
 * 导出格式选择对话框触发器。
 *
 * 用空对象作为状态信号，仅用于触发对话框显示；不携带数据。
 */
private class ExportFormatPicker

/**
 *
 * 在用户点击"导出对话记录"后弹出，让用户选择目标格式：
 * - JSON：完整备份（含设置 + 壁纸 + 多会话）
 * - Markdown：人类可读对话记录（含人设卡 + 消息）
 * - 纯文本：跨应用兼容格式
 */
@Composable
private fun ExportFormatPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (ConversationCodec.Format) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "选择导出格式",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "不同格式适用于不同场景",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.size(16.dp))

                ConversationCodec.Format.values().forEach { format ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(format) },
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = ".${format.extension}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = format.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = when (format) {
                                        ConversationCodec.Format.JSON -> "完整备份，可恢复所有数据"
                                        ConversationCodec.Format.MARKDOWN -> "可读对话记录，含人设卡"
                                        ConversationCodec.Format.TEXT -> "纯文本，跨应用兼容"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
                TextButton(onClick = onDismiss) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 把 "13:30" 转成"下午 1:30"这种用户一看就懂的说法。 */
private fun readableTimeText(time: String): String {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return time
    val minute = parts.getOrNull(1) ?: "00"
    val period = when {
        hour < 6 -> "凌晨"
        hour < 12 -> "上午"
        hour < 14 -> "中午"
        hour < 18 -> "下午"
        else -> "晚上"
    }
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$period $hour12:$minute"
}

/**
 * 查找聊天记录面板：输入关键词在本会话历史消息中搜索，
 * 结果按微信样式展示头像、名字、时间与内容摘录，点击跳转到对应消息。
 */
@Composable
private fun SearchChatPanel(
    conversation: Conversation?,
    messages: List<com.quiddity.app.data.model.Message>,
    onBack: () -> Unit,
    onOpenMessage: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val searchable = remember(messages) { messages.filterNot { it.isNotice } }
    val results = remember(query, searchable) {
        com.quiddity.app.domain.ChatRecordSearch.searchResults(searchable, query)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = "查找聊天记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.size(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入关键词，如：旅行、预算") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))

        when {
            query.isBlank() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "输入关键词，搜索本会话说过的内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            results.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "没有找到匹配的消息",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = results, key = { it.id }) { message ->
                        ChatSearchResultRow(
                            message = message,
                            conversation = conversation,
                            query = query,
                            onClick = { onOpenMessage(message.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatSearchResultRow(
    message: com.quiddity.app.data.model.Message,
    conversation: Conversation?,
    query: String,
    onClick: () -> Unit
) {
    val isUser = message.role == Role.USER
    val name = if (isUser) {
        "我"
    } else {
        conversation?.persona?.name?.ifBlank { conversation.title } ?: "AI"
    }
    val avatarUri = if (isUser) null else conversation?.persona?.aiAvatarUri
    val colorScheme = MaterialTheme.colorScheme
    val excerpt = remember(message.content, query) {
        com.quiddity.app.domain.ChatRecordSearch.buildExcerpt(message.content, query)
    }
    val displayText = remember(excerpt, message.content, name, colorScheme) {
        val fallback = message.content.replace("\n", " ").trim()
            .let { if (it.length > 80) it.take(80) + "…" else it }
        val prefix = "$name："
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
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = DateUtils.formatSearchTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 查看时间库：输入密码弹窗。 */
@Composable
private fun TimeLibraryPasswordDialog(
    error: Boolean,
    input: String,
    revealed: Boolean,
    password: String,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "查看时间库",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "这个会话的时间库设置了查看密码，密码由 AI 制定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (!revealed) {
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "AI 决定不告知密码。你可以直接在聊天里问 AI，或等明天重新生成时间库后再查看。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    label = { Text("数字密码") },
                    singleLine = true,
                    isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (error) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "密码不对，请重试",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.size(8.dp))
                    TextButton(onClick = onConfirm) { Text("查看") }
                }
            }
        }
    }
}

/** 查看时间库：内容展示弹窗（用户可读的时间说法）。 */
@Composable
private fun TimeLibraryDetailDialog(
    conversation: Conversation?,
    onDismiss: () -> Unit
) {
    val conv = conversation ?: return
    val today = java.time.LocalDate.now().toString()
    val generatedToday = conv.timeLibraryGeneratedDate == today
    val times = conv.timeLibrary
    val body = buildString {
        if (!generatedToday) {
            appendLine("今天还没有生成时间库。")
            appendLine("请确认模型接口已配置，重新打开本会话会再次尝试生成。")
        } else if (times.isEmpty()) {
            appendLine("今天的时间库是空的，AI 判断今天不需要主动发消息。")
        } else {
            times.forEach { point ->
                val state = if (point.isPending) "待触发" else "已处理"
                appendLine("${readableTimeText(point.time)} · $state")
            }
            appendLine("")
            appendLine("说明：「下午 1:30」就是下午一点半；「待触发」表示还没到时间，「已处理」表示到点已经处理过了。")
        }
        if (conv.timeLibraryPassword.isNotBlank() && conv.timeLibraryPasswordRevealed) {
            appendLine("")
            appendLine("查看密码：${conv.timeLibraryPassword}")
        }
    }
    ConfirmDialog(
        title = "今日时间库",
        message = body.trim(),
        confirmText = "知道了",
        cancelText = null,
        onConfirm = onDismiss,
        onDismiss = onDismiss
    )
}

internal enum class HamburgerPanel {
    QuickSetup, Persona, UserPersona, Scene, ApiSelector, ApiEditor,
    Wallpaper, Compression, SearchChat,
    GroupName, GroupContextLimit, GroupMembers, GroupBackground
}

// ==================== 主菜单 ====================

@Composable
private fun MainMenuContent(
    conversation: Conversation?,
    messages: List<com.quiddity.app.data.model.Message>,
    currentTier: com.quiddity.app.domain.ApiCatalogManager.ModelTier,
    settings: com.quiddity.app.data.model.AppSettings,
    onPanelSelected: (HamburgerPanel) -> Unit,
    onDismiss: () -> Unit,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onClearSettings: () -> Unit,
    onExportPersona: () -> Unit,
    onImportPersona: () -> Unit,
    onExportConversation: () -> Unit,
    onImportConversation: () -> Unit,
    onContextLimitChange: (Int) -> Unit,
    onResetContextLimit: () -> Unit,
    onMemoryBankEnabledChange: (Boolean) -> Unit,
    onMemoryBankRoundsChange: (Int) -> Unit,
    onCompressionClick: () -> Unit,
    onClearMessages: () -> Unit,
    onActiveMessageChange: (Boolean) -> Unit,
    onViewTimeLibrary: () -> Unit,
    onOpenSearchChat: () -> Unit,
    replyStyle: String,
    onReplyStyleChange: (String) -> Unit,
    webSearchSupported: Boolean,
    currentModelId: String,
    onTemperatureChange: (Double?) -> Unit,
    onWebSearchChange: (Boolean) -> Unit
) {
    var showReplyStyleDialog by remember { mutableStateOf(false) }
    var showTemperatureEditor by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部：标题 + 关闭
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "会话设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close, "关闭",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 外观
            MenuSectionCard(title = "外观") {
            // 会话内的"外观"只是全局主题的一个快捷入口，状态直接来自 settings.darkMode，
            // 切换时调用 SettingsViewModel.setDarkMode，与设置面板中的总开关保持一致。
            ToggleMenuRow(
                title = "深色模式",
                subtitle = if (darkMode) "当前：暗色" else "当前：亮色",
                checked = darkMode,
                onCheckedChange = onDarkModeChange,
            )
            // - 仅对当前会话生效，不影响其他会话
            // - 持久化到 conversation.wallpaperUri
            MenuRow(
                title = "会话壁纸",
                subtitle = if (conversation?.wallpaperUri != null) "已设置" else "未设置",
                onClick = { onPanelSelected(HamburgerPanel.Wallpaper) },
            )

            // 人设
            }
            MenuSectionCard(title = "人设") {
            MenuRow(
                title = "快速设定",
                subtitle = "描述你想要的人设，AI 一次性生成并填入",
                onClick = { onPanelSelected(HamburgerPanel.QuickSetup) },
                expandableSubtitle = true
            )
            val aiPersonaName = conversation?.persona?.name
            val aiPersonaSet = !aiPersonaName.isNullOrBlank()
            MenuRow(
                title = "AI 人设",
                subtitle = if (aiPersonaSet) "AI: $aiPersonaName" else "未设置",
                onClick = { onPanelSelected(HamburgerPanel.Persona) },
                expandableSubtitle = true,
                subtitleColor = if (aiPersonaSet) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                }
            )
            MenuRow(
                title = "用户人设",
                subtitle = if (conversation?.userPersona?.name?.isNotBlank() == true)
                    "用户: ${conversation.userPersona.name}" else "未设置",
                onClick = { onPanelSelected(HamburgerPanel.UserPersona) },
                expandableSubtitle = true
            )
            MenuRow(
                title = "场景设置",
                subtitle = if (conversation?.scene?.isNotBlank() == true)
                    conversation.scene.trim() else "未设置",
                onClick = { onPanelSelected(HamburgerPanel.Scene) },
                expandableSubtitle = true
            )
            MenuRow(
                title = "对话风格",
                subtitle = when (replyStyle) {
                    QuiddityConstants.REPLY_STYLE_CONCISE -> "简洁自然"
                    QuiddityConstants.REPLY_STYLE_DETAILED -> "细腻详细"
                    else -> "跟随人设（默认）"
                },
                onClick = { showReplyStyleDialog = true }
            )

            // 模型配置
            }
            MenuSectionCard(title = "模型配置") {
            MenuRow(
                title = "选择模型配置",
                subtitle = settings.catalog
                    .firstOrNull { it.id == (conversation?.apiCatalogId ?: settings.activeCatalogId) }
                    ?.let { "当前：${it.name} · ${it.apiModel}" } ?: "未选择",
                onClick = { onPanelSelected(HamburgerPanel.ApiSelector) },
                expandableSubtitle = true
            )
            MenuRow(
                title = "管理模型配置",
                subtitle = "添加、编辑或删除模型配置",
                onClick = { onPanelSelected(HamburgerPanel.ApiEditor) },
            )
            Spacer(modifier = Modifier.size(4.dp))
            val temperatureSubtitle = if (conversation?.temperature != null) {
                "本会话 " + String.format(java.util.Locale.US, "%.1f", conversation.temperature) +
                    " · 默认 " + String.format(java.util.Locale.US, "%.1f", settings.globalTemperature)
            } else {
                "跟随默认（" + String.format(java.util.Locale.US, "%.1f", settings.globalTemperature) + "）"
            }
            MenuRow(
                title = "采样温度",
                subtitle = temperatureSubtitle,
                onClick = { showTemperatureEditor = !showTemperatureEditor }
            )
            if (showTemperatureEditor) {
                TemperatureEditorPanel(
                    current = conversation?.temperature,
                    globalDefault = settings.globalTemperature,
                    onTemperatureChange = onTemperatureChange
                )
            }
            Spacer(modifier = Modifier.size(4.dp))
            WebSearchMenuRow(
                enabled = conversation?.webSearchEnabled == true,
                supported = webSearchSupported,
                currentModelId = currentModelId,
                onWebSearchChange = onWebSearchChange
            )

            }
            MenuSectionCard(title = "统计") {
            TokenStatsPanel(
                conversation = conversation,
                messages = messages,
                onContextLimitChange = onContextLimitChange,
                onResetContextLimit = onResetContextLimit
            )

            // 主动消息（对应算法文档 2.2 会话级开启）
            }
            MenuSectionCard(title = "主动消息") {
            ToggleMenuRow(
                title = "主动消息",
                subtitle = if (conversation?.activeMessageEnabled == true) {
                    "已开启：AI 按时间库主动发消息"
                } else {
                    "开启后立即生成当日时间库"
                },
                checked = conversation?.activeMessageEnabled == true,
                onCheckedChange = onActiveMessageChange,
            )
            // 系统条件引导：会话级开启后展示精确闹钟 / 电池优化状态与一键跳转
            if (conversation?.activeMessageEnabled == true) {
                Spacer(modifier = Modifier.size(8.dp))
                ActiveMessagePermissionCard(
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                MenuRow(
                    title = "查看时间库",
                    subtitle = "查看本会话今日时间库（按 AI 设定可能需要密码）",
                    onClick = onViewTimeLibrary,
                )
            }

            // 数据
            }
            MenuSectionCard(title = "数据") {
            MenuRow(
                title = "会话压缩",
                subtitle = if (conversation?.memoryBankEnabled == true) {
                    "已启用 · 每 ${conversation.memoryBankRounds} 轮压缩"
                } else {
                    "未启用（点击进入配置）"
                },
                onClick = onCompressionClick,
                trailingIcon = Icons.Filled.Compress,
                trailingTint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(4.dp))
            ExportImportCard(
                title = "人设卡",
                subtitle = "导出或导入当前会话的人设卡",
                onExport = onExportPersona,
                onImport = onImportPersona,
            )
            Spacer(modifier = Modifier.size(4.dp))
            ExportImportCard(
                title = "对话记录",
                subtitle = "导出或导入当前会话的全部数据",
                onExport = onExportConversation,
                onImport = onImportConversation,
            )
            Spacer(modifier = Modifier.size(4.dp))
            MenuRow(
                title = "查找聊天记录",
                subtitle = "按关键词搜索本会话的历史消息",
                onClick = onOpenSearchChat,
            )

            // 危险操作：清空设置放在最底部并加感叹号，以示区别
            Spacer(modifier = Modifier.size(16.dp))
            }
            MenuSectionCard(title = "危险操作") {
            MenuRow(
                title = "清空会话记录",
                subtitle = "删除全部消息并重置压缩对话（仅影响当前会话，保留人设/场景/记忆/壁纸）",
                onClick = onClearMessages,
                trailingIcon = Icons.Filled.DeleteSweep,
                trailingTint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.size(4.dp))
            MenuRow(
                title = "清空会话设置",
                subtitle = "重置当前会话的人设/用户/场景/记忆（不影响消息记录）",
                onClick = onClearSettings,
                trailingIcon = Icons.Filled.Warning,
                trailingTint = MaterialTheme.colorScheme.error
            )
            }
        }
    }

    // ===== 对话风格选择弹窗（用户自主权：表达方式由用户决定，默认完全跟随人设） =====
    if (showReplyStyleDialog) {
        val options = listOf(
            Triple(
                QuiddityConstants.REPLY_STYLE_FOLLOW_PERSONA,
                "跟随人设",
                "完全按 AI 人设中的性格与期望表达，不做额外限制（默认）"
            ),
            Triple(
                QuiddityConstants.REPLY_STYLE_CONCISE,
                "简洁自然",
                "回复尽量简短自然，像日常聊天"
            ),
            Triple(
                QuiddityConstants.REPLY_STYLE_DETAILED,
                "细腻详细",
                "回复充分展开，描写细腻、篇幅不限"
            )
        )
        AlertDialog(
            onDismissRequest = { showReplyStyleDialog = false },
            title = { Text("对话风格") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    options.forEach { (style, label, desc) ->
                        val selected = replyStyle == style
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onReplyStyleChange(style)
                                    showReplyStyleDialog = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReplyStyleDialog = false }) {
                    Text("完成")
                }
            }
        )
    }
}

@Composable
private fun MenuSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 2.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        content()
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun MenuRow(
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    expandableSubtitle: Boolean = false,
    trailingIcon: ImageVector? = null,
    trailingTint: androidx.compose.ui.graphics.Color? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
) {
    // Box 替代 Surface：行内无 elevation 需求，Box+background+clip 跳过 Surface 的 CompositionLocalProvider 开销
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleColor
                )
                if (subtitle.isNotEmpty()) {
                    if (expandableSubtitle) {
                        ExpandableText(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            maxCollapsedLines = 1
                        )
                    } else {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor
                        )
                    }
                }
            }
            Icon(
                imageVector = trailingIcon ?: Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = trailingTint ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ============================================================
// 群聊 1.5.0：群设置（群名称 / 上下文条数 N / 成员管理 / 停止模式）
// ============================================================

/**
 * 群聊主菜单（方案十：群名称、上下文条数 N、成员管理、停止模式 A/B；
 * 不含共享用户人设与时间库入口）。
 */
@Composable
private fun GroupMenuContent(
    conversation: Conversation?,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onContextLimit: () -> Unit,
    onStopModeChange: (String) -> Unit,
    onGroupBackground: () -> Unit,
    onWallpaper: () -> Unit,
    onManageMembers: () -> Unit,
    onSearchChat: () -> Unit,
    onClearMessages: () -> Unit,
    onDeleteConversation: () -> Unit,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "群聊设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close, "关闭",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MenuSectionCard(title = "群聊") {
                MenuRow(
                    title = "查找聊天记录",
                    subtitle = "搜索本群历史消息",
                    onClick = onSearchChat,
                    trailingIcon = Icons.Filled.ChevronRight
                )
                MenuRow(
                    title = "群聊背景 / 场景",
                    subtitle = conversation?.let { conv ->
                        if (conv.groupBackground.isNotBlank()) {
                            val label = if (conv.groupBackgroundMode == QuiddityConstants.GROUP_BACKGROUND_MODE_SCENE)
                                "场景" else "背景"
                            "$label：${conv.groupBackground.trim()}"
                        } else {
                            "未设置（可选）"
                        }
                    } ?: "未设置（可选）",
                    onClick = onGroupBackground,
                    expandableSubtitle = true
                )
                MenuRow(
                    title = "群名称",
                    subtitle = conversation?.title?.ifBlank { "新群聊" } ?: "新群聊",
                    onClick = onRename
                )
                MenuRow(
                    title = "上下文条数 N",
                    subtitle = "最近 ${conversation?.groupContextLimit ?: QuiddityConstants.GROUP_DEFAULT_CONTEXT_LIMIT} 条",
                    onClick = onContextLimit
                )
                val stopMode = conversation?.stopMode ?: QuiddityConstants.GROUP_DEFAULT_STOP_MODE
                ToggleMenuRow(
                    title = "停止模式",
                    subtitle = if (stopMode == QuiddityConstants.GROUP_STOP_MODE_A)
                        "A：只停止当前成员，排队的递补" else "B：停止时清空整个队列（默认）",
                    checked = stopMode == QuiddityConstants.GROUP_STOP_MODE_B,
                    onCheckedChange = { checked ->
                        onStopModeChange(
                            if (checked) QuiddityConstants.GROUP_STOP_MODE_B
                            else QuiddityConstants.GROUP_STOP_MODE_A
                        )
                    }
                )
            }
            MenuSectionCard(title = "外观") {
                ToggleMenuRow(
                    title = "深色模式",
                    subtitle = if (darkMode) "当前：暗色" else "当前：亮色",
                    checked = darkMode,
                    onCheckedChange = onDarkModeChange
                )
                MenuRow(
                    title = "群聊壁纸",
                    subtitle = if (conversation?.wallpaperUri != null) "已设置" else "未设置",
                    onClick = onWallpaper
                )
            }
            MenuSectionCard(title = "成员管理") {
                MenuRow(
                    title = "查看 / 添加 / 移除成员",
                    subtitle = "当前 ${conversation?.memberConversationIds?.size ?: 0}/3 个",
                    onClick = onManageMembers,
                    trailingIcon = Icons.Filled.ChevronRight
                )
            }
            MenuSectionCard(title = "危险操作") {
                MenuRow(
                    title = "清除聊天记录",
                    subtitle = "删除本群所有消息，不可恢复",
                    onClick = onClearMessages
                )
                MenuRow(
                    title = "删除该会话",
                    subtitle = "删除群聊及其全部消息，成员私聊不受影响",
                    onClick = onDeleteConversation,
                    titleColor = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 群名称编辑面板（方案十.1：群名称可随时改名）。
 */
@Composable
private fun GroupNamePanel(
    currentName: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "群名称",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("群名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.size(16.dp))
        TextButton(
            enabled = name.isNotBlank(),
            onClick = { onSave(name.trim()) }
        ) {
            Text("保存")
        }
    }
}

/**
 * 群聊上下文条数 N 编辑面板（方案六.2：默认 50，范围 1～200）。
 */
@Composable
private fun GroupContextLimitPanel(
    currentLimit: Int,
    onBack: () -> Unit,
    onSave: (Int) -> Unit
) {
    var limit by rememberSaveable { mutableIntStateOf(currentLimit) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "上下文条数 N",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = "群聊记录只取最近 N 条（1～200）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = limit > QuiddityConstants.GROUP_MIN_CONTEXT_LIMIT,
                onClick = { limit = (limit - 5).coerceAtLeast(QuiddityConstants.GROUP_MIN_CONTEXT_LIMIT) }
            ) { Text("−5") }
            TextButton(
                enabled = limit > QuiddityConstants.GROUP_MIN_CONTEXT_LIMIT,
                onClick = { limit = (limit - 1).coerceAtLeast(QuiddityConstants.GROUP_MIN_CONTEXT_LIMIT) }
            ) { Text("−1") }
            Text(
                text = limit.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            TextButton(
                enabled = limit < QuiddityConstants.GROUP_MAX_CONTEXT_LIMIT,
                onClick = { limit = (limit + 1).coerceAtMost(QuiddityConstants.GROUP_MAX_CONTEXT_LIMIT) }
            ) { Text("+1") }
            TextButton(
                enabled = limit < QuiddityConstants.GROUP_MAX_CONTEXT_LIMIT,
                onClick = { limit = (limit + 5).coerceAtMost(QuiddityConstants.GROUP_MAX_CONTEXT_LIMIT) }
            ) { Text("+5") }
        }
        Spacer(modifier = Modifier.size(16.dp))
        TextButton(onClick = { onSave(limit) }) { Text("保存") }
    }
}

/**
 * 群聊背景 / 场景编辑面板：背景（氛围 / 群规）与场景（多人情境）合并为一个设置项，
 * 单选其一开启；文本注入所有成员的回复提示词。清空输入并保存 = 移除。
 */
@Composable
private fun GroupBackgroundPanel(
    currentText: String,
    currentMode: String,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(currentText) }
    var mode by rememberSaveable { mutableStateOf(currentMode) }
    val isScene = mode == QuiddityConstants.GROUP_BACKGROUND_MODE_SCENE
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "群聊背景 / 场景",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { mode = QuiddityConstants.GROUP_BACKGROUND_MODE_BACKGROUND }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.RadioButton(
                    selected = !isScene,
                    onClick = { mode = QuiddityConstants.GROUP_BACKGROUND_MODE_BACKGROUND }
                )
                Text(
                    text = "背景 / 群规",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { mode = QuiddityConstants.GROUP_BACKGROUND_MODE_SCENE }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.RadioButton(
                    selected = isScene,
                    onClick = { mode = QuiddityConstants.GROUP_BACKGROUND_MODE_SCENE }
                )
                Text(
                    text = "场景",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(if (isScene) "场景描述" else "背景 / 群规") },
            placeholder = {
                Text(
                    if (isScene) {
                        "例如：你们几个朋友正在一场篝火晚会上，夜空晴朗，周围是树林"
                    } else {
                        "例如：这是大学同学群，关系很熟，说话随意，偶尔互怼"
                    }
                )
            },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = if (isScene) {
                "多人场景的情境描述，会注入到每个成员回复时的提示词里；清空后保存即移除。"
            } else {
                "氛围与群规描述，会注入到每个成员回复时的提示词里；清空后保存即移除。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(16.dp))
        TextButton(onClick = { onSave(text, mode) }) {
            Text("保存")
        }
    }
}

/**
 * 成员添加流程状态机（多弹窗稳定切换：同一时刻只显示一个弹窗；
 * 新增 API 底部面板作为覆盖层叠加在配置弹窗之上）。
 */
private sealed interface MemberAddFlow {
    data object None : MemberAddFlow
    data object Selecting : MemberAddFlow
    data class Result(
        val passedCount: Int,
        val failed: List<Pair<Conversation, String>>
    ) : MemberAddFlow
    data class Config(
        val member: Conversation,
        val failed: List<Pair<Conversation, String>>
    ) : MemberAddFlow
}

/**
 * 群聊成员管理面板（方案十.4-5 + 需求）：
 * 显示当前成员头像（通过的加入后立即显示）；不足 3 个显示加号按钮（添加成员）；
 * 单个 API 有问题时通过的正常加入，未通过的进入弹窗重试/配置（可现场新增 API）。
 */
@Composable
private fun GroupMemberManagePanel(
    group: Conversation,
    viewModel: ChatViewModel,
    settings: com.quiddity.app.data.model.AppSettings,
    onBack: () -> Unit
) {
    var addFlow by remember { mutableStateOf<MemberAddFlow>(MemberAddFlow.None) }
    var showApiCreateSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conversationRepo = com.quiddity.app.di.ServiceLocator.conversationRepository
    val settingsRepo = com.quiddity.app.di.ServiceLocator.settingsRepository
    val apiCatalogManager = com.quiddity.app.di.ServiceLocator.apiCatalogManager

    val members = remember(group.memberConversationIds) {
        group.memberConversationIds.mapNotNull { id ->
            conversationRepo.getConversation(id)
        }
    }
    val soloList = remember(settings.catalog) {
        conversationRepo.conversations.value
            .filter { it.type == ConversationType.SOLO }
    }

    /** 提交添加：通过的正常加入（显示头像），未通过的进入结果弹窗。 */
    fun submitAdd(ids: List<String>) {
        if (ids.isEmpty()) {
            addFlow = MemberAddFlow.None
            return
        }
        viewModel.addGroupMembers(ids) { _, failures ->
            val failedPairs = failures.mapNotNull { (id, reason) ->
                conversationRepo.getConversation(id)?.let { it to reason }
            }
            val passedCount = ids.size - failures.size
            if (failures.isEmpty()) {
                android.widget.Toast.makeText(
                    context,
                    "已添加 $passedCount 个成员",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                addFlow = MemberAddFlow.None
            } else {
                addFlow = MemberAddFlow.Result(passedCount, failedPairs)
            }
        }
    }

    /** 配置成员 API 后重新校验；通过则从失败列表移除。 */
    fun configureMemberAndRevalidate(member: Conversation, catalogId: String?) {
        scope.launch {
            val updated = member.copy(apiCatalogId = catalogId)
            conversationRepo.updateConversation(updated)
            val result = conversationRepo.validateGroupMember(updated)
            if (result.isSuccess) {
                android.widget.Toast.makeText(
                    context,
                    "${member.persona.name.ifBlank { "成员" }} 配置成功，已加入",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                val remaining = (addFlow as? MemberAddFlow.Config)
                    ?.failed
                    ?.filterNot { it.first.id == member.id }
                    .orEmpty()
                addFlow = if (remaining.isEmpty()) MemberAddFlow.None
                else MemberAddFlow.Result(0, remaining)
            } else {
                android.widget.Toast.makeText(
                    context,
                    "仍未通过：${result.exceptionOrNull()?.message ?: "校验失败"}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "成员管理",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))

        Text(
            text = "当前成员（${members.size}/${QuiddityConstants.GROUP_MAX_MEMBERS}）",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(members, key = { it.id }) { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AiAvatar(
                            avatarUri = member.persona.aiAvatarUri,
                            name = member.persona.name,
                            size = 40.dp
                        )
                    }
                    Spacer(modifier = Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = member.persona.name.ifBlank { "未命名 AI" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "模型：${member.apiCatalogId?.let { id ->
                                settings.catalog.firstOrNull { c -> c.id == id }?.apiModel
                            } ?: "跟随全局"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        enabled = members.size > 1,
                        onClick = { viewModel.removeGroupMember(member.id) }
                    ) {
                        Text(
                            text = "移除",
                            color = if (members.size > 1) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
            if (members.size < QuiddityConstants.GROUP_MAX_MEMBERS) {
                item(key = "add_member") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { addFlow = MemberAddFlow.Selecting }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = "添加成员",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    when (val flow = addFlow) {
        is MemberAddFlow.Selecting -> {
            AddGroupMembersDialog(
                soloList = soloList,
                currentIds = group.memberConversationIds,
                hasApiConfig = settings.catalog.isNotEmpty(),
                onConfirm = { ids -> submitAdd(ids) },
                onDismiss = { addFlow = MemberAddFlow.None }
            )
        }
        is MemberAddFlow.Result -> {
            MemberAddResultDialog(
                passedCount = flow.passedCount,
                failed = flow.failed,
                onRetry = { submitAdd(flow.failed.map { it.first.id }) },
                onConfig = { member ->
                    addFlow = MemberAddFlow.Config(member, flow.failed)
                },
                onDone = { addFlow = MemberAddFlow.None }
            )
        }
        is MemberAddFlow.Config -> {
            MemberApiConfigDialog(
                member = flow.member,
                catalog = settings.catalog,
                currentId = flow.member.apiCatalogId,
                onSelect = { catalogId -> configureMemberAndRevalidate(flow.member, catalogId) },
                onAddNew = { showApiCreateSheet = true },
                onRetry = {
                    scope.launch {
                        val updated = conversationRepo.getConversation(flow.member.id)
                            ?: return@launch
                        val result = conversationRepo.validateGroupMember(updated)
                        if (result.isSuccess) {
                            android.widget.Toast.makeText(
                                context,
                                "${updated.persona.name.ifBlank { "成员" }} 校验通过，已加入",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            val remaining = flow.failed.filterNot { it.first.id == updated.id }
                            addFlow = if (remaining.isEmpty()) MemberAddFlow.None
                            else MemberAddFlow.Result(0, remaining)
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "仍未通过：${result.exceptionOrNull()?.message ?: "校验失败"}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onBack = { addFlow = MemberAddFlow.Result(0, flow.failed) }
            )
        }
        else -> Unit
    }

    // 新增模型配置底部面板（叠加在配置弹窗之上，保存后回到配置弹窗继续选择）
    if (showApiCreateSheet) {
        ApiEditBottomSheet(
            initial = null,
            catalogManager = apiCatalogManager,
            testConnection = { url, key, model ->
                apiCatalogManager.testConnection(url, key, model)
            },
            onDismiss = { showApiCreateSheet = false },
            onSave = { state ->
                scope.launch {
                    runCatching {
                        val entry = apiCatalogManager.buildEntry(
                            id = state.id,
                            name = state.name,
                            providerId = state.providerId,
                            apiUrl = state.apiUrl,
                            apiModel = state.apiModel,
                            apiKey = state.apiKey
                        )
                        settingsRepo.upsertCatalog(entry)
                    }.onFailure {
                        android.util.Log.e("GroupMemberManagePanel", "新增模型配置失败", it)
                        android.widget.Toast.makeText(
                            context,
                            "新增模型配置失败：${it.message ?: "未知错误"}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                showApiCreateSheet = false
            }
        )
    }
}

/**
 * 成员添加结果弹窗（需求）：通过的已加入并显示头像；未通过的逐条列出原因，
 * 可整体重试或对单个成员进入配置。
 */
@Composable
private fun MemberAddResultDialog(
    passedCount: Int,
    failed: List<Pair<Conversation, String>>,
    onRetry: () -> Unit,
    onConfig: (Conversation) -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("添加成员结果") },
        text = {
            Column {
                Text(
                    text = if (passedCount > 0) "已加入 $passedCount 个成员（头像已显示）" else "没有成员通过校验",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (failed.isNotEmpty()) {
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = "以下 ${failed.size} 个未通过：",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(failed, key = { it.first.id }) { (member, reason) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.persona.name.ifBlank { member.title },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                TextButton(onClick = { onConfig(member) }) {
                                    Text("配置", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) { Text("完成") }
        },
        dismissButton = {
            if (failed.isNotEmpty()) {
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    )
}

/**
 * 成员模型配置弹窗（需求：未配置 API 的成员可直接在此页面配置）。
 * 选择已有配置 / 使用默认（跟随全局）/ 新增配置；配置后自动重新校验。
 */
@Composable
private fun MemberApiConfigDialog(
    member: Conversation,
    catalog: List<com.quiddity.app.data.model.ApiCatalogEntry>,
    currentId: String?,
    onSelect: (String?) -> Unit,
    onAddNew: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("配置 ${member.persona.name.ifBlank { "成员" }} 的模型") },
        text = {
            Column {
                Text(
                    text = "成员回复使用各自私聊的模型配置，选择后自动重新校验：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(catalog, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelect(entry.id) }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = entry.id == currentId,
                                onClick = { onSelect(entry.id) }
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = entry.apiModel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item(key = "use_default") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelect(null) }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = currentId == null,
                                onClick = { onSelect(null) }
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "使用默认（跟随全局激活配置）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    item(key = "add_new") {
                        TextButton(
                            onClick = onAddNew,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ 新增模型配置")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBack) { Text("返回") }
        },
        dismissButton = {
            TextButton(onClick = onRetry) { Text("重试") }
        }
    )
}

/**
 * 添加群聊成员弹窗（方案十.5）：私聊列表勾选，最多选到 3 个，
 * 确定后执行 API 测试，通过的角色加入，未通过的返回通知可重试。
 */
@Composable
private fun AddGroupMembersDialog(
    soloList: List<Conversation>,
    currentIds: List<String>,
    hasApiConfig: Boolean,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val candidates = soloList.filter { it.id !in currentIds }
    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加成员") },
        text = {
            if (candidates.isEmpty()) {
                Text(
                    text = "没有可添加的私聊会话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(candidates, key = { it.id }) { conv ->
                        val checked = conv.id in selected
                        val status = when {
                            !hasApiConfig -> "API 未配置"
                            conv.userPersona.name.isBlank() -> "用户名未设置"
                            conv.persona.name.isBlank() -> "AI 名未设置"
                            else -> "可加入"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { toggle(conv.id) }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { toggle(conv.id) })
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                            ) {
                                AiAvatar(
                                    avatarUri = conv.persona.aiAvatarUri,
                                    name = conv.persona.name,
                                    size = 36.dp
                                )
                            }
                            Spacer(modifier = Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.title.ifBlank { "未命名会话" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "AI：${conv.persona.name.ifBlank { "未设置" }} · $status",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (status == "可加入") MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                if (selected.size + currentIds.size > QuiddityConstants.GROUP_MAX_MEMBERS) {
                    Text(
                        text = "最多 ${QuiddityConstants.GROUP_MAX_MEMBERS} 个成员",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty() &&
                    (currentIds.size + selected.size) <= QuiddityConstants.GROUP_MAX_MEMBERS,
                onClick = { onConfirm(selected.toList()) }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 带开关的设置行。
 *
 * 与 [MenuRow] 不同，本组件右侧显示一个带动画的 Toggle Switch，
 * 用于需要明确展示"开/关"状态并可直接切换的项（如深色模式）。
 * 点击整行或拖动开关均可触发 [onCheckedChange]。
 */
@Composable
private fun ToggleMenuRow(
    title: String,
    subtitle: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    // Box 替代 Surface：行内无 elevation 需求，Box+background+clip 跳过 Surface 的 CompositionLocalProvider 开销
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow
            )
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCheckedChange(!checked) }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        }
                    )
                }
            }
            QuiddityToggleSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

/**
 * 会话级采样温度编辑面板：预设快捷档 + 0～2 滑杆 + 跟随默认重置。
 * 官方文档：DeepSeek 思考模式下 temperature 不生效。
 */
@Composable
private fun TemperatureEditorPanel(
    current: Double?,
    globalDefault: Double,
    onTemperatureChange: (Double?) -> Unit
) {
    val effective = current ?: globalDefault
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "采样温度",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (current != null) {
                    TextButton(onClick = { onTemperatureChange(null) }) {
                        Text("跟随默认")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                temperaturePresets.forEach { (value, label) ->
                    TemperaturePresetChip(
                        value = value,
                        label = label,
                        selected = kotlin.math.abs(effective - value) < 0.001,
                        onClick = { onTemperatureChange(value) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            TemperatureSlider(
                value = effective,
                onValueChangeFinished = { onTemperatureChange(it) }
            )
            Text(
                text = "范围 0～2，DeepSeek 官方默认 1.0；思考模式下温度不生效。\n" +
                    "0.0 代码/数学 · 1.0 数据抽取 · 1.3 通用对话/翻译 · 1.5 创意写作",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/** 官方场景建议档位（值 → 展示标签）。 */
private val temperaturePresets = listOf(
    0.0 to "0.0 严谨",
    1.0 to "1.0 均衡",
    1.3 to "1.3 对话",
    1.5 to "1.5 创意"
)

@Composable
private fun TemperaturePresetChip(
    value: Double,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * DeepSeek 官方服务端联网搜索开关行。
 * 仅官方服务商 + 支持模型可用（由 [supported] 判定）；不支持时整行禁用并说明原因。
 */
@Composable
private fun WebSearchMenuRow(
    enabled: Boolean,
    supported: Boolean,
    currentModelId: String,
    onWebSearchChange: (Boolean) -> Unit
) {
    ToggleMenuRow(
        title = "官方联网搜索",
        subtitle = if (supported) {
            if (enabled) {
                "已开启：DeepSeek 服务端搜索，无需第三方引擎"
            } else {
                "DeepSeek 官方服务端联网搜索"
            }
        } else {
            "仅 DeepSeek 官方 " + QuiddityConstants.DEEPSEEK_RESPONSES_MODEL +
                " 支持（当前：$currentModelId）"
        },
        checked = enabled,
        enabled = supported,
        onCheckedChange = { onWebSearchChange(it) }
    )
}

/**
 * 导出/导入卡片：标题、说明文字、以及「导出」「导入」两个按钮。
 *
 */
@Composable
private fun ExportImportCard(
    title: String,
    subtitle: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    // Box 替代 Surface：无 elevation 需求，Box+background+clip 跳过 Surface 开销
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                ) { Text("导出") }
                TextButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f)
                ) { Text("导入") }
            }
        }
    }
}
