package com.quiddity.app

import android.app.Application
import android.app.Activity
import android.os.Bundle
import android.content.res.Configuration
import com.quiddity.app.data.local.SettingsStore
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.util.CrashLogger
import com.quiddity.app.active.ReplyOverlayController

/**
 * Quiddity 应用入口。
 * 负责初始化 [ServiceLocator]，提供全局依赖访问点。
 * 同时安装全局崩溃日志记录器，便于排查无日志闪退问题。
 *
 * - 在 onCreate 最早期同步读取 darkMode 镜像（SharedPreferences）
 * - 强制覆盖应用配置的 UI 模式，确保 values-night 资源限定符生效
 * - 这样 Android 12+ 系统在 Activity 启动前渲染 splash 时使用的就是正确的暗/亮色主题
 * - 强制锁定 fontScale=1.0f：保证所有 sp 字体在不同设备/不同系统字体设置下渲染一致
 *   （部分 OEM 默认 fontScale≠1.0，用户系统设置也会改；锁定后视觉与设计稿完全一致）
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
        registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                private var resumedCount = 0

                override fun onActivityResumed(activity: Activity) {
                    resumedCount++
                    ReplyOverlayController.setAppVisible(resumedCount > 0)
                }

                override fun onActivityPaused(activity: Activity) {
                    resumedCount = (resumedCount - 1).coerceAtLeast(0)
                    ReplyOverlayController.setAppVisible(resumedCount > 0)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    /**
     * 重写 attachBaseContext：把 Configuration 里的 fontScale / densityDpi 强制改回设计值。
     * 这是所有 Android 跨设备显示一致方案的标准做法——在 Context 体系创建之前生效，
     * 所有 sp / dp 计算都基于这个 Configuration，避免 OEM 定制或用户系统设置导致的视觉漂移。
     */
    override fun attachBaseContext(base: android.content.Context) {
        val config = Configuration(base.resources.configuration)
        // 锁定字体缩放 1.0：所有 sp 值按设计尺寸渲染，不受系统字号影响
        config.fontScale = 1.0f
        // 不锁定 densityDpi：屏幕物理密度应保持原样，否则文字会模糊
        val ctx = base.createConfigurationContext(config)
        super.attachBaseContext(ctx)
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
        // 同步锁定 fontScale（attachBaseContext 已在更早阶段生效，这里只是为了运行时保险）
        config.fontScale = 1.0f
        config.uiMode = if (darkMode) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 系统配置变化（如用户在系统设置里改了字体大小）时强制覆盖回 1.0
        val patched = Configuration(newConfig)
        patched.fontScale = 1.0f
        @Suppress("DEPRECATION")
        resources.updateConfiguration(patched, resources.displayMetrics)
    }

    companion object {
        @Volatile
        lateinit var instance: QuiddityApp
            private set
    }
}
