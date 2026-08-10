package com.quiddity.app.active

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.quiddity.app.domain.agent.AgentNotification
import com.quiddity.app.domain.agent.AgentSensorState

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

/**
 * 通知读取监听服务（只读，V1 不做通知回复/清除）：
 * 收到通知时把快照写入 [AgentSensorState]，供 Agent 只读工具使用。
 * 内容仅保存在进程内。
 */
class NotificationBridge : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        refreshSnapshots()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshSnapshots()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshSnapshots()
    }

    override fun onDestroy() {
        isConnected = false
        AgentSensorState.updateNotifications(emptyList())
        super.onDestroy()
    }

    private fun refreshSnapshots() {
        val items = runCatching {
            activeNotifications
                .mapNotNull { it.toAgentNotification() }
                .sortedByDescending { it.timestamp }
                .take(MAX_SNAPSHOT)
        }.getOrDefault(emptyList())
        AgentSensorState.updateNotifications(items)
    }

    private fun StatusBarNotification.toAgentNotification(): AgentNotification? {
        val extras = notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return AgentNotification(
            packageName = packageName,
            title = title,
            text = text,
            timestamp = postTime
        )
    }

    companion object {
        private const val MAX_SNAPSHOT = 100

        @Volatile
        var isConnected: Boolean = false
            private set

        /** 系统通知使用权设置中是否已启用本包名。 */
        fun isServiceEnabled(context: Context): Boolean {
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }
    }
}
