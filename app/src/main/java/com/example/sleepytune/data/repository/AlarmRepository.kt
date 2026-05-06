package com.example.sleepytune.data.repository

import com.example.sleepytune.data.local.entity.Alarm
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun observeAllAlarms(): Flow<List<Alarm>>
    suspend fun getAlarm(id: Long): Alarm?
    suspend fun getEnabledAlarms(): List<Alarm>
    suspend fun saveAlarm(alarm: Alarm): Long
    suspend fun updateAlarm(alarm: Alarm)
    suspend fun deleteAlarm(alarm: Alarm)
    suspend fun enableAlarm(id: Long, enabled: Boolean)
}
