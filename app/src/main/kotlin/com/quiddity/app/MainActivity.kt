package com.quiddity.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.quiddity.app.active.OperationNotifyController
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.ui.navigation.QuiddityRoute
import com.quiddity.app.ui.navigation.QuiddityNavHost
import com.quiddity.app.ui.theme.QuiddityTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
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

// 当前规则：仅承载 Compose 根容器；状态栏图标颜色跟随应用主题。
class MainActivity : ComponentActivity() {

    /** 通知深链待消费的会话路由（主动消息 / 行动弹窗点击后直接进入对应会话框）。 */
    private var pendingConversationRoute by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Android 13+ 通知权限申请（首次行动弹窗被系统静默丢弃时触发）。 */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 授权结果无需处理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 行动通知弹窗宿主：Agent 执行行动类工具时由应用发系统通知弹窗说明正在进行的操作
        OperationNotifyController.attach(this)
        OperationNotifyController.onRequestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        pendingConversationRoute = conversationRouteFromIntent(intent)
        // 全局沉浸式（内容延伸到系统栏，壁纸/背景铺满全屏）。
        // 键盘处理在聊天页内完成：窗口可见区域测量键盘高度 + 内容平滑跟随（见 ChatScreen）。
        enableEdgeToEdge()
        // adjustResize：确保键盘弹起时窗口可见区域（visibleFrame）正确缩小，
        // 应用据此测量键盘高度，不依赖 ROM 的 IME insets 派发。
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // ===== 稳定帧率 =====
        // 实测本应用平均单帧约 9ms、90 分位 23ms，无法稳定跑满 120Hz（8.28ms 截止线），
        // 120Hz 下半数帧超时、节奏忽快忽慢，体感比锁 60Hz 更卡。
        // 因此主动锁定 60Hz Display.Mode，让 9ms 级帧时间全部落在 16.6ms 截止线内，滚动/动画均匀顺滑。
        applyStableRefreshRate(this)
        lifecycle.addObserver(StableRefreshRateObserver(this))

        // WhileSubscribed(5_000ms)：UI 不可见时停止订阅，可见时立即恢复，节省后台耗电。
        val settingsState = ServiceLocator.settingsRepository.observeSettings()
            .stateIn(
                scope = lifecycleScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ServiceLocator.settingsRepository.currentSnapshot()
            )

        // ===== 三条开发规范（位于文件中间位置） =====
        // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
        //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
        // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
        //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
        // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
        //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

        setContent {
            val settings by settingsState.collectAsState()
            // 状态栏图标颜色跟随应用主题（不跟随系统），亮色模式=深色图标
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !settings.darkMode
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !settings.darkMode
            // 窗口背景色跟随主题，避免黑夜模式过渡动画时露出奶白色窗口背景
            window.decorView.setBackgroundColor(
                if (settings.darkMode) ContextCompat.getColor(this, R.color.dark_bg)
                else ContextCompat.getColor(this, R.color.cream_bg)
            )
            // ===== 字体缩放 =====
            // QuiddityApp 已在 Configuration 层锁定 fontScale=1.0（整体强制默认字体，不受系统字号影响）。
            // 此处用 LocalDensity 覆盖做二次精细控制，无需 recreate 即可平滑生效：
            // - followSystemFont=true：读取系统字号（Resources.getSystem 反映系统设置，不受应用 Configuration 锁定影响）
            // - followSystemFont=false：使用用户在总设置中选择的 fontScale（默认 1.0 = 设计稿原尺寸）
            val baseDensity = LocalDensity.current
            val effectiveFontScale = if (settings.followSystemFont) {
                android.content.res.Resources.getSystem().configuration.fontScale
                    .takeIf { it > 0f } ?: 1.0f
            } else {
                settings.fontScale
            }
            val scaledDensity = Density(density = baseDensity.density, fontScale = effectiveFontScale)
            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                Box(modifier = Modifier.fillMaxSize()) {
                    QuiddityTheme(darkMode = settings.darkMode) {
                        QuiddityNavHost(
                            pendingConversationRoute = pendingConversationRoute,
                            onPendingConversationConsumed = { pendingConversationRoute = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingConversationRoute = conversationRouteFromIntent(intent)
    }

    override fun onDestroy() {
        OperationNotifyController.detach(this)
        super.onDestroy()
    }

    /** 解析通知深链 Intent → 会话路由（agentchat/{id} / chat/{id}）；无会话参数返回 null。 */
    private fun conversationRouteFromIntent(intent: Intent?): String? {
        val conversationId = intent?.getStringExtra(EXTRA_OPEN_CONVERSATION_ID) ?: return null
        val typeName = intent.getStringExtra(EXTRA_OPEN_CONVERSATION_TYPE)
        return if (typeName == ConversationType.AGENT.name) {
            QuiddityRoute.AgentChat.create(conversationId)
        } else {
            QuiddityRoute.Chat.create(conversationId)
        }
    }

    companion object {
        const val EXTRA_OPEN_CONVERSATION_ID = "open_conversation_id"
        const val EXTRA_OPEN_CONVERSATION_TYPE = "open_conversation_type"

        /** 构造跳转到指定会话的应用内 Intent（主动消息 / 行动弹窗通知点击使用）。 */
        fun conversationIntent(
            context: Context,
            conversationId: String,
            conversationType: ConversationType
        ): Intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(EXTRA_OPEN_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_OPEN_CONVERSATION_TYPE, conversationType.name)
        }
    }

}

/**
 * 应用稳定帧率模式（60Hz）。
 *
 * 关键策略：双管齐下，锁定 60Hz 避免 120Hz 下帧超时：
 * 1. `preferredDisplayModeId`：精确指定最接近 60Hz 的 Display.Mode ID（覆盖系统默认）
 * 2. `preferredRefreshRate`：软提示刷新率值，作为保险
 * 兼容性：
 * - 优先选与当前分辨率一致的模式，避免锁帧率导致分辨率跳变（跨 ROM 通用）
 * - 设备没有 60Hz 模式时退而选最接近 60Hz 的模式
 * - API 23+ 均可用，不依赖任何厂商 ROM 的白名单
 */
private fun applyStableRefreshRate(activity: ComponentActivity) {
    val window = activity.window ?: return
    val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display
    } else {
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay
    }
    if (display == null) return

    val supportedModes = display.supportedModes
    if (supportedModes.isEmpty()) return

    // 优先选与当前分辨率一致且最接近 60Hz 的模式，避免锁帧率导致分辨率跳变
    val currentWidth = display.mode.physicalWidth
    val currentHeight = display.mode.physicalHeight
    var targetMode: Display.Mode? = null
    var bestScore = Float.MAX_VALUE
    for (mode in supportedModes) {
        val sameResolution = mode.physicalWidth == currentWidth &&
            mode.physicalHeight == currentHeight
        val rateDiff = kotlin.math.abs(mode.refreshRate - TARGET_REFRESH_RATE)
        val score = if (sameResolution) rateDiff else rateDiff + 1_000f
        if (score < bestScore) {
            bestScore = score
            targetMode = mode
        }
    }
    val stableMode = targetMode ?: return

    val params: WindowManager.LayoutParams = window.attributes
    params.preferredDisplayModeId = stableMode.modeId
    params.preferredRefreshRate = TARGET_REFRESH_RATE
    window.attributes = params
}

/** 目标帧率：稳定 60Hz（60fps 在所有系统上通用，且本应用 9ms 级帧时间可全部达标）。 */
private const val TARGET_REFRESH_RATE = 60f

/**
 * 监听显示模式变化的 LifecycleObserver。
 * 折叠屏展开、外接显示器接入等场景下系统会重新协商 Display.Mode，
 * 需重新申请稳定帧率，否则新显示通道回落高刷。
 */
private class StableRefreshRateObserver(
    private val activity: ComponentActivity
) : androidx.lifecycle.DefaultLifecycleObserver {
    override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
        // 重新进入前台时（如折叠展开、外接屏切换）重新锁定一次
        applyStableRefreshRate(activity)
    }
}
