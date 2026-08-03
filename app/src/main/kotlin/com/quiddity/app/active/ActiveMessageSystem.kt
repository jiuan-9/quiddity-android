package com.quiddity.app.active

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

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
 * 主动消息系统条件检测与跳转（对应算法文档 5.1 触发方式的可靠性保障）。
 *
 * 解决的问题：
 * - Android 12+ 未授予"闹钟和提醒"权限时，精确闹钟自动降级为非精确，
 *   触发可能延迟数分钟；本类提供状态检测与一键跳转授权。
 * - 国产 ROM 强杀后台 / 省电策略拦截时，闹钟到点不触发；本类提供电池优化
 *   白名单检测与一键跳转系统设置引导。
 *
 * 所有检测均为轻量系统 API 调用（无 IO），可安全地在 UI 组合期直接读取。
 */
object ActiveMessageSystem {

    /**
     * 精确闹钟是否可用。
     * Android 12（API 31）以下系统闹钟默认精确；API 31+ 需用户授予
     * "闹钟和提醒"（SCHEDULE_EXACT_ALARM）权限后才可注册精确闹钟。
     */
    fun exactAlarmGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    /**
     * 本应用是否已被系统"电池优化"放行（忽略电池优化）。
     * 未放行时国产 ROM 可能强杀后台进程，导致闹钟到点无法唤醒。
     */
    fun batteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 跳转到电池优化白名单设置：优先打开针对本应用的"不限制"授权页
     * （部分 ROM 直接提供开关），失败时回退到系统电池优化列表。
     */
    fun openBatteryOptimizationSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }.onFailure {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }
    }

    /**
     * 跳转到"闹钟和提醒"精确闹钟授权页（仅 Android 12+ 需要；
     * Android 11 及以下精确闹钟默认可用，无需跳转）。
     */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        }
    }
}
