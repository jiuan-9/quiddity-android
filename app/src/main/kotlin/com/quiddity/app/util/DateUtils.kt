package com.quiddity.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 日期格式化工具。
 */
object DateUtils {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun formatTimestamp(ts: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = ts }

        val isToday = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        val isYesterday = run {
            val y = now.clone() as Calendar
            y.add(Calendar.DAY_OF_YEAR, -1)
            y.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                    y.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
        }

        return when {
            isToday -> timeFormat.format(Date(ts))
            isYesterday -> "昨天"
            now.get(Calendar.YEAR) == target.get(Calendar.YEAR) -> dateFormat.format(Date(ts))
            else -> dateTimeFormat.format(Date(ts))
        }
    }
}
