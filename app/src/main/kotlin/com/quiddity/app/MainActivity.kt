package com.quiddity.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.ui.navigation.QuiddityNavHost
import com.quiddity.app.ui.theme.QuiddityTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// 当前规则：仅承载 Compose 根容器；状态栏图标颜色跟随应用主题。
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                if (settings.darkMode) android.graphics.Color.parseColor("#1A1A1A")
                else android.graphics.Color.parseColor("#FAF9F6")
            )
            QuiddityTheme(darkMode = settings.darkMode) {
                QuiddityNavHost()
            }
        }
    }
}
