package com.quiddity.app.active

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.quiddity.app.MainActivity
import com.quiddity.app.R

/**
 * Agent 定时提醒广播：AlarmManager 到点触发，直接推送通知栏提醒。
 */
class AgentReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "Agent 提醒"
        val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
        postNotification(context, title, text)
    }

    private fun postNotification(context: Context, title: String, text: String) {
        runCatching {
            NotificationChannels.ensure(
                context,
                CHANNEL_ID,
                "Agent 提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val pending = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text.replace("\n", " ").trim().let {
                    if (it.length > 80) it.take(80) + "…" else it
                })
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            // 用标题+文本派生稳定且唯一的通知 ID：多条不同提醒同时到点时不互相覆盖，
            // 同一条提醒（相同标题文本）仍走 ID 复用，更新而非重复堆叠。
            val notificationId = (title + "|" + text).hashCode() and Int.MAX_VALUE
            manager.notify(notificationId, notification)
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.quiddity.app.action.AGENT_REMINDER"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        private const val CHANNEL_ID = "agent_reminder"
    }
}
