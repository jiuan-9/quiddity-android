package com.quiddity.app.active

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quiddity.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机广播接收器：设备重启后 AlarmManager 中的闹钟全部丢失，
 * 在此依据当前状态重新注册（对应算法文档 七、状态重置 与 5.1 触发方式）。
 *
 * - 使用 goAsync 保持接收器活跃，直至本地重置与重注册完成
 * - 仅执行本地操作（读写会话文件 + 注册闹钟），无需前台服务
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        // goAsync 窗口内只做轻量本地操作：加载会话 + 每日重置 + 重注册闹钟，
        // 避免等待网络型 LLM 的当天库补齐，确保 finish() 不被系统回收打断
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ServiceLocator.conversationRepository.loadAll()
                ServiceLocator.timeLibraryRepository.onAppStartLight()
            } finally {
                pendingResult.finish()
            }
        }
        // 需要网络/耗时的当天库补齐：放到不占用 goAsync 窗口的独立协程执行
        CoroutineScope(Dispatchers.Default).launch {
            ServiceLocator.timeLibraryRepository.refreshLibrariesForEnabledSessions()
        }
    }
}
