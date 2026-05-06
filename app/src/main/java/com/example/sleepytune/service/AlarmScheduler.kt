package com.example.sleepytune.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.sleepytune.data.local.entity.Alarm
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val REQUEST_CODE_BASE = 1000
    }

    fun schedule(alarm: Alarm) {
        if (!alarm.isEnabled) {
            Log.w(TAG, "闹钟未启用，跳过调度: id=${alarm.id}")
            return
        }

        val alarmTime = calculateNextAlarmTime(alarm)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGERED
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_PLAYLIST_ID, alarm.playlistId)
            putExtra(AlarmReceiver.EXTRA_RINGTONE_PATH, alarm.ringtone)
            putExtra(AlarmReceiver.EXTRA_VIBRATE, alarm.vibrate)
            putExtra(AlarmReceiver.EXTRA_VIBRATE_DELAY_MINUTES, alarm.vibrateDelayMinutes)
            putExtra(AlarmReceiver.EXTRA_VOLUME, alarm.volume)
            putExtra(AlarmReceiver.EXTRA_LABEL, alarm.label)
            putExtra(AlarmReceiver.EXTRA_SHUFFLE_MODE, alarm.shuffleMode)
            putExtra(AlarmReceiver.EXTRA_FADE_IN_ENABLED, alarm.fadeInEnabled)
            putExtra(AlarmReceiver.EXTRA_FADE_IN_DURATION, alarm.fadeInDuration)
        }

        val requestCode = alarm.id.toInt()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, com.example.sleepytune.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(
            alarmTime.timeInMillis,
            showPendingIntent
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.w(TAG, "无精确闹钟权限")
                }
            }

            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.i(TAG, "已调度闹钟: id=${alarm.id}, 时间=${alarmTime.time}")

            // 备用闹钟
            if (!alarm.repeatDays.isNullOrEmpty()) {
                scheduleBackupAlarm(alarm, alarmTime.timeInMillis)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "调度闹钟失败(权限): ${e.message}")
            scheduleFallbackAlarm(alarmTime.timeInMillis, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "调度闹钟失败: ${e.message}", e)
            scheduleFallbackAlarm(alarmTime.timeInMillis, pendingIntent)
        }
    }

    private fun scheduleBackupAlarm(alarm: Alarm, triggerTimeMs: Long) {
        val backupIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.sleepytune.ALARM_BACKUP"
            putExtra("alarm_id", alarm.id)
            putExtra("playlist_id", alarm.playlistId)
            putExtra("ringtone_path", alarm.ringtone)
            putExtra("vibrate", alarm.vibrate)
            putExtra("vibrate_delay_minutes", alarm.vibrateDelayMinutes)
            putExtra("volume", alarm.volume)
            putExtra("label", alarm.label ?: "闹钟")
            putExtra("shuffle_mode", alarm.shuffleMode)
            putExtra("fade_in_enabled", alarm.fadeInEnabled)
            putExtra("fade_in_duration", alarm.fadeInDuration)
            putExtra("is_backup", true)
        }

        val backupPendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt() + 100000,
            backupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val backupCalendar = Calendar.getInstance().apply {
            timeInMillis = triggerTimeMs
            add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                backupCalendar.timeInMillis,
                backupPendingIntent
            )
        } catch (e: Exception) {
            Log.e(TAG, "备用闹钟调度失败: ${e.message}")
        }
    }

    private fun scheduleFallbackAlarm(triggerTimeMs: Long, pendingIntent: PendingIntent) {
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMs,
                pendingIntent
            )
        } catch (e: Exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        }
    }

    fun cancel(alarm: Alarm) {
        cancelPendingIntent(alarm.id.toInt())
        cancelPendingIntent(alarm.id.toInt() + 100000)
        Log.i(TAG, "已取消闹钟: id=${alarm.id}")
    }

    private fun cancelPendingIntent(requestCode: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun calculateNextAlarmTime(alarm: Alarm): Calendar {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.repeatDays.isNullOrEmpty()) {
            if (calendar.before(Calendar.getInstance())) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            return calendar
        }

        val targetDays = alarm.repeatDays.map { dayValue ->
            when (dayValue) {
                1 -> Calendar.MONDAY
                2 -> Calendar.TUESDAY
                3 -> Calendar.WEDNESDAY
                4 -> Calendar.THURSDAY
                5 -> Calendar.FRIDAY
                6 -> Calendar.SATURDAY
                7 -> Calendar.SUNDAY
                else -> Calendar.MONDAY
            }
        }.toSet()

        val today = calendar.get(Calendar.DAY_OF_WEEK)

        for (i in 0..7) {
            val checkDay = (today - 1 + i) % 7 + 1
            if (targetDays.contains(checkDay)) {
                if (i > 0) {
                    calendar.add(Calendar.DAY_OF_YEAR, i)
                }
                if (i == 0 && calendar.before(Calendar.getInstance())) {
                    continue
                }
                return calendar
            }
        }

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar
    }
}
