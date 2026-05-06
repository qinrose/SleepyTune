package com.example.sleepytune.data.repository

import com.example.sleepytune.data.local.dao.AlarmDao
import com.example.sleepytune.data.local.entity.Alarm
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepositoryImpl @Inject constructor(
    private val alarmDao: AlarmDao
) : AlarmRepository {

    override fun observeAllAlarms(): Flow<List<Alarm>> {
        return alarmDao.getAllAlarms()
    }

    override suspend fun getAlarm(id: Long): Alarm? {
        return alarmDao.getAlarmById(id)
    }

    override suspend fun getEnabledAlarms(): List<Alarm> {
        return alarmDao.getEnabledAlarms()
    }

    override suspend fun saveAlarm(alarm: Alarm): Long {
        return alarmDao.insert(alarm)
    }

    override suspend fun updateAlarm(alarm: Alarm) {
        alarmDao.update(alarm)
    }

    override suspend fun deleteAlarm(alarm: Alarm) {
        alarmDao.delete(alarm)
    }

    override suspend fun enableAlarm(id: Long, enabled: Boolean) {
        alarmDao.updateEnabled(id, enabled)
    }
}
