package com.quiddity.app.active

import android.app.Notification
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.quiddity.app.domain.agent.AgentNotificationEvent
import com.quiddity.app.domain.agent.AgentNotification
import com.quiddity.app.domain.agent.AgentSensorState

/**
 * 通知读取监听服务（只读，V1 不做通知回复/清除）：
 * 收到通知时把快照写入 [AgentSensorState]，供 Agent 只读工具使用。
 * 内容仅保存在进程内。
 */
class NotificationBridge : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        isConnected = true
        refreshSnapshots()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { AgentSensorState.appendNotificationEvent(it.toAgentEvent("posted")) }
        refreshSnapshots()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { AgentSensorState.appendNotificationEvent(it.toAgentEvent("removed")) }
        refreshSnapshots()
    }

    override fun onDestroy() {
        instance = null
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

    private fun StatusBarNotification.toAgentEvent(type: String): AgentNotificationEvent {
        val extras = notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return AgentNotificationEvent(
            packageName = packageName,
            title = title,
            text = text,
            timestamp = System.currentTimeMillis(),
            type = type
        )
    }

    companion object {
        private const val MAX_SNAPSHOT = 100

        @Volatile
        private var instance: NotificationBridge? = null

        @Volatile
        var isConnected: Boolean = false
            private set

        /** 清除指定包名 / 通知 ID 的通知（id 为空时清除该包名的第一条）。 */
        fun cancelNotification(pkg: String, id: Int?): String {
            val bridge = instance ?: return "通知清除需要先开启通知使用权"
            val target = runCatching {
                bridge.activeNotifications.firstOrNull { sbn ->
                    sbn.packageName == pkg && (id == null || sbn.id == id)
                }
            }.getOrNull() ?: return "未找到 $pkg 的匹配通知"
            bridge.cancelNotification(target.key)
            return "已清除通知：${target.packageName}（ID ${target.id}）"
        }

        /** 清除所有可见通知。 */
        fun cancelAllNotifications(): String {
            val bridge = instance ?: return "通知清除需要先开启通知使用权"
            bridge.cancelAllNotifications()
            return "已清除全部通知"
        }

        /** 回复通知：优先走 RemoteInput 内联回复，目标不支持时回退为点击通知打开应用。 */
        fun replyNotification(pkg: String, id: Int?, reply: String): String {
            val bridge = instance ?: return "回复通知需要先开启通知使用权"
            val target = runCatching {
                bridge.activeNotifications.firstOrNull { sbn ->
                    sbn.packageName == pkg && (id == null || sbn.id == id)
                }
            }.getOrNull() ?: return "未找到 $pkg 的匹配通知"
            val notification = target.notification
            val replyAction = notification?.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }
            if (replyAction != null) {
                val sent = runCatching {
                    val remoteInputs = replyAction.remoteInputs ?: emptyArray()
                    val intent = Intent().apply { addFlags(Intent.FLAG_RECEIVER_FOREGROUND) }
                    val results = Bundle().apply {
                        remoteInputs.firstOrNull()?.let { putCharSequence(it.resultKey, reply) }
                    }
                    RemoteInput.addResultsToIntent(remoteInputs, intent, results)
                    replyAction.actionIntent.send(bridge, 0, intent)
                    true
                }.getOrDefault(false)
                if (sent) return "已回复通知：${target.packageName}（ID ${target.id}）"
            }
            val tapped = runCatching {
                notification?.contentIntent?.send()
                true
            }.getOrDefault(false)
            return if (tapped) {
                "该通知不支持内联回复，已点击打开应用：${target.packageName}"
            } else {
                "回复通知失败：该通知既无内联回复，也无法点击打开"
            }
        }

        /** 系统通知使用权设置中是否已启用本包名。 */
        fun isServiceEnabled(context: Context): Boolean {
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }
    }
}
