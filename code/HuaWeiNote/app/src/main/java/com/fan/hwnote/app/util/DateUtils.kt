package com.fan.hwnote.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val weekdayTimeFmt = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())

    /**
     * 列表卡片右上角时间显示规则：
     * - 同一天：HH:mm（如 14:30）
     * - 昨天：昨天 HH:mm
     * - 7 天内：周X HH:mm
     * - 更早：yyyy/MM/dd
     */
    fun formatRelative(timeMs: Long, now: Long = System.currentTimeMillis()): String {
        val target = Calendar.getInstance().apply { timeInMillis = timeMs }
        val current = Calendar.getInstance().apply { timeInMillis = now }
        val sameDay = target.get(Calendar.YEAR) == current.get(Calendar.YEAR)
            && target.get(Calendar.DAY_OF_YEAR) == current.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return timeFmt.format(Date(timeMs))

        // 昨天判定：把 current 减 1 天看 day_of_year
        val yesterday = (current.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = target.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)
            && target.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) return "昨天 " + timeFmt.format(Date(timeMs))

        val diffDays = (now - timeMs) / (24L * 60 * 60 * 1000)
        if (diffDays in 0..6) return weekdayTimeFmt.format(Date(timeMs))

        return dateFmt.format(Date(timeMs))
    }
}
