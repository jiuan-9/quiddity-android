package com.quiddity.app.active

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.quiddity.app.R

/**
 * 通知渠道统一创建入口（模块边界注释：通知渠道注册）。
 *
 * 应用 minSdk = 26，所有通知都必须先注册渠道，否则 Android 8+ 会静默丢弃
 * （悬浮窗回复、Agent 行动弹窗、定时提醒、主动消息通知全部依赖本入口）。
 * 各发送方在首次弹通知前调用一次，重复调用幂等；
 * [buildForeground] 统一构建前台服务通知（文案/优先级/图标一致，仅渠道不同）。
 */
object NotificationChannels {
    fun ensure(context: Context, channelId: String, channelName: String, importance: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, channelName, importance)
            )
        }
    }

    /** 构建前台服务通知：统一文案/样式（仅渠道不同）。文案约定（1.6.0）：处理期间统一「潮水无声，静待回荡」，完成后自动消失。 */
    fun buildForeground(context: Context, channelId: String): Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("潮水无声")
            .setContentText("静待回荡")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
