package com.quiddity.app.active

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.quiddity.app.domain.TimeLibraryEngine
import java.util.Calendar

/**
 * 主动消息本地闹钟调度器（对应算法文档 5.1 触发方式）。
 *
 * - 使用 Android AlarmManager 定时唤醒，仅依赖手机本地时间，不依赖网络时间
 * - 即使 App 被从后台清除，闹钟仍可唤醒应用进程（受系统省电策略影响）
 * - 每个会话同一时刻只注册一个闹钟（最近的下一个 pending 时间点），
 *   触发处理后由 [com.quiddity.app.data.repo.TimeLibraryRepository] 重新调度下一个
 * - Android 12+ 未授予"闹钟和提醒"精确闹钟权限时自动降级为非精确闹钟
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        /** 主动消息闹钟触发广播 action。 */
        const val ACTION_ALARM = "com.quiddity.app.action.ACTIVE_MESSAGE_ALARM"
        /** 会话 ID extra。 */
        const val EXTRA_CONV_ID = "conv_id"
        /** 触发时间点（"HH:mm"）extra。 */
        const val EXTRA_TIME = "time"
    }

    /**
     * 注册指定时间点的闹钟。
     * - 时间点解析失败或已过期（不晚于当前时间）时不注册
     * - 同一会话的新闹钟会覆盖旧闹钟（requestCode 仅由会话 ID 派生）
     */
    fun schedule(convId: String, time: String) {
        val minutes = TimeLibraryEngine.parseMinutes(time) ?: return
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= System.currentTimeMillis()) return
        val pendingIntent = buildPendingIntent(convId, time)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }

    /**
     * 注销该会话的所有定时闹钟（对应算法文档 八、任务注销）。
     */
    fun cancelAll(convId: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(convId),
            buildIntent(convId, ""),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildIntent(convId: String, time: String): Intent =
        Intent(context, ActiveMessageReceiver::class.java)
            .setAction(ACTION_ALARM)
            .putExtra(EXTRA_CONV_ID, convId)
            .putExtra(EXTRA_TIME, time)

    private fun buildPendingIntent(convId: String, time: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(convId),
            buildIntent(convId, time),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun requestCode(convId: String): Int =
        ("active_message_" + convId).hashCode() and Int.MAX_VALUE
}
