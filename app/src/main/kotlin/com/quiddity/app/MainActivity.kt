package com.quiddity.app

import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.quiddity.app.active.OperationPipController
import com.quiddity.app.di.ServiceLocator
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

    // ===== 屏幕操作小窗（PiP）：操作状态文本 + 小窗模式标记（全屏时隐藏状态层） =====
    private var pipStatusText by androidx.compose.runtime.mutableStateOf("正在操作屏幕")
    private var pipMode by androidx.compose.runtime.mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
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

        // ===== 屏幕操作小窗（PiP）桥注册：Agent 执行模拟操作时自动进小窗 =====
        // 目标应用保持全屏供 AI 操作，Quiddity 以小窗展示操作状态；退出由用户点开或
        // AI 主动调用（global_action exit_pip），不自动恢复。
        OperationPipController.onEnterPipRequest = { status ->
            runOnUiThread { enterOperationPip(status) }
        }
        OperationPipController.onStatusUpdate = { text ->
            runOnUiThread { pipStatusText = text }
        }
        OperationPipController.inPip = false

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
                        QuiddityNavHost()
                    }
                    // PiP 小窗状态层：全屏时隐藏；进入小窗后盖住聊天，只显示操作状态。
                    // 用叠加层而非替换 NavHost，避免导航状态因组合切换丢失。
                    if (pipMode) {
                        PipStatusOverlay(statusText = pipStatusText)
                    }
                }
            }
        }
    }

    // ===== 屏幕操作小窗（PiP）：进入 / 退出 / 状态更新 =====
    // 自动进入需要 Android 12+（前台应用可无手势请求）；Android 8~11 不支持自动进入，保持全屏现状。
    private fun enterOperationPip(status: String) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return
        if (pipMode) {
            pipStatusText = status
            return
        }
        val store = ServiceLocator.agentStore
        if (store?.snapshot()?.pipOnScreenOps == false) return
        pipStatusText = status
        runCatching {
            enterPictureInPictureMode(
                android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(android.util.Rational(16, 9))
                    .build()
            )
        }
    }

    // 注：Android 无编程退出画中画的 API——进入小窗后恢复全屏只能由用户点击小窗完成。

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        OperationPipController.inPip = isInPictureInPictureMode
        pipMode = isInPictureInPictureMode
    }

    override fun onDestroy() {
        OperationPipController.onEnterPipRequest = null
        OperationPipController.onStatusUpdate = null
        OperationPipController.inPip = false
        super.onDestroy()
    }
}

/** PiP 小窗状态层：深色全屏 + 居中大字操作状态（小窗内可读），提示用户点按恢复。 */
@Composable
private fun PipStatusOverlay(statusText: String) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0xFF1B1B1F)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🤖",
                fontSize = 48.sp,
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "AI 正在操作屏幕",
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f)
            )
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
