package com.example.sleepytune.utils

object Constants {
    
    // 通知相关
    object Notification {
        const val CHANNEL_ID_ALARM = "alarm_channel"
        const val CHANNEL_ID_SNOOZE = "snooze_channel"
        const val CHANNEL_ID_SLEEP_TIMER = "sleep_timer_channel"
        
        const val NOTIFICATION_ID_ALARM = 1001
        const val NOTIFICATION_ID_SNOOZE = 1002
        const val NOTIFICATION_ID_SLEEP_TIMER = 1003
    }
    
    // 闹钟相关
    object Alarm {
        const val SNOOZE_MINUTES = 5
        const val DEFAULT_DURATION_MINUTES = 5
        const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10分钟
    }
    
    // 音量相关
    object Volume {
        const val DEFAULT_VOLUME = 1.0f
        const val DEFAULT_FADE_IN_DURATION_MS = 30_000L // 30秒
        const val FADE_IN_STEP_DURATION_MS = 500L // 500ms
    }
    
    // 睡眠定时器
    object SleepTimer {
        val PRESET_TIMES = listOf(15, 30, 45, 60, 90, 120)
        const val FADE_OUT_DURATION_MS = 30_000L
    }
    
    // 自动停止
    object AutoStop {
        val PRESET_DURATIONS = listOf(1, 3, 5, 10, 15, 30)
        const val DEFAULT_DURATION_MINUTES = 5
    }
    
    // Intent Actions
    object Actions {
        const val ALARM_TRIGGER = "com.example.sleepytune.ALARM_TRIGGER"
        const val ALARM_BACKUP = "com.example.sleepytune.ALARM_BACKUP"
        const val SNOOZE_TRIGGER = "com.example.sleepytune.SNOOZE_TRIGGER"
        const val ACTION_STOP = "com.example.sleepytune.ACTION_STOP"
        const val ACTION_SNOOZE = "com.example.sleepytune.ACTION_SNOOZE"
    }
    
    // 请求码
    object RequestCode {
        const val ALARM_BASE = 1000
        const val ALARM_BACKUP_BASE = 2000
        const val SNOOZE = 9999
    }
    
    // 偏好设置Key
    object Prefs {
        const val THEME_MODE = "theme_mode"
        const val ALARM_VOLUME = "alarm_volume"
        const val FADE_IN_ENABLED = "fade_in_enabled"
        const val FADE_IN_DURATION = "fade_in_duration"
    }
}
