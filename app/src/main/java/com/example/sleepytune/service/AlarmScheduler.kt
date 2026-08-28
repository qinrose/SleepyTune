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
        private const val REQUEST_CODE_SELF_HEAL = 987654
        private const val PRE_WAKE_MS = 60_000L            // 响前 60 秒预唤醒
        private const val REQUEST_OFFSET_PRE_WAKE = 400_000 // 预唤醒 requestCode 偏移
        private const val REQUEST_OFFSET_BACKUP = 500_000   // 备份闹钟 requestCode 偏移
    }

    fun schedule(alarm: Alarm) {
        if (!alarm.isEnabled) {
            Log.w(TAG, "闹钟未启用，跳过调度: id=${alarm.id}")
            return
        }

        val alarmTime = calculateNextAlarmTime(alarm)
        val requestCode = alarm.id.toInt()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            buildAlarmIntent(AlarmReceiver.ACTION_ALARM_TRIGGERED, alarm),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val showIntent = Intent(context, com.example.sleepytune.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
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
        } catch (e: SecurityException) {
            Log.e(TAG, "调度闹钟失败(权限): ${e.message}")
            scheduleFallbackAlarm(alarmTime.timeInMillis, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "调度闹钟失败: ${e.message}", e)
            scheduleFallbackAlarm(alarmTime.timeInMillis, pendingIntent)
        }

        val exactAvailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        // 备份闹钟：同一时刻再挂一条，主闹钟投递失败时的兜底（服务端有防重入守卫，不会双响）
        try {
            val backupPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode + REQUEST_OFFSET_BACKUP,
                buildAlarmIntent(AlarmReceiver.ACTION_ALARM_BACKUP, alarm),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            if (exactAvailable) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime.timeInMillis,
                    backupPendingIntent
                )
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime.timeInMillis, backupPendingIntent)
            }
            Log.i(TAG, "已挂备份闹钟: id=${alarm.id}")
        } catch (e: Exception) {
            Log.w(TAG, "挂备份闹钟失败: ${e.message}")
        }

        // 预唤醒：响前 60 秒先醒一次（拿 WakeLock + 幂等重挂主闹钟，防 OEM 静默清调度）
        val preWakeTime = alarmTime.timeInMillis - PRE_WAKE_MS
        if (preWakeTime > System.currentTimeMillis()) {
            try {
                val preWakePendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode + REQUEST_OFFSET_PRE_WAKE,
                    buildAlarmIntent(AlarmReceiver.ACTION_PRE_WAKE, alarm),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                if (exactAvailable) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        preWakeTime,
                        preWakePendingIntent
                    )
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, preWakeTime, preWakePendingIntent)
                }
                Log.i(TAG, "已挂预唤醒闹钟: id=${alarm.id}, 提前60秒")
            } catch (e: Exception) {
                Log.w(TAG, "挂预唤醒闹钟失败: ${e.message}")
            }
        }

        // 每天自愈一次，重新核对所有闹钟，防止 OEM 悄悄清掉调度
        scheduleSelfHealing()
    }

    // 统一构建闹钟 Intent（主闹钟 / 备份 / 预唤醒共用同一套参数）
    private fun buildAlarmIntent(action: String, alarm: Alarm): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
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
            putExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, alarm.snoozeMinutes)
            putExtra(AlarmReceiver.EXTRA_SNOOZE_ENABLED, alarm.snoozeEnabled)
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

    // 计算闹钟的下一次触发时间（毫秒），供错过检测使用
    fun getNextTriggerTime(alarm: Alarm): Long {
        return calculateNextAlarmTime(alarm).timeInMillis
    }

    // 每天一次的“自愈心跳”：即便用户几天不打开 App，只要系统没重启/没强停，
    // 也会周期性把所有启用的闹钟重新注册一遍
    fun scheduleSelfHealing() {
        try {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_SELF_HEAL
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_SELF_HEAL,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            // 12 小时后先自愈一次，之后每天一次
            val firstTrigger = System.currentTimeMillis() + 12 * 60 * 60 * 1000L
            // 优先用精确闹钟（Doze 下不被延迟）；无权限时退回不精确重复
            val exactAvailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (exactAvailable) {
                // 一次性精确闹钟：触发后由 SELF_HEAL 分支再次挂起（re-arm）
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    firstTrigger,
                    pendingIntent
                )
            } else {
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    firstTrigger,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
            }
            Log.i(TAG, "已安排每日自愈心跳 (exact=$exactAvailable)")
        } catch (e: Exception) {
            Log.w(TAG, "安排自愈心跳失败: ${e.message}")
        }
    }

    fun cancel(alarm: Alarm) {
        cancelPendingIntent(alarm.id.toInt(), AlarmReceiver.ACTION_ALARM_TRIGGERED)
        cancelPendingIntent(alarm.id.toInt() + REQUEST_OFFSET_BACKUP, AlarmReceiver.ACTION_ALARM_BACKUP)
        cancelPendingIntent(alarm.id.toInt() + REQUEST_OFFSET_PRE_WAKE, AlarmReceiver.ACTION_PRE_WAKE)
        Log.i(TAG, "已取消闹钟(含备份/预唤醒): id=${alarm.id}")
    }

    private fun cancelPendingIntent(requestCode: Int, action: String) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
        }
        // 与 schedule() 使用相同的 FLAG_MUTABLE，避免因 flag 不一致导致取消失败
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        Log.i(TAG, "已取消 PendingIntent: requestCode=$requestCode, action=$action")
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
