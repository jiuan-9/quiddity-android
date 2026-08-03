package com.quiddity.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
 * 日期格式化工具。
 */
object DateUtils {

    // DateTimeFormatter 不可变且线程安全，可安全地跨线程共享
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormat = DateTimeFormatter.ofPattern("MM-dd")
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val searchDateFormatter = DateTimeFormatter.ofPattern("yy.M.d")

    /** 24 小时制时刻（HH:mm），用于气泡旁的时间小字。 */
    fun formatTime(ts: Long): String =
        Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormat)

    /** 聊天记录搜索结果的完整时间：今天/昨天/26.8.3 + 24 小时时刻。 */
    fun formatSearchTime(ts: Long): String {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        val now = LocalDate.now(zone)
        val time = Instant.ofEpochMilli(ts).atZone(zone).toLocalTime().format(timeFormat)
        return when (date) {
            now -> "今天 $time"
            now.minusDays(1) -> "昨天 $time"
            else -> "${date.format(searchDateFormatter)} $time"
        }
    }

    fun formatTimestamp(ts: Long): String {
        val zone = ZoneId.systemDefault()
        val now = LocalDate.now(zone)
        val targetDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        val targetTime = Instant.ofEpochMilli(ts).atZone(zone).toLocalDateTime()

        return when {
            targetDate == now -> targetTime.format(timeFormat)
            targetDate == now.minusDays(1) -> "昨天"
            targetDate.year == now.year -> targetDate.format(dateFormat)
            else -> targetTime.format(dateTimeFormat)
        }
    }
}
