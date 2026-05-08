package com.example.sleepytune.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.sleepytune.ui.screen.alarm.AlarmAlertActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"

        const val ACTION_ALARM_TRIGGERED = "com.example.sleepytune.ALARM_TRIGGERED"
        const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
        const val ACTION_TIME_SET = "android.intent.action.TIME_SET"
        const val ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED"

        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_PLAYLIST_ID = "playlist_id"
        const val EXTRA_RINGTONE_PATH = "ringtone_path"
        const val EXTRA_VIBRATE = "vibrate"
        const val EXTRA_VIBRATE_DELAY_MINUTES = "vibrate_delay_minutes"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_LABEL = "label"
        const val EXTRA_SHUFFLE_MODE = "shuffle_mode"
        const val EXTRA_FADE_IN_ENABLED = "fade_in_enabled"
        const val EXTRA_FADE_IN_DURATION = "fade_in_duration"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received broadcast: ${intent.action}")

        val appContext = context.applicationContext
        val hiltEntryPoint = EntryPointAccessors.fromApplication(
            appContext,
            AlarmReceiverEntryPoint::class.java
        )

        val alarmScheduler = hiltEntryPoint.alarmScheduler()
        val alarmRepository = hiltEntryPoint.alarmRepository()

        when (intent.action) {
            ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "System reboot, rescheduling all alarms")
                rescheduleAllAlarms(alarmScheduler, alarmRepository)
            }
            ACTION_TIME_SET, ACTION_TIMEZONE_CHANGED -> {
                Log.i(TAG, "Time/timezone changed, rescheduling all alarms")
                rescheduleAllAlarms(alarmScheduler, alarmRepository)
            }
            ACTION_ALARM_TRIGGERED -> {
                handleAlarmTriggered(context, intent, alarmScheduler, alarmRepository)
            }
            else -> {
                Log.w(TAG, "Unknown broadcast: ${intent.action}")
            }
        }
    }

    private fun handleAlarmTriggered(
        context: Context,
        intent: Intent,
        alarmScheduler: AlarmScheduler,
        alarmRepository: com.example.sleepytune.data.repository.AlarmRepository
    ) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        val playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, 0L)
        val ringtonePath = intent.getStringExtra(EXTRA_RINGTONE_PATH)
        val vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)
        val vibrateDelayMinutes = intent.getIntExtra(EXTRA_VIBRATE_DELAY_MINUTES, 0)
        val volume = intent.getFloatExtra(EXTRA_VOLUME, 1.0f)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Alarm"
        val shuffleMode = intent.getBooleanExtra(EXTRA_SHUFFLE_MODE, false)
        val fadeInEnabled = intent.getBooleanExtra(EXTRA_FADE_IN_ENABLED, true)
        val fadeInDuration = intent.getIntExtra(EXTRA_FADE_IN_DURATION, 30)
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 5)

        Log.i(TAG, "Alarm triggered: id=$alarmId, playlist=$playlistId, vibrate=$vibrate, delay=$vibrateDelayMinutes, snooze=$snoozeMinutes")

        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("playlist_id", playlistId)
            putExtra("ringtone_path", ringtonePath)
            putExtra("vibrate", vibrate)
            putExtra("vibrate_delay_minutes", vibrateDelayMinutes)
            putExtra("volume", volume)
            putExtra("label", label)
            putExtra("shuffle_mode", shuffleMode)
            putExtra("fade_in_enabled", fadeInEnabled)
            putExtra("fade_in_duration", fadeInDuration)
            putExtra("snooze_minutes", snoozeMinutes)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("alarm_id", alarmId)
            putExtra("label", label)
        }
        context.startActivity(alertIntent)

        CoroutineScope(Dispatchers.IO).launch {
            val alarm = alarmRepository.getAlarm(alarmId)
            if (alarm != null && !alarm.repeatDays.isNullOrEmpty()) {
                alarmScheduler.schedule(alarm)
            }
        }
    }

    private fun rescheduleAllAlarms(
        alarmScheduler: AlarmScheduler,
        alarmRepository: com.example.sleepytune.data.repository.AlarmRepository
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alarms = alarmRepository.getEnabledAlarms()
                alarms.forEach { alarm ->
                    alarmScheduler.schedule(alarm)
                }
                Log.i(TAG, "Rescheduled ${alarms.size} alarms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reschedule alarms: ${e.message}", e)
            }
        }
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface AlarmReceiverEntryPoint {
        fun alarmScheduler(): AlarmScheduler
        fun alarmRepository(): com.example.sleepytune.data.repository.AlarmRepository
    }
}
