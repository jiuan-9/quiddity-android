package com.quiddity.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
