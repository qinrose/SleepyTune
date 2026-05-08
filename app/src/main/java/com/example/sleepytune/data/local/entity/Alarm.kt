package com.example.sleepytune.data.local.entity

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
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val label: String? = null,
    val volume: Float = 1.0f,
    val duration: Int = 5,
    val shuffleMode: Boolean = false,
    val fadeInEnabled: Boolean = true,
    val fadeInDuration: Int = 30,
    val vibrateDelayMinutes: Int = 0
)
