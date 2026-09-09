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
    // 查看时间库流程：0=关闭 1=展示内容
    var timeLibraryViewStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(visible) {
        if (!visible) {
            currentPanel = null
        }
    }

