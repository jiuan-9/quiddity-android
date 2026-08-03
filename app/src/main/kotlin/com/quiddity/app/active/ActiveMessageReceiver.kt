package com.quiddity.app.active

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

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
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
