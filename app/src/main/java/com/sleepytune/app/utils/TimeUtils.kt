package com.sleepytune.app.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {
    
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    
    // 格式化时间 (HH:mm)
    fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }
    
    // 格式化时间 (Date -> HH:mm)
    fun formatTime(date: Date): String {
        return timeFormat.format(date)
    }
    
    // 格式化日期 (yyyy-MM-dd)
    fun formatDate(date: Date): String {
        return dateFormat.format(date)
    }
    
    // 格式化日期时间 (yyyy-MM-dd HH:mm)
    fun formatDateTime(date: Date): String {
        return dateTimeFormat.format(date)
    }
    
    // 格式化时长 (毫秒 -> mm:ss)
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    // 格式化时长 (毫秒 -> HH:mm:ss)
    fun formatDurationLong(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    // 计算距离下次闹钟的时间
    fun calculateTimeUntilAlarm(hour: Int, minute: Int, repeatDays: Set<Int>?): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        if (repeatDays == null || repeatDays.isEmpty()) {
            // 一次性闹钟
            if (calendar.timeInMillis <= now) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            return calendar.timeInMillis - now
        }
        
        // 重复闹钟
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val currentDayValue = if (currentDayOfWeek == Calendar.SUNDAY) 7 else currentDayOfWeek - 1
        
        var minDiff = Long.MAX_VALUE
        
        for (day in repeatDays) {
            var dayDiff = day - currentDayValue
            if (dayDiff < 0) dayDiff += 7
            
            val alarmTime = calendar.timeInMillis + dayDiff * 24 * 60 * 60 * 1000L
            val diff = alarmTime - now
            
            if (diff > 0 && diff < minDiff) {
                minDiff = diff
            }
        }
        
        return if (minDiff == Long.MAX_VALUE) 0 else minDiff
    }
    
    // 格式化剩余时间 (毫秒 -> "X小时Y分钟" 或 "X分钟" 或 "X秒")
    fun formatRemainingTime(millis: Long): String {
        val totalMinutes = (millis / 60000).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val seconds = ((millis % 60000) / 1000).toInt()
        
        return when {
            hours > 0 -> "${hours}小时${minutes}分钟"
            minutes > 0 -> "${minutes}分钟"
            else -> "${seconds}秒"
        }
    }
    
    // 计算下一个 5 分钟整点（新建闹钟的默认时间用）：14:07 → 14:10，14:59 → 15:00
    fun nextFiveMinuteMark(): Pair<Int, Int> {
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.MINUTE)
        val nextMinute = ((minute / 5) + 1) * 5
        return if (nextMinute < 60) {
            Pair(now.get(Calendar.HOUR_OF_DAY), nextMinute)
        } else {
            val hour = now.get(Calendar.HOUR_OF_DAY) + 1
            Pair(if (hour == 24) 0 else hour, 0)
        }
    }

    // 获取星期几的中文名称
    fun getDayName(day: Int): String {
        return when (day) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            7 -> "周日"
            else -> ""
        }
    }
    
    // 获取重复日的描述文本
    fun getRepeatDaysDescription(repeatDays: Set<Int>?): String {
        if (repeatDays == null || repeatDays.isEmpty()) {
            return "仅一次"
        }
        if (repeatDays == setOf(1, 2, 3, 4, 5, 6, 7)) {
            return "每天"
        }
        if (repeatDays == setOf(1, 2, 3, 4, 5)) {
            return "工作日"
        }
        if (repeatDays == setOf(6, 7)) {
            return "周末"
        }
        return repeatDays.sorted().map { getDayName(it) }.joinToString("、")
    }
}
