package com.example.sleepytune.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.sleepytune.data.repository.PlaylistRepository
import com.example.sleepytune.ui.screen.alarm.AlarmAlertActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"

        const val ACTION_ALARM_TRIGGERED = "com.example.sleepytune.ALARM_TRIGGERED"
        const val ACTION_SELF_HEAL = "com.example.sleepytune.SELF_HEAL"
        const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
        const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
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
        const val EXTRA_SNOOZE_ENABLED = "snooze_enabled"

        const val EXTRA_SONG_TITLE = "song_title"
        const val EXTRA_SONG_ARTIST = "song_artist"
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
                alarmScheduler.scheduleSelfHealing()
            }
            ACTION_MY_PACKAGE_REPLACED -> {
                Log.i(TAG, "App updated, restoring all alarms")
                rescheduleAllAlarms(alarmScheduler, alarmRepository)
                alarmScheduler.scheduleSelfHealing()
            }
            ACTION_TIME_SET, ACTION_TIMEZONE_CHANGED -> {
                Log.i(TAG, "Time/timezone changed, rescheduling all alarms")
                rescheduleAllAlarms(alarmScheduler, alarmRepository)
                alarmScheduler.scheduleSelfHealing()
            }
            ACTION_SELF_HEAL -> {
                Log.i(TAG, "自愈心跳：重新核对所有闹钟调度")
                rescheduleAllAlarms(alarmScheduler, alarmRepository)
                alarmScheduler.scheduleSelfHealing()
            }
            ACTION_ALARM_TRIGGERED, "com.example.sleepytune.ALARM_BACKUP" -> {
                handleAlarmTriggered(context, intent, alarmScheduler, alarmRepository, hiltEntryPoint)
            }
            AlarmSnoozeManager.ACTION_SNOOZE_TRIGGER -> {
                handleSnoozeTriggered(context, intent, hiltEntryPoint)
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
        alarmRepository: com.example.sleepytune.data.repository.AlarmRepository,
        hiltEntryPoint: AlarmReceiverEntryPoint
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
        val snoozeEnabled = intent.getBooleanExtra(EXTRA_SNOOZE_ENABLED, true)

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
            putExtra("snooze_enabled", snoozeEnabled)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val playlistRepository = hiltEntryPoint.playlistRepository()

            // 预加载歌曲信息，立即传给 Activity（考虑 shuffleMode）
            val (songTitle, songArtist) = loadSongInfo(ringtonePath, playlistId, shuffleMode, playlistRepository)

            val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("alarm_id", alarmId)
                putExtra("label", label)
                putExtra("snooze_enabled", snoozeEnabled)
                putExtra(EXTRA_SONG_TITLE, songTitle)
                putExtra(EXTRA_SONG_ARTIST, songArtist)
            }
            context.startActivity(alertIntent)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val alarm = alarmRepository.getAlarm(alarmId)
            if (alarm != null && !alarm.repeatDays.isNullOrEmpty()) {
                alarmScheduler.schedule(alarm)
            }
            // 每天自动核对一遍所有调度，防止 OEM 偷偷清掉闹钟
            alarmScheduler.scheduleSelfHealing()
        }
    }

    // 预加载歌曲信息（IO线程），避免Activity显示时歌曲名为空
    // 注意：歌单模式下不预加载，因为 shuffleMode 的随机选择可能与 PlaylistManager 不一致
    // 等 AlarmPlaybackService 发送广播更新正确的歌曲信息
    private suspend fun loadSongInfo(
        ringtonePath: String?,
        playlistId: Long,
        shuffleMode: Boolean,
        playlistRepository: PlaylistRepository
    ): Pair<String, String> {
        // 单铃声模式：直接解析文件名
        if (!ringtonePath.isNullOrEmpty()) {
            val fileName = ringtonePath.substringAfterLast("/").substringBeforeLast(".")
            return Pair(fileName, "闹钟铃声")
        }
        // 歌单模式：不预加载，等 Service 广播更新
        return Pair("", "")
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

    // 处理贪睡触发（AlarmSnoozeManager 调度的闹钟）
    private fun handleSnoozeTriggered(
        context: Context,
        intent: Intent,
        hiltEntryPoint: AlarmReceiverEntryPoint
    ) {
        Log.i(TAG, "贪睡触发")

        // 取消贪睡通知
        val notificationHelper = hiltEntryPoint.notificationHelper()
        notificationHelper.cancelSnoozeNotification()

        val alarmId = intent.getLongExtra("alarm_id", -1L)
        val playlistId = intent.getLongExtra("playlist_id", 0L)
        val ringtonePath = intent.getStringExtra("ringtone_path")
        val vibrate = intent.getBooleanExtra("vibrate", true)
        val volume = intent.getFloatExtra("volume", 1.0f)
        val label = intent.getStringExtra("label") ?: "闹钟"
        val shuffleMode = intent.getBooleanExtra("shuffle_mode", false)
        val fadeInEnabled = intent.getBooleanExtra("fade_in_enabled", true)
        val fadeInDuration = intent.getIntExtra("fade_in_duration", 30)
        val snoozeMinutes = intent.getIntExtra("snooze_minutes", 5)

        Log.i(TAG, "贪睡触发: alarmId=$alarmId, label=$label")

        // 启动播放服务
        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            putExtra("alarm_id", alarmId)
            putExtra("playlist_id", playlistId)
            putExtra("ringtone_path", ringtonePath)
            putExtra("vibrate", vibrate)
            putExtra("volume", volume)
            putExtra("label", label)
            putExtra("shuffle_mode", shuffleMode)
            putExtra("fade_in_enabled", fadeInEnabled)
            putExtra("fade_in_duration", fadeInDuration)
            putExtra("snooze_minutes", snoozeMinutes)
            putExtra("snooze_enabled", true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 预加载歌曲信息并启动 Activity
        CoroutineScope(Dispatchers.IO).launch {
            val playlistRepository = hiltEntryPoint.playlistRepository()
            val (songTitle, songArtist) = loadSongInfo(ringtonePath, playlistId, shuffleMode, playlistRepository)

            val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("alarm_id", alarmId)
                putExtra("label", label)
                putExtra("snooze_enabled", true)
                putExtra(EXTRA_SONG_TITLE, songTitle)
                putExtra(EXTRA_SONG_ARTIST, songArtist)
            }
            context.startActivity(alertIntent)
        }
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface AlarmReceiverEntryPoint {
        fun alarmScheduler(): AlarmScheduler
        fun alarmRepository(): com.example.sleepytune.data.repository.AlarmRepository
        fun playlistRepository(): PlaylistRepository
        fun notificationHelper(): NotificationHelper
    }
}