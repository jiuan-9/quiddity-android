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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
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
import com.quiddity.app.ui.components.ApiCatalogEditFormState
import com.quiddity.app.ui.components.ConfirmDialog
import com.quiddity.app.util.DateUtils
import com.quiddity.app.ui.components.ExpandableText
import com.quiddity.app.ui.components.QuiddityToggleSwitch
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
    onJumpToMessage: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val apiCatalogManager = remember { ServiceLocator.apiCatalogManager }
    val currentTier = remember(conversation) { viewModel.resolveCurrentTier() }

    // Android 13+ 需要通知权限：开启主动消息时一并请求，保证到点能弹通知
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    // 子面板导航状态
    var currentPanel by remember { mutableStateOf<HamburgerPanel?>(null) }
    var pendingClearSettings by remember { mutableStateOf(false) }
    var pendingClearMessages by remember { mutableStateOf(false) }
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
                ) { panel ->
                    when (panel) {
                        null -> MainMenuContent(
                            conversation = conversation,
                            messages = messages,
                            currentTier = currentTier,
                            settings = settings,
                            hasWallpaper = hasWallpaper,
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
                            onOpenSearchChat = { currentPanel = HamburgerPanel.SearchChat }
                        )
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
                                    onGenerate = { description, selectedTier ->
                                        viewModel.quickSetupGenerate(description, selectedTier)
                                    },
                                    onApply = { rawText, selectedTier ->
                                        viewModel.applyQuickSetupResult(rawText, selectedTier)
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
        ConfirmDialog(
            title = "清空会话记录（含压缩对话）",
            message = "将删除当前会话的所有消息，并重置压缩记忆（compressedMemory / lastCompressedAtRound）。AI 人设 / 用户 / 场景 / 记忆 / 壁纸保留。此操作不可撤销。",
            confirmText = "清空",
            onConfirm = {
                viewModel.clearConversationMessages()
                pendingClearMessages = false
                toastMsg = "会话记录已清空"
            },
            onDismiss = { pendingClearMessages = false }
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
    onClick: () -> Unit
) {
    val isUser = message.role == Role.USER
    val name = if (isUser) {
        "我"
    } else {
        conversation?.persona?.name?.ifBlank { conversation.title } ?: "AI"
    }
    val avatarUri = if (isUser) null else conversation?.persona?.aiAvatarUri

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
                    text = message.content.replace("\n", " ").trim().let {
                        if (it.length > 80) it.take(80) + "…" else it
                    },
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
    Wallpaper, Compression, SearchChat
}

// ==================== 主菜单 ====================

@Composable
private fun MainMenuContent(
    conversation: Conversation?,
    messages: List<com.quiddity.app.data.model.Message>,
    currentTier: com.quiddity.app.domain.ApiCatalogManager.ModelTier,
    settings: com.quiddity.app.data.model.AppSettings,
    hasWallpaper: Boolean,
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
    onOpenSearchChat: () -> Unit
) {
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
            SectionHeader("外观")
            // 会话内的"外观"只是全局主题的一个快捷入口，状态直接来自 settings.darkMode，
            // 切换时调用 SettingsViewModel.setDarkMode，与设置面板中的总开关保持一致。
            ToggleMenuRow(
                title = "深色模式",
                subtitle = if (darkMode) "当前：暗色" else "当前：亮色",
                checked = darkMode,
                onCheckedChange = onDarkModeChange,
                hasWallpaper = hasWallpaper
            )
            // - 仅对当前会话生效，不影响其他会话
            // - 持久化到 conversation.wallpaperUri
            MenuRow(
                title = "会话壁纸",
                subtitle = if (conversation?.wallpaperUri != null) "已设置" else "未设置",
                onClick = { onPanelSelected(HamburgerPanel.Wallpaper) },
                hasWallpaper = hasWallpaper
            )

            // 人设
            SectionHeader("人设")
            MenuRow(
                title = "快速设定",
                subtitle = "描述你想要的人设，AI 一次性生成并填入",
                onClick = { onPanelSelected(HamburgerPanel.QuickSetup) },
                hasWallpaper = hasWallpaper,
                expandableSubtitle = true
            )
            val aiPersonaName = conversation?.persona?.name
            val aiPersonaSet = !aiPersonaName.isNullOrBlank()
            MenuRow(
                title = "AI 人设",
                subtitle = if (aiPersonaSet) "AI: $aiPersonaName" else "未设置",
                onClick = { onPanelSelected(HamburgerPanel.Persona) },
                hasWallpaper = hasWallpaper,
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
                hasWallpaper = hasWallpaper,
                expandableSubtitle = true
            )
            MenuRow(
                title = "场景设置",
                subtitle = if (conversation?.scene?.isNotBlank() == true)
                    conversation.scene.trim() else "未设置",
                onClick = { onPanelSelected(HamburgerPanel.Scene) },
                hasWallpaper = hasWallpaper,
                expandableSubtitle = true
            )

            // 模型配置
            SectionHeader("模型配置")
            MenuRow(
                title = "选择模型配置",
                subtitle = settings.catalog
                    .firstOrNull { it.id == (conversation?.apiCatalogId ?: settings.activeCatalogId) }
                    ?.let { "当前：${it.name} · ${it.apiModel}" } ?: "未选择",
                onClick = { onPanelSelected(HamburgerPanel.ApiSelector) },
                hasWallpaper = hasWallpaper,
                expandableSubtitle = true
            )
            MenuRow(
                title = "管理模型配置",
                subtitle = "添加、编辑或删除模型配置",
                onClick = { onPanelSelected(HamburgerPanel.ApiEditor) },
                hasWallpaper = hasWallpaper
            )

            SectionHeader("统计")
            TokenStatsPanel(
                conversation = conversation,
                messages = messages,
                onContextLimitChange = onContextLimitChange,
                onResetContextLimit = onResetContextLimit
            )

            // 主动消息（对应算法文档 2.2 会话级开启）
            SectionHeader("主动消息")
            ToggleMenuRow(
                title = "主动消息",
                subtitle = if (conversation?.activeMessageEnabled == true) {
                    "已开启：AI 按时间库主动发消息"
                } else {
                    "开启后立即生成当日时间库"
                },
                checked = conversation?.activeMessageEnabled == true,
                onCheckedChange = onActiveMessageChange,
                hasWallpaper = hasWallpaper
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
                    hasWallpaper = hasWallpaper
                )
            }

            // 数据
            SectionHeader("数据")
            MenuRow(
                title = "会话压缩",
                subtitle = if (conversation?.memoryBankEnabled == true) {
                    "已启用 · 每 ${conversation.memoryBankRounds} 轮压缩"
                } else {
                    "未启用（点击进入配置）"
                },
                onClick = onCompressionClick,
                hasWallpaper = hasWallpaper,
                trailingIcon = Icons.Filled.Compress,
                trailingTint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(4.dp))
            ExportImportCard(
                title = "人设卡",
                subtitle = "导出或导入当前会话的人设卡",
                onExport = onExportPersona,
                onImport = onImportPersona,
                hasWallpaper = hasWallpaper
            )
            Spacer(modifier = Modifier.size(4.dp))
            ExportImportCard(
                title = "对话记录",
                subtitle = "导出或导入当前会话的全部数据",
                onExport = onExportConversation,
                onImport = onImportConversation,
                hasWallpaper = hasWallpaper
            )
            Spacer(modifier = Modifier.size(4.dp))
            MenuRow(
                title = "查找聊天记录",
                subtitle = "按关键词搜索本会话的历史消息",
                onClick = onOpenSearchChat,
                hasWallpaper = hasWallpaper
            )

            // 危险操作：清空设置放在最底部并加感叹号，以示区别
            Spacer(modifier = Modifier.size(16.dp))
            SectionHeader("危险操作")
            MenuRow(
                title = "清空会话记录",
                subtitle = "删除全部消息并重置压缩对话（仅影响当前会话，保留人设/场景/记忆/壁纸）",
                onClick = onClearMessages,
                hasWallpaper = hasWallpaper,
                trailingIcon = Icons.Filled.DeleteSweep,
                trailingTint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.size(4.dp))
            MenuRow(
                title = "清空会话设置",
                subtitle = "重置当前会话的人设/用户/场景/记忆（不影响消息记录）",
                onClick = onClearSettings,
                hasWallpaper = hasWallpaper,
                trailingIcon = Icons.Filled.Warning,
                trailingTint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun MenuRow(
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    hasWallpaper: Boolean = false,
    expandableSubtitle: Boolean = false,
    trailingIcon: ImageVector? = null,
    trailingTint: androidx.compose.ui.graphics.Color? = null,
    subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
) {
    // Box 替代 Surface：行内无 elevation 需求，Box+background+clip 跳过 Surface 的 CompositionLocalProvider 开销
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                // 玻璃层级区分：壁纸存在时卡片用更低透明度，与主面板形成层次
                if (hasWallpaper) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface
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
                    color = MaterialTheme.colorScheme.onSurface
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
    onCheckedChange: (Boolean) -> Unit,
    hasWallpaper: Boolean = false
) {
    // Box 替代 Surface：行内无 elevation 需求，Box+background+clip 跳过 Surface 的 CompositionLocalProvider 开销
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (hasWallpaper) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            QuiddityToggleSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/**
 * 导出/导入卡片：标题、说明文字、以及「导出」「导入」两个按钮。
 *
 * @param hasWallpaper 壁纸存在时使用更低透明度，形成玻璃层级区分
 */
@Composable
private fun ExportImportCard(
    title: String,
    subtitle: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    hasWallpaper: Boolean = false
) {
    // Box 替代 Surface：无 elevation 需求，Box+background+clip 跳过 Surface 开销
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                // 玻璃层级区分：壁纸存在时卡片用更低透明度
                if (hasWallpaper) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceContainerLow
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
