package com.sleepytune.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmSnoozeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AlarmSnoozeManager"
        private const val PREFS_NAME = "snooze_prefs"
        private const val KEY_SNOOZE_ALARM_ID = "snooze_alarm_id"
        private const val KEY_SNOOZE_TRIGGER_TIME = "snooze_trigger_time"
        private const val KEY_SNOOZE_PLAYLIST_ID = "snooze_playlist_id"
        private const val KEY_SNOOZE_RINGTONE = "snooze_ringtone"
        private const val KEY_SNOOZE_VIBRATE = "snooze_vibrate"
        private const val KEY_SNOOZE_VOLUME = "snooze_volume"
        private const val KEY_SNOOZE_LABEL = "snooze_label"
        private const val KEY_SNOOZE_SHUFFLE = "snooze_shuffle"
        private const val KEY_SNOOZE_FADE_IN_ENABLED = "snooze_fade_in_enabled"
        private const val KEY_SNOOZE_FADE_IN_DURATION = "snooze_fade_in_duration"
        private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
        private const val KEY_SNOOZE_ENABLED = "snooze_enabled"
        
        const val SNOOZE_MINUTES = 5
        const val ACTION_SNOOZE_TRIGGER = "com.sleepytune.app.SNOOZE_TRIGGER"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 保存贪睡状态
    fun saveSnoozeState(
        alarmId: Long,
        playlistId: Long,
        ringtone: String?,
        vibrate: Boolean,
        volume: Float,
        label: String?,
        shuffleMode: Boolean = false,
        fadeInEnabled: Boolean = true,
        fadeInDuration: Int = 30,
        snoozeMinutes: Int = 5,
        snoozeEnabled: Boolean = false
    ) {
        val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        
        prefs.edit().apply {
            putLong(KEY_SNOOZE_ALARM_ID, alarmId)
            putLong(KEY_SNOOZE_TRIGGER_TIME, triggerTime)
            putLong(KEY_SNOOZE_PLAYLIST_ID, playlistId)
            putString(KEY_SNOOZE_RINGTONE, ringtone)
            putBoolean(KEY_SNOOZE_VIBRATE, vibrate)
            putFloat(KEY_SNOOZE_VOLUME, volume)
            putString(KEY_SNOOZE_LABEL, label)
            putBoolean(KEY_SNOOZE_SHUFFLE, shuffleMode)
            putBoolean(KEY_SNOOZE_FADE_IN_ENABLED, fadeInEnabled)
            putInt(KEY_SNOOZE_FADE_IN_DURATION, fadeInDuration)
            putInt(KEY_SNOOZE_MINUTES, snoozeMinutes)
            putBoolean(KEY_SNOOZE_ENABLED, snoozeEnabled)
            apply()
        }
        
        Log.i(TAG, "贪睡状态已保存: alarmId=$alarmId, triggerTime=$triggerTime, snoozeMinutes=$snoozeMinutes")
    }
    
    // 获取剩余贪睡时间(毫秒)
    fun getSnoozeRemainingTime(): Long {
        val triggerTime = prefs.getLong(KEY_SNOOZE_TRIGGER_TIME, 0L)
        if (triggerTime == 0L) return 0L
        
        val remaining = triggerTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }
    
    // 是否有贪睡中的闹钟
    fun hasActiveSnooze(): Boolean {
        return getSnoozeRemainingTime() > 0
    }
    
    // 获取贪睡闹钟ID
    fun getSnoozeAlarmId(): Long {
        return prefs.getLong(KEY_SNOOZE_ALARM_ID, -1L)
    }
    
    // 取消贪睡
    fun cancelSnooze() {
        // 取消AlarmManager中的贪睡闹钟
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9999, // 贪睡专用requestCode
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        
        pendingIntent?.let {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(it)
        }
        
        // 清除SharedPreferences
        prefs.edit().clear().apply()
        
        Log.i(TAG, "贪睡已取消")
    }
    
    // 调度贪睡闹钟
    fun scheduleSnoozeAlarm(
        alarmId: Long,
        playlistId: Long,
        ringtone: String?,
        vibrate: Boolean,
        volume: Float,
        label: String?,
        shuffleMode: Boolean = false,
        fadeInEnabled: Boolean = true,
        fadeInDuration: Int = 30,
        snoozeMinutes: Int = 5,
        snoozeEnabled: Boolean = false
    ) {
        val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)
        
        // 保存贪睡状态
        saveSnoozeState(alarmId, playlistId, ringtone, vibrate, volume, label, shuffleMode, fadeInEnabled, fadeInDuration, snoozeMinutes, snoozeEnabled)
        
        // 调度AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_TRIGGER
            putExtra("alarm_id", alarmId)
            putExtra("playlist_id", playlistId)
            putExtra("ringtone_path", ringtone)
            putExtra("vibrate", vibrate)
            putExtra("volume", volume)
            putExtra("label", label ?: context.getString(com.sleepytune.app.R.string.alarm_title))
            putExtra("is_snooze", true)
            putExtra("shuffle_mode", shuffleMode)
            putExtra("fade_in_enabled", fadeInEnabled)
            putExtra("fade_in_duration", fadeInDuration)
            putExtra("snooze_minutes", snoozeMinutes)
            putExtra("snooze_enabled", snoozeEnabled)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9999, // 贪睡专用requestCode
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            
            Log.i(TAG, "贪睡闹钟已调度: ${snoozeMinutes}分钟后触发")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "调度贪睡闹钟失败: ${e.message}", e)
        }
    }
    
    // 获取贪睡信息
    fun getSnoozeInfo(): SnoozeInfo? {
        val alarmId = prefs.getLong(KEY_SNOOZE_ALARM_ID, -1L)
        if (alarmId == -1L) return null
        
        return SnoozeInfo(
            alarmId = alarmId,
            playlistId = prefs.getLong(KEY_SNOOZE_PLAYLIST_ID, 0L),
            ringtone = prefs.getString(KEY_SNOOZE_RINGTONE, null),
            vibrate = prefs.getBoolean(KEY_SNOOZE_VIBRATE, true),
            volume = prefs.getFloat(KEY_SNOOZE_VOLUME, 1.0f),
            label = prefs.getString(KEY_SNOOZE_LABEL, null),
            triggerTime = prefs.getLong(KEY_SNOOZE_TRIGGER_TIME, 0L),
            shuffleMode = prefs.getBoolean(KEY_SNOOZE_SHUFFLE, false),
            fadeInEnabled = prefs.getBoolean(KEY_SNOOZE_FADE_IN_ENABLED, true),
            fadeInDuration = prefs.getInt(KEY_SNOOZE_FADE_IN_DURATION, 30),
            snoozeMinutes = prefs.getInt(KEY_SNOOZE_MINUTES, 5)
        )
    }
    
    // 清除贪睡状态
    fun clearSnoozeState() {
        prefs.edit().clear().apply()
        Log.i(TAG, "贪睡状态已清除")
    }
}

data class SnoozeInfo(
    val alarmId: Long,
    val playlistId: Long,
    val ringtone: String?,
    val vibrate: Boolean,
    val volume: Float,
    val label: String?,
    val triggerTime: Long,
    val shuffleMode: Boolean = false,
    val fadeInEnabled: Boolean = true,
    val fadeInDuration: Int = 30,
    val snoozeMinutes: Int = 5
)
