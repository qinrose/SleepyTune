package com.example.sleepytune.service

import android.content.Context
import android.content.Intent
import com.example.sleepytune.data.local.entity.Music
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
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

    // 持久化贪睡启用状态
    fun persistSnoozeEnabled(context: Context, snoozeEnabled: Boolean) {
        context.getSharedPreferences("alarm_state", Context.MODE_MULTI_PROCESS).edit().apply {
            putBoolean("snooze_enabled", snoozeEnabled)
            apply()
        }
    }

    // 持久化闹钟参数（进程恢复用）
    fun persistAlarmParams(
        context: Context,
        volume: Float,
        vibrate: Boolean,
        vibrateDelayMinutes: Int,
        shuffleMode: Boolean,
        fadeInEnabled: Boolean,
        fadeInDuration: Int,
        snoozeMinutes: Int
    ) {
        context.getSharedPreferences("alarm_params", Context.MODE_PRIVATE).edit().apply {
            putFloat("volume", volume)
            putBoolean("vibrate", vibrate)
            putInt("vibrate_delay_minutes", vibrateDelayMinutes)
            putBoolean("shuffle_mode", shuffleMode)
            putBoolean("fade_in_enabled", fadeInEnabled)
            putInt("fade_in_duration", fadeInDuration)
            putInt("snooze_minutes", snoozeMinutes)
            apply()
        }
    }

    // 加载闹钟参数
    fun loadAlarmParams(context: Context): AlarmParams {
        val prefs = context.getSharedPreferences("alarm_params", Context.MODE_PRIVATE)
        return AlarmParams(
            volume = prefs.getFloat("volume", 1.0f),
            vibrate = prefs.getBoolean("vibrate", true),
            vibrateDelayMinutes = prefs.getInt("vibrate_delay_minutes", 0),
            shuffleMode = prefs.getBoolean("shuffle_mode", false),
            fadeInEnabled = prefs.getBoolean("fade_in_enabled", true),
            fadeInDuration = prefs.getInt("fade_in_duration", 30),
            snoozeMinutes = prefs.getInt("snooze_minutes", 5)
        )
    }

    // 闹钟参数数据类
    data class AlarmParams(
        val volume: Float,
        val vibrate: Boolean,
        val vibrateDelayMinutes: Int,
        val shuffleMode: Boolean,
        val fadeInEnabled: Boolean,
        val fadeInDuration: Int,
        val snoozeMinutes: Int
    )

    // 获取贪睡启用状态
    fun getSnoozeEnabledCrossProcess(context: Context): Boolean {
        return context.getSharedPreferences("alarm_state", Context.MODE_MULTI_PROCESS)
            .getBoolean("snooze_enabled", true)
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

    // 获取跨进程的闹钟ID
    fun getAlarmIdCrossProcess(context: Context): Long {
        return context.getSharedPreferences("alarm_state", Context.MODE_MULTI_PROCESS)
            .getLong("alarm_id", -1L)
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

    // 持久化歌单列表（进程恢复用）
    fun persistPlaylist(context: Context, musicList: List<Music>, currentIndex: Int) {
        val arr = JSONArray()
        musicList.forEach { music ->
            arr.put(JSONObject().apply {
                put("id", music.id)
                put("title", music.title)
                put("artist", music.artist)
                put("path", music.path)
            })
        }
        context.getSharedPreferences("alarm_playlist", Context.MODE_PRIVATE).edit().apply {
            putString("music_list", arr.toString())
            putInt("current_index", currentIndex)
            apply()
        }
    }

    // 加载持久化的歌单
    fun loadPlaylist(context: Context): Pair<List<Music>, Int> {
        val prefs = context.getSharedPreferences("alarm_playlist", Context.MODE_PRIVATE)
        val json = prefs.getString("music_list", null) ?: return Pair(emptyList(), 0)
        val index = prefs.getInt("current_index", 0)
        val arr = JSONArray(json)
        val list = mutableListOf<Music>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(Music(
                id = obj.getLong("id"),
                title = obj.optString("title", ""),
                artist = obj.optString("artist", ""),
                album = "",
                duration = 0L,
                size = 0L,
                path = obj.getString("path"),
                mimeType = "audio/*"
            ))
        }
        return Pair(list, index)
    }

    // 持久化单铃声路径
    fun persistRingtonePath(context: Context, path: String?) {
        context.getSharedPreferences("alarm_playlist", Context.MODE_PRIVATE).edit().apply {
            putString("ringtone_path", path)
            apply()
        }
    }

    // 加载单铃声路径
    fun loadRingtonePath(context: Context): String? {
        return context.getSharedPreferences("alarm_playlist", Context.MODE_PRIVATE)
            .getString("ringtone_path", null)
    }

    // 持久化歌单ID
    fun persistPlaylistId(context: Context, playlistId: Long) {
        context.getSharedPreferences("alarm_playlist", Context.MODE_PRIVATE).edit().apply {
            putLong("playlist_id", playlistId)
            apply()
        }
    }

    // 加载歌单ID
    fun loadPlaylistId(context: Context): Long {
        return context.getSharedPreferences("alarm_playlist", Context.MODE_PRIVATE)
            .getLong("playlist_id", 0L)
    }

    // 清除歌单持久化状态
    fun clearPlaylistState(context: Context) {
        context.getSharedPreferences("alarm_playlist", Context.MODE_PRIVATE).edit().clear().apply()
    }

    // 清除跨进程持久化状态
    fun clearPersistedState(context: Context) {
        context.getSharedPreferences("alarm_state", Context.MODE_MULTI_PROCESS).edit().clear().commit()
        clearPlaylistState(context)
        clearState()
    }
}
