package com.example.sleepytune.service

import android.content.Context
import android.util.Log
import com.example.sleepytune.data.local.entity.Alarm
import com.example.sleepytune.data.repository.AlarmRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRescheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val alarmStateManager: AlarmStateManager,
    private val notificationHelper: NotificationHelper
) {
    companion object {
        private const val TAG = "AlarmRescheduler"
        // 错过判定宽容窗口：超过这么久还没触发才算错过（避免时钟微差误报）
        private const val MISSED_GRACE_MS = 60_000L
    }

    suspend fun rescheduleAllAlarms() {
        Log.i(TAG, "开始恢复所有闹钟...")
        
        try {
            // 0. 错过检测：基于上一次持久化的 next_alarm_time
            //    必须在重新调度之前执行，否则 next_alarm_time 会被刷新掉
            checkMissedAlarms()

            // 无论有没有闹钟，都先兜底把自愈心跳安排好
            alarmScheduler.scheduleSelfHealing()

            val enabledAlarms = alarmRepository.getEnabledAlarms()
            
            if (enabledAlarms.isEmpty()) {
                Log.i(TAG, "没有启用的闹钟需要恢复")
                refreshNextAlarmTime(enabledAlarms)
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
            
            // 刷新"下一个闹钟时间"，供下次错过检测使用
            refreshNextAlarmTime(enabledAlarms)
            
            Log.i(TAG, "闹钟恢复完成: 成功=$successCount, 失败=$failCount")
            
        } catch (e: Exception) {
            Log.e(TAG, "恢复闹钟失败: ${e.message}", e)
        }
    }

    // 刷新持久化的"下一个闹钟触发时间"（所有启用闹钟中最早的一次）
    suspend fun refreshNextAlarmTime(enabledAlarms: List<Alarm>? = null) {
        try {
            val alarms = enabledAlarms ?: alarmRepository.getEnabledAlarms()
            val next = alarms.minOfOrNull { alarmScheduler.getNextTriggerTime(it) } ?: 0L
            if (next > 0) {
                alarmStateManager.persistNextAlarmTime(context, next)
            } else {
                alarmStateManager.clearNextAlarmTime(context)
            }
            Log.i(TAG, "下次闹钟时间: ${if (next > 0) next else "无"}")
        } catch (e: Exception) {
            Log.e(TAG, "刷新下次闹钟时间失败: ${e.message}", e)
        }
    }

    // 错过检测：到点没响（进程被杀/厂商清调度/强停）时提醒用户
    private fun checkMissedAlarms() {
        try {
            val next = alarmStateManager.getNextAlarmTime(context)
            if (next <= 0) return
            val now = System.currentTimeMillis()
            // 还没到点 / 刚过宽容窗口 → 不算错过
            if (next > now - MISSED_GRACE_MS) return
            // 正在播放中的闹钟不算错过
            if (alarmStateManager.isAlarmPlayingCrossProcess(context)) return
            // 实际触发时间 >= 预期时间 → 只是被系统延迟投递，不算错过
            if (alarmStateManager.getLastTriggerTime(context) >= next) return
            // 同一时刻已提示过 → 不重复打扰
            if (alarmStateManager.getMissedChecked(context) == next) return

            alarmStateManager.persistMissedChecked(context, next)
            notificationHelper.showMissedAlarmNotification(next)
            Log.w(TAG, "检测到错过的闹钟: $next")
        } catch (e: Exception) {
            Log.e(TAG, "错过检测失败: ${e.message}", e)
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
