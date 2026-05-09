package com.example.sleepytune.service

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// 全局闹钟状态管理器
@Singleton
class AlarmStateManager @Inject constructor() {
    
    private val _isAlarmPlaying = MutableStateFlow(false)
    val isAlarmPlaying: StateFlow<Boolean> = _isAlarmPlaying.asStateFlow()
    
    private val _currentAlarmId = MutableStateFlow(-1L)
    val currentAlarmId: StateFlow<Long> = _currentAlarmId.asStateFlow()
    
    private val _alarmLabel = MutableStateFlow("")
    val alarmLabel: StateFlow<String> = _alarmLabel.asStateFlow()
    
    private val _songTitle = MutableStateFlow("")
    val songTitle: StateFlow<String> = _songTitle.asStateFlow()
    
    private val _songArtist = MutableStateFlow("")
    val songArtist: StateFlow<String> = _songArtist.asStateFlow()

    // 跨进程持久化闹钟播放状态
    fun persistAlarmPlaying(context: Context, isPlaying: Boolean, alarmId: Long = -1L, label: String = "") {
        context.getSharedPreferences("alarm_state", Context.MODE_MULTI_PROCESS).edit().apply {
            putBoolean("is_alarm_playing", isPlaying)
            putLong("alarm_id", alarmId)
            putString("alarm_label", label)
            apply()
        }
        _isAlarmPlaying.value = isPlaying
        _currentAlarmId.value = alarmId
        _alarmLabel.value = label
    }

    // 持久化歌曲信息（跨进程）
    fun persistSongInfo(context: Context, title: String, artist: String) {
        context.getSharedPreferences("alarm_state", Context.MODE_MULTI_PROCESS).edit().apply {
            putString("song_title", title)
            putString("song_artist", artist)
            apply()
        }
        _songTitle.value = title
        _songArtist.value = artist
    }

    // 持久化震动状态
    fun persistVibrateState(context: Context, shouldVibrate: Boolean, isVibrating: Boolean) {
        context.getSharedPreferences("alarm_state", Context.MODE_MULTI_PROCESS).edit().apply {
            putBoolean("should_vibrate", shouldVibrate)
            putBoolean("is_vibrating", isVibrating)
            apply()
        }
    }

    // 获取震动状态
    fun getVibrateStateCrossProcess(context: Context): Pair<Boolean, Boolean> {
        val prefs = context.getSharedPreferences("alarm_state", Context.MODE_MULTI_PROCESS)
        return Pair(
            prefs.getBoolean("should_vibrate", true),
            prefs.getBoolean("is_vibrating", false)
        )
    }

    // 从SharedPreferences读取跨进程状态
    fun isAlarmPlayingCrossProcess(context: Context): Boolean {
        return context.getSharedPreferences("alarm_state", Context.MODE_MULTI_PROCESS)
            .getBoolean("is_alarm_playing", false)
    }

    // 获取跨进程的闹钟标签
    fun getAlarmLabelCrossProcess(context: Context): String {
        return context.getSharedPreferences("alarm_state", Context.MODE_MULTI_PROCESS)
            .getString("alarm_label", "") ?: ""
    }

    // 获取跨进程的歌曲信息
    fun getSongInfoCrossProcess(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences("alarm_state", Context.MODE_MULTI_PROCESS)
        return Pair(
            prefs.getString("song_title", "") ?: "",
            prefs.getString("song_artist", "") ?: ""
        )
    }

    // 设置闹钟播放状态
    fun setAlarmPlaying(isPlaying: Boolean, alarmId: Long = -1L, label: String = "") {
        _isAlarmPlaying.value = isPlaying
        _currentAlarmId.value = alarmId
        _alarmLabel.value = label
    }
    
    fun setSongInfo(title: String, artist: String) {
        _songTitle.value = title
        _songArtist.value = artist
    }
    
    fun stopAlarm(context: Context) {
        val intent = Intent(context, AlarmPlaybackService::class.java)
        context.stopService(intent)
        val snoozeIntent = Intent("com.example.sleepytune.CLEAR_SNOOZE")
        context.sendBroadcast(snoozeIntent)
        persistAlarmPlaying(context, false)
    }
    
    fun clearState() {
        _isAlarmPlaying.value = false
        _currentAlarmId.value = -1L
        _alarmLabel.value = ""
        _songTitle.value = ""
        _songArtist.value = ""
    }

    // 清除跨进程持久化状态
    fun clearPersistedState(context: Context) {
        // 使用 commit() 同步写入，确保状态立即清除
        context.getSharedPreferences("alarm_state", Context.MODE_MULTI_PROCESS).edit().clear().commit()
        clearState()
    }
}
