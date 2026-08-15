package com.quiddity.app.ui.chat.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.ExportPayload
import com.quiddity.app.data.model.ImportMode
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.chat.components.panels.PersonaPanel
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.ConversationCodec
import com.quiddity.app.util.IdGenerator
import kotlinx.coroutines.launch


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
    onJumpToMessage: ((String) -> Unit)? = null,
    // Agent 模式：AI 人设行改为「选择角色」（角色库点选），不进入 PersonaPanel 编辑表单
    onPersonaOverride: (() -> Unit)? = null,
    // 上层覆盖层（如选择角色面板）打开时禁用菜单 BackHandler，避免抢先消费返回键
    backHandlerEnabled: Boolean = true
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
    var pendingExportFormat by remember { mutableStateOf<ExportFormatPicker?>(null) }
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
    BackHandler(enabled = visible && backHandlerEnabled) {
        if (currentPanel != null) {
            currentPanel = null
        } else {
            onDismiss()
        }
    }


    val launchers = rememberHamburgerFileLaunchers(
        context = context,
        scope = scope,
        conversation = conversation,
        settings = settings,
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        onToast = { toastMsg = it },
        onKeyRefill = { pendingKeyRefill = it },
        onImportPayload = { pendingImportPayload = it }
    )
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
                    // 毛玻璃半透明面板：透出壁纸，保留文字可读
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
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
                    HamburgerPanelHost(
                        panel = panel,
                        conversation = conversation,
                        messages = messages,
                        settings = settings,
                        viewModel = viewModel,
                        settingsViewModel = settingsViewModel,
                        apiCatalogManager = apiCatalogManager,
                        currentTier = currentTier,
                        webSearchSupported = webSearchSupported,
                        currentModelId = currentModelId,
                        context = context,
                        notificationPermissionLauncher = notificationPermissionLauncher,
                        launchers = launchers,
                        onPanelSelected = { currentPanel = it },
                        onDismiss = onDismiss,
                        onJumpToMessage = onJumpToMessage,
                        onPersonaOverride = onPersonaOverride,
                        onToast = { toastMsg = it },
                        onRequestExportFormat = { pendingExportFormat = ExportFormatPicker() },
                        onRequestClearSettings = { pendingClearSettings = true },
                        onRequestClearMessages = { pendingClearMessages = true },
                        onRequestDeleteConversation = { pendingDeleteConversation = true }
                    )
                }
            }
        }
    }

    HamburgerConfirmDialogs(
        pendingClearSettings = pendingClearSettings,
        pendingClearMessages = pendingClearMessages,
        pendingDeleteConversation = pendingDeleteConversation,
        pendingKeyRefill = pendingKeyRefill,
        isGroupConversation = conversation?.type == ConversationType.GROUP,
        onClearSettings = {
            viewModel.clearConversationSettings()
            pendingClearSettings = false
        },
        onClearMessages = {
            viewModel.clearConversationMessages()
            pendingClearMessages = false
            toastMsg = "???????"
        },
        onDeleteConversation = {
            pendingDeleteConversation = false
            onDeleteConversation()
        },
        onDismiss = {
            pendingClearSettings = false
            pendingClearMessages = false
            pendingDeleteConversation = false
            pendingKeyRefill = null
        }
    )

    TimeLibraryViewFlow(
        step = timeLibraryViewStep,
        passwordInput = timeLibraryPasswordInput,
        passwordError = timeLibraryPasswordError,
        conversation = conversation,
        viewModel = viewModel,
        onStepChange = { timeLibraryViewStep = it },
        onPasswordInputChange = { timeLibraryPasswordInput = it },
        onPasswordErrorChange = { timeLibraryPasswordError = it }
    )

    ExportFormatDialog(
        pending = pendingExportFormat,
        onDismiss = { pendingExportFormat = null },
        onSelect = { format ->
            pendingExportFormat = null
            val idPart = IdGenerator.newUuid()
            when (format) {
                ConversationCodec.Format.JSON -> {
                    launchers.jsonExport.launch("quiddity-conversation-$idPart.json")
                }
                ConversationCodec.Format.MARKDOWN -> {
                    launchers.markdownExport.launch("quiddity-conversation-$idPart.md")
                }
                ConversationCodec.Format.TEXT -> {
                    launchers.textExport.launch("quiddity-conversation-$idPart.txt")
                }
            }
        }
    )

    ImportMergeDialog(
        payload = pendingImportPayload,
        onDismiss = { pendingImportPayload = null },
        onMerge = { p ->
            scope.launch {
                settingsViewModel.importAllPayload(p, mode = ImportMode.MERGE)
                toastMsg = "?????????"
            }
        },
        onCharactersOnly = { p ->
            scope.launch {
                settingsViewModel.importAllPayload(p, mode = ImportMode.CHARACTERS_ONLY)
                toastMsg = "??????"
            }
        },
        onReplace = { p ->
            scope.launch {
                settingsViewModel.importAllPayload(p, mode = ImportMode.REPLACE)
                toastMsg = "?????????"
            }
        }
    )
}

internal enum class HamburgerPanel {
    QuickSetup, Persona, UserPersona, Scene, ApiSelector, ApiEditor,
    Wallpaper, Compression, SearchChat, TimeLibrary,
    GroupName, GroupContextLimit, GroupMembers, GroupBackground
}
