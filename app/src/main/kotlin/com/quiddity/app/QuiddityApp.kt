package com.quiddity.app

import android.app.Application
import android.content.res.Configuration
import com.quiddity.app.data.local.SettingsStore
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.util.CrashLogger

/**
 * Quiddity 应用入口。
 * 负责初始化 [ServiceLocator]，提供全局依赖访问点。
 * 同时安装全局崩溃日志记录器，便于排查无日志闪退问题。
 *
 * - 在 onCreate 最早期同步读取 darkMode 镜像（SharedPreferences）
 * - 强制覆盖应用配置的 UI 模式，确保 values-night 资源限定符生效
 * - 这样 Android 12+ 系统在 Activity 启动前渲染 splash 时使用的就是正确的暗/亮色主题
 */
class QuiddityApp : Application() {
    override fun onCreate() {
        // 在 super.onCreate() 之前同步读取 darkMode 并覆盖 UI 模式配置
        // 确保 splash 屏幕使用与用户主题设置一致的颜色
        val darkMode = SettingsStore.readDarkModeSync(this)
        applyUiMode(darkMode)

        super.onCreate()
        instance = this
        CrashLogger.install(this)
        ServiceLocator.init(this)
    }

    /**
     * 强制应用 UI 模式配置。
     *
     * Android 资源系统通过 Configuration.uiMode 决定使用 values 还是 values-night 资源。
     * 默认跟随系统设置，但本应用暗色模式由用户控制，因此需要强制覆盖：
     * - darkMode=true → UI_MODE_NIGHT_YES → 使用 values-night 资源
     * - darkMode=false → UI_MODE_NIGHT_NO → 使用 values 资源
     *
     * 注意：此方法在 Application.onCreate 中调用，影响整个应用进程的资源解析。
     * 后续 Activity 创建时会继承此配置，splash 屏幕也会使用正确的暗/亮色主题。
     */
    private fun applyUiMode(darkMode: Boolean) {
        val config = Configuration(resources.configuration)
        config.uiMode = if (darkMode) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    companion object {
        @Volatile
        lateinit var instance: QuiddityApp
            private set
    }
}
