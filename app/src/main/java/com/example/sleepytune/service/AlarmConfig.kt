package com.example.sleepytune.service

// 闹钟配置参数（统一管理所有参数）
data class AlarmConfig(
    val alarmId: Long = -1L,
    val playlistId: Long = 0L,
    val ringtonePath: String? = null,
    val volume: Float = 1.0f,
    val vibrate: Boolean = true,
    val vibrateDelayMinutes: Int = 0,
    val label: String = "闹钟",
    val shuffleMode: Boolean = false,
    val fadeInEnabled: Boolean = true,
    val fadeInDuration: Int = 30,
    val snoozeMinutes: Int = 5,
    val snoozeEnabled: Boolean = true
) {
    // 是否跟随系统闹钟音量（volume < 0 表示跟随系统）
    val followSystemVolume: Boolean = volume < 0
    
    // 从 Intent 解析配置
    companion object {
        fun fromIntent(intent: android.content.Intent): AlarmConfig {
            return AlarmConfig(
                alarmId = intent.getLongExtra("alarm_id", -1L),
                playlistId = intent.getLongExtra("playlist_id", 0L),
                ringtonePath = intent.getStringExtra("ringtone_path"),
                volume = intent.getFloatExtra("volume", 1.0f),
                vibrate = intent.getBooleanExtra("vibrate", true),
                vibrateDelayMinutes = intent.getIntExtra("vibrate_delay_minutes", 0),
                label = intent.getStringExtra("label") ?: "闹钟",
                shuffleMode = intent.getBooleanExtra("shuffle_mode", false),
                fadeInEnabled = intent.getBooleanExtra("fade_in_enabled", true),
                fadeInDuration = intent.getIntExtra("fade_in_duration", 30),
                snoozeMinutes = intent.getIntExtra("snooze_minutes", 5),
                snoozeEnabled = intent.getBooleanExtra("snooze_enabled", true)
            )
        }
    }
}