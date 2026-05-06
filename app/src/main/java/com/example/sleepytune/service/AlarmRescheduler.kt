package com.example.sleepytune.service

import android.content.Context
import android.util.Log
import com.example.sleepytune.data.repository.AlarmRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRescheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler
) {
    companion object {
        private const val TAG = "AlarmRescheduler"
    }
    
    suspend fun rescheduleAllAlarms() {
        Log.i(TAG, "开始恢复所有闹钟...")
        
        try {
            val enabledAlarms = alarmRepository.getEnabledAlarms()
            
            if (enabledAlarms.isEmpty()) {
                Log.i(TAG, "没有启用的闹钟需要恢复")
                return
            }
            
            var successCount = 0
            var failCount = 0
            
            for (alarm in enabledAlarms) {
                try {
                    alarmScheduler.schedule(alarm)
                    successCount++
                    Log.i(TAG, "恢复闹钟: id=${alarm.id}, ${String.format("%02d:%02d", alarm.hour, alarm.minute)}")
                } catch (e: Exception) {
                    failCount++
                    Log.e(TAG, "恢复闹钟失败: id=${alarm.id}, ${e.message}")
                }
            }
            
            Log.i(TAG, "闹钟恢复完成: 成功=$successCount, 失败=$failCount")
            
        } catch (e: Exception) {
            Log.e(TAG, "恢复闹钟失败: ${e.message}", e)
        }
    }
    
    suspend fun rescheduleAlarm(alarmId: Long) {
        try {
            val alarm = alarmRepository.getAlarm(alarmId)
            if (alarm != null && alarm.isEnabled) {
                alarmScheduler.schedule(alarm)
                Log.i(TAG, "重新调度闹钟: id=$alarmId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "重新调度闹钟失败: id=$alarmId, ${e.message}")
        }
    }
}
