package com.quiddity.app.active

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 主动消息闹钟触发广播接收器（对应算法文档 5.2 触发入口）。
 *
 * - 收到 [AlarmScheduler.ACTION_ALARM] 后，将会话 ID 与触发时间点转交
 *   [ActiveMessageService]（前台服务）承载 LLM 网络请求，避免在 onReceive 中
 *   执行耗时操作导致 ANR
 * - 进程被闹钟拉起时 [com.quiddity.app.QuiddityApp.onCreate] 必然已执行
 *   ServiceLocator 初始化，因此直接启动服务即可
 */
class ActiveMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_ALARM) return
        val convId = intent.getStringExtra(AlarmScheduler.EXTRA_CONV_ID) ?: return
        val time = intent.getStringExtra(AlarmScheduler.EXTRA_TIME) ?: return
        val serviceIntent = Intent(context, ActiveMessageService::class.java)
            .putExtra(AlarmScheduler.EXTRA_CONV_ID, convId)
            .putExtra(AlarmScheduler.EXTRA_TIME, time)
        // Android 12+：未授予「闹钟和提醒」精确闹钟权限时会降级为非精确闹钟，
        // 后台以此拉起进程再启动前台服务会抛 ForegroundServiceStartNotAllowedException，
        // 导致进程被杀、主动消息丢失。这里用 runCatching 防护：失败仅记日志，不崩进程。
        runCatching {
            ContextCompat.startForegroundService(context, serviceIntent)
        }.onFailure {
            android.util.Log.e("ActiveMessageReceiver", "后台启动前台服务失败，主动消息本次未能触发", it)
        }
    }
}
