package com.example.sleepytune.utils

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// 日期格式化工具类 - 支持本地化
object DateFormatUtils {

    // 格式化时间为 HH:mm 格式（本地化）
    fun formatTime(context: Context, hour: Int, minute: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val locale = getCurrentLocale(context)
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        
        return if (is24Hour) {
            String.format(locale, "%02d:%02d", hour, minute)
        } else {
            val format = SimpleDateFormat("h:mm a", locale)
            format.format(calendar.time)
        }
    }

    // 格式化日期为完整格式（本地化）
    fun formatDate(context: Context, date: Date): String {
        val locale = getCurrentLocale(context)
        val format = SimpleDateFormat.getDateInstance(SimpleDateFormat.FULL, locale)
        return format.format(date)
    }

    // 格式化日期为短格式（本地化）
    fun formatShortDate(context: Context, date: Date): String {
        val locale = getCurrentLocale(context)
        val format = SimpleDateFormat.getDateInstance(SimpleDateFormat.SHORT, locale)
        return format.format(date)
    }

    // 格式化日期时间（本地化）
    fun formatDateTime(context: Context, date: Date): String {
        val locale = getCurrentLocale(context)
        val format = SimpleDateFormat.getDateTimeInstance(
            SimpleDateFormat.MEDIUM,
            SimpleDateFormat.SHORT,
            locale
        )
        return format.format(date)
    }

    // 格式化星期几（本地化）
    fun formatDayOfWeek(context: Context, dayOfWeek: Int): String {
        val locale = getCurrentLocale(context)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
        }
        val format = SimpleDateFormat("EEEE", locale)
        return format.format(calendar.time)
    }

    // 格式化短星期几（本地化）
    fun formatShortDayOfWeek(context: Context, dayOfWeek: Int): String {
        val locale = getCurrentLocale(context)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
        }
        val format = SimpleDateFormat("EEE", locale)
        return format.format(calendar.time)
    }

    // 格式化月份（本地化）
    fun formatMonth(context: Context, month: Int): String {
        val locale = getCurrentLocale(context)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.MONTH, month)
        }
        val format = SimpleDateFormat("MMMM", locale)
        return format.format(calendar.time)
    }

    // 格式化相对时间（如"2小时后"）
    fun formatRelativeTime(context: Context, futureTime: Long): String {
        val now = System.currentTimeMillis()
        val diff = futureTime - now
        
        val locale = getCurrentLocale(context)
        val minutes = (diff / (1000 * 60)).toInt()
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> {
                if (locale.language == "zh") {
                    "${days}天后"
                } else {
                    "in $days day${if (days > 1) "s" else ""}"
                }
            }
            hours > 0 -> {
                if (locale.language == "zh") {
                    "${hours}小时后"
                } else {
                    "in $hours hour${if (hours > 1) "s" else ""}"
                }
            }
            minutes > 0 -> {
                if (locale.language == "zh") {
                    "${minutes}分钟后"
                } else {
                    "in $minutes minute${if (minutes > 1) "s" else ""}"
                }
            }
            else -> {
                if (locale.language == "zh") {
                    "即将"
                } else {
                    "soon"
                }
            }
        }
    }

    // 格式化持续时间（本地化）
    fun formatDuration(context: Context, durationMinutes: Int): String {
        val locale = getCurrentLocale(context)
        
        return if (locale.language == "zh") {
            when {
                durationMinutes >= 60 -> {
                    val hours = durationMinutes / 60
                    val mins = durationMinutes % 60
                    if (mins > 0) "${hours}小时${mins}分钟" else "${hours}小时"
                }
                else -> "${durationMinutes}分钟"
            }
        } else {
            when {
                durationMinutes >= 60 -> {
                    val hours = durationMinutes / 60
                    val mins = durationMinutes % 60
                    if (mins > 0) "$hours hr $mins min" else "$hours hr"
                }
                else -> "$durationMinutes min"
            }
        }
    }

    // 获取当前Locale
    private fun getCurrentLocale(context: Context): Locale {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }

    // 判断是否为中文环境
    fun isChineseLocale(context: Context): Boolean {
        return getCurrentLocale(context).language == "zh"
    }

    // 格式化下次闹钟时间描述
    fun formatNextAlarmDescription(context: Context, nextTime: Long): String {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = nextTime }
        val nowCalendar = Calendar.getInstance()
        
        val isToday = calendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == nowCalendar.get(Calendar.DAY_OF_YEAR)
        
        val isTomorrow = calendar.get(Calendar.YEAR) == nowCalendar.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == nowCalendar.get(Calendar.DAY_OF_YEAR) + 1
        
        val timeStr = formatTime(
            context,
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
        
        val locale = getCurrentLocale(context)
        
        return when {
            isToday -> {
                if (locale.language == "zh") "今天 $timeStr" else "Today $timeStr"
            }
            isTomorrow -> {
                if (locale.language == "zh") "明天 $timeStr" else "Tomorrow $timeStr"
            }
            else -> {
                formatDateTime(context, Date(nextTime))
            }
        }
    }
}
