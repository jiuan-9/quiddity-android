package com.quiddity.app.active

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.quiddity.app.R
import com.quiddity.app.di.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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



/**
 * 主动消息前台服务：承载闹钟触发后的 LLM 决策与发送（对应算法文档 5.2）。
 *
 * - 闹钟触发时 App 可能处于后台甚至已被系统回收，前台服务保证进程在执行
 *   决策调用（可能持续数秒）期间不被系统杀死
 * - 流程：解析 extras → 确保会话数据已加载 → 调用
 *   [com.quiddity.app.data.repo.TimeLibraryRepository.onAlarmTriggered] →
 *   结束后立即 [stopSelf]
 * - 前台服务类型 dataSync：短时同步任务，Android 14 下 6 小时超时对本次
 *   秒级任务无影响
 */
class ActiveMessageService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runningJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val convId = intent?.getStringExtra(AlarmScheduler.EXTRA_CONV_ID)
        val time = intent?.getStringExtra(AlarmScheduler.EXTRA_TIME) ?: ""
        if (convId == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        runningJob = scope.launch {
            try {
                // 进程被闹钟拉起时 Application.onCreate 已初始化 ServiceLocator，
                // 但会话数据异步加载可能尚未完成，这里主动确保加载就绪
                ServiceLocator.conversationRepository.loadAll()
                ServiceLocator.timeLibraryRepository.onAlarmTriggered(convId, time)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.e("ActiveMessageService", "主动消息处理失败", t)
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runningJob?.cancel()
        scope.cancel()
        super.onDestroy()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "主动消息", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("主动消息")
            .setContentText("正在处理定时消息…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "active_message"
        private const val NOTIFICATION_ID = 1001
    }
}
