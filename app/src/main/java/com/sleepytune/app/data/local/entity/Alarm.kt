package com.sleepytune.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alarms",
    indices = [
        Index(value = ["isEnabled"]),
        Index(value = ["hour", "minute"]),
        Index(value = ["playlistId"])
    ]
)
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val repeatDays: Set<Int>? = null,
    val isEnabled: Boolean = true,
    val playlistId: Long = 0L,
    val ringtone: String? = null,
    val vibrate: Boolean = true,
    val snoozeEnabled: Boolean = false,  // 默认不启用贪睡
    val snoozeMinutes: Int = 5,
    val label: String? = null,
    val volume: Float = 1.0f,
    val duration: Int = 5,
    val shuffleMode: Boolean = false,
    val fadeInEnabled: Boolean = true,
    val fadeInDuration: Int = 30,
    val vibrateDelayMinutes: Int = 0
) {
    companion object {
        /**
         * 哨兵 id：表示闹钟使用"每日随机"歌单（每天自动换一批随机歌曲）。
         * 不落库为真实歌单引用，响铃时由 PlaylistManager 解析为当日随机歌单。
         */
        const val DAILY_RANDOM_PLAYLIST_ID = -1L
    }
}
