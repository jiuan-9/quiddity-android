package com.quiddity.app.active

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Agent 任务前台服务（1.6.0）：承载 Agent 流式回复期间的后台存活保障。
 *
 * 解决的问题：
 * - Agent 执行多轮工具调用可能持续数十秒，期间用户切到后台时进程可能被系统回收，
 *   导致回复中断、通知栏残留半成品状态；
 * - 本服务在 Agent 回复开始时启动（Activity 可见时发起，Android 12+ 允许），
 *   结束后立即 [stopSelf]——期间进程保持存活，回复在后台也能完整跑完。
 *
 * 通知表现（Android 8+ 一致）：
 * - 统一使用「潮水无声 / 静待回荡」文案（与主动消息前台服务同一约定）；
 * - 渠道 IMPORTANCE_LOW：静默无打扰，不响铃不震动；
 * - 前台服务类型 dataSync：短时同步任务（秒级，Android 14 的 6 小时超时无影响）。
 */
class AgentTaskService : Service() {

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 兜底自检：长时间无人显式停止时自动停止（防泄漏；正常由外部 stop() 提前停止）。 */
    private val leakGuard = Runnable { stopSelf() }

    override fun onDestroy() {
        mainHandler.removeCallbacks(leakGuard)
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        // 关键：startForegroundService 后【绝不能立即 stopSelf】——
        // 系统在 5 秒超时检查时若发现服务已停止，会抛
        // ForegroundServiceDidNotStartInTimeException 并杀死整个进程（1.6.2 崩溃根因）。
        // 服务生命周期由外部 [stop] 显式控制（任务结束即停）；
        // 兜底防泄漏：5 分钟无人停止则自动停止。
        mainHandler.removeCallbacks(leakGuard)
        mainHandler.postDelayed(leakGuard, LEAK_GUARD_DELAY_MS)
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        ensureNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        NotificationChannels.ensure(
            this,
            CHANNEL_ID,
            "Agent 任务",
            NotificationManager.IMPORTANCE_LOW
        )
    }

    private fun buildNotification(): Notification =
        NotificationChannels.buildForeground(this, CHANNEL_ID)

    companion object {
        private const val CHANNEL_ID = "agent_task"
        private const val NOTIFICATION_ID = 2001

        /** 兜底自检延迟：5 分钟（正常任务秒级~分钟级，外部停止更早到达）。 */
        private const val LEAK_GUARD_DELAY_MS = 5 * 60 * 1000L

        /** 停止延迟：覆盖 startForegroundService 的服务创建窗口（防超时竞态崩溃）。 */
        private const val STOP_DELAY_MS = 2500L

        /** 启动 Agent 任务前台服务（幂等：已运行时不再重复启动）。 */
        fun start(context: Context) {
            if (running) return
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AgentTaskService::class.java)
                )
                running = true
            }.onFailure {
                running = false
            }
        }

        /**
         * 停止 Agent 任务前台服务（幂等）。
         *
         * 关键时序（1.6.2 崩溃修复）：startForegroundService 是异步跨进程调用，
         * 若在服务创建完成（onStartCommand 执行 startForeground）之前立刻 stopService，
         * 系统会在 5 秒超时检查时抛 ForegroundServiceDidNotStartInTimeException 杀死进程。
         * 因此停止操作延迟 [STOP_DELAY_MS] 执行；若期间新任务重新 start（running 回到 true），
         * 延迟停止自动放弃（服务继续为新任务保活）。
         */
        fun stop(context: Context) {
            if (!running) return
            running = false
            // 延迟停止：覆盖 startForegroundService 的服务创建窗口（防超时竞态崩溃）；
            // 若期间新任务重新 start（running 回到 true），本次停止自动放弃
            stopHandler.postDelayed({
                if (!running) {
                    runCatching {
                        context.stopService(Intent(context, AgentTaskService::class.java))
                    }.onFailure {
                        // 服务可能已被系统回收：忽略，无需恢复状态
                    }
                }
            }, STOP_DELAY_MS)
        }

        private val stopHandler = android.os.Handler(android.os.Looper.getMainLooper())

        @Volatile
        private var running = false
    }
}
