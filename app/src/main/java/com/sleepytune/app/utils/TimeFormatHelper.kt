package com.sleepytune.app.utils

import com.sleepytune.app.ui.viewmodel.TimeFormatViewModel

object TimeFormatHelper {

    // 格式化时间显示（已解析 12/24 制，AUTO 由上层解析为布尔）
    fun formatTime(hour: Int, minute: Int, is24Hour: Boolean): String {
        return if (is24Hour) {
            String.format("%02d:%02d", hour, minute)
        } else {
            val displayHour = when (hour) {
                0 -> 12
                in 1..12 -> hour
                else -> hour - 12
            }
            val period = if (hour < 12) "AM" else "PM"
            String.format("%02d:%02d %s", displayHour, minute, period)
        }
    }

    // 格式化时间显示
    fun formatTime(hour: Int, minute: Int, timeFormat: TimeFormatViewModel.TimeFormat): String {
        return when (timeFormat) {
            TimeFormatViewModel.TimeFormat.HOUR_24 -> {
                String.format("%02d:%02d", hour, minute)
            }
            TimeFormatViewModel.TimeFormat.HOUR_12 -> {
                val (displayHour, period) = when (hour) {
                    0 -> 12 to "AM"
                    in 1..11 -> hour to "AM"
                    12 -> 12 to "PM"
                    in 13..23 -> (hour - 12) to "PM"
                    else -> hour to "AM"
                }
                String.format("%02d:%02d %s", displayHour, minute, period)
            }
            TimeFormatViewModel.TimeFormat.AUTO -> {
                // 需要系统上下文：调用方应使用 is24Hour 重载；此处兜底走 24 小时制
                String.format("%02d:%02d", hour, minute)
            }
        }
    }
    
    // 获取小时显示值（12小时制）
    fun getDisplayHour(hour: Int, timeFormat: TimeFormatViewModel.TimeFormat): Int {
        return when (timeFormat) {
            TimeFormatViewModel.TimeFormat.HOUR_24 -> hour
            TimeFormatViewModel.TimeFormat.HOUR_12 -> {
                when (hour) {
                    0 -> 12
                    in 1..12 -> hour
                    else -> hour - 12
                }
            }
            TimeFormatViewModel.TimeFormat.AUTO -> hour
        }
    }
    
    // 获取AM/PM标识
    fun getAmPm(hour: Int): String {
        return if (hour < 12) "AM" else "PM"
    }
    
    // 从12小时制转换为24小时制
    fun convertTo24Hour(displayHour: Int, isAM: Boolean): Int {
        return when {
            isAM && displayHour == 12 -> 0
            !isAM && displayHour == 12 -> 12
            !isAM -> displayHour + 12
            else -> displayHour
        }
    }
}
