package com.example.sleepytune.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleepytune.R
import com.example.sleepytune.data.local.entity.Alarm
import com.example.sleepytune.data.local.entity.Playlist
import com.example.sleepytune.data.repository.AlarmRepository
import com.example.sleepytune.data.repository.PlaylistRepository
import com.example.sleepytune.service.AlarmScheduler
import com.example.sleepytune.utils.PermissionUtils
import com.example.sleepytune.utils.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AlarmEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRepository: AlarmRepository,
    private val playlistRepository: PlaylistRepository,
    private val alarmScheduler: AlarmScheduler,
    private val alarmRescheduler: com.example.sleepytune.service.AlarmRescheduler
) : ViewModel() {

    // 新建闹钟默认"当前时间的下一个 5 分钟整点"（如 14:07 → 14:10），而非固定 8:00
    private val _alarm = MutableStateFlow(
        TimeUtils.nextFiveMinuteMark().let { (h, m) -> Alarm(hour = h, minute = m, playlistId = 0L) }
    )
    val alarm: StateFlow<Alarm> = _alarm.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    // 每日随机歌单 id（用于在歌单选择列表里隐藏它）
    private val _dailyRandomPlaylistId = MutableStateFlow(0L)
    val dailyRandomPlaylistId: StateFlow<Long> = _dailyRandomPlaylistId.asStateFlow()

    /** 刷新每日随机歌单（跨天自动换一批；force=true 手动换一批） */
    fun refreshDailyRandomPlaylist(force: Boolean = false) {
        viewModelScope.launch {
            val name = context.getString(R.string.playlist_daily_random)
            val id = playlistRepository.refreshDailyRandomPlaylist(name, force = force)
            if (id != null && id > 0) {
                _dailyRandomPlaylistId.value = id
            }
        }
    }

    fun updateTime(hour: Int, minute: Int) {
        _alarm.value = _alarm.value.copy(hour = hour, minute = minute)
    }

    fun setPlaylist(playlistId: Long) {
        _alarm.value = _alarm.value.copy(playlistId = playlistId)
    }

    fun setRepeatDays(days: Set<Int>) {
        _alarm.value = _alarm.value.copy(repeatDays = days)
    }

    fun setVibrate(vibrate: Boolean) {
        _alarm.value = _alarm.value.copy(vibrate = vibrate)
    }

    fun setSnoozeEnabled(snoozeEnabled: Boolean) {
        _alarm.value = _alarm.value.copy(snoozeEnabled = snoozeEnabled)
    }

    fun setSnoozeMinutes(minutes: Int) {
        _alarm.value = _alarm.value.copy(snoozeMinutes = minutes)
    }

    fun setLabel(label: String?) {
        _alarm.value = _alarm.value.copy(label = label)
    }

    fun setVolume(volume: Float) {
        _alarm.value = _alarm.value.copy(volume = volume)
    }

    fun setShuffleMode(shuffleMode: Boolean) {
        _alarm.value = _alarm.value.copy(shuffleMode = shuffleMode)
    }

    fun setFadeInEnabled(enabled: Boolean) {
        _alarm.value = _alarm.value.copy(fadeInEnabled = enabled)
    }

    fun setFadeInDuration(durationSeconds: Int) {
        _alarm.value = _alarm.value.copy(fadeInDuration = durationSeconds)
    }

    fun setVibrateDelayMinutes(minutes: Int) {
        _alarm.value = _alarm.value.copy(vibrateDelayMinutes = minutes)
    }

    fun saveAlarm(context: Context? = null, onResult: (Boolean) -> Unit = {}) {
        val alarmToSave = _alarm.value

        if (context != null && !PermissionUtils.checkExactAlarmPermission(context)) {
            PermissionUtils.requestExactAlarmPermission(context) {
                doSaveAlarm(alarmToSave, context, onResult)
            }
            return
        }

        doSaveAlarm(alarmToSave, context, onResult)
    }

    private fun doSaveAlarm(alarmToSave: Alarm, context: Context?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                // 保存前校验：避免存一个"必定响默认铃声"的闹钟
                context?.let { ctx ->
                    val (block, message) = validateMusicSelection(alarmToSave, ctx)
                    if (message != null) {
                        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
                        if (block) {
                            onResult(false)
                            return@launch
                        }
                    }
                }

                if (alarmToSave.id == 0L) {
                    val id = alarmRepository.saveAlarm(alarmToSave)
                    android.util.Log.i("AlarmEditVM", "新建闹钟成功: id=$id")

                    if (alarmToSave.isEnabled) {
                        alarmScheduler.schedule(alarmToSave.copy(id = id))
                    }
                } else {
                    alarmScheduler.cancel(alarmToSave)
                    alarmRepository.updateAlarm(alarmToSave)

                    if (alarmToSave.isEnabled) {
                        alarmScheduler.schedule(alarmToSave)
                    }
                }
                // 刷新"下一个闹钟时间"，保持错过检测准确
                alarmRescheduler.refreshNextAlarmTime()
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("AlarmEditVM", "保存闹钟失败: ${e.message}", e)
                onResult(false)
            }
        }
    }

    /**
     * 校验闹钟选中的音乐是否可播放。
     * @return Pair(是否阻止保存, 提示消息)；null 消息表示校验通过
     */
    private suspend fun validateMusicSelection(alarm: Alarm, context: Context): Pair<Boolean, String?> {
        // 每日随机歌单：先确保当日歌单存在，再校验其中的歌曲
        val songIds = if (alarm.playlistId == Alarm.DAILY_RANDOM_PLAYLIST_ID) {
            val dailyId = playlistRepository.refreshDailyRandomPlaylist(
                context.getString(R.string.playlist_daily_random)
            )
            if (dailyId == null) {
                return Pair(false, "每日随机歌单生成失败，请稍后重试")
            }
            playlistRepository.getSongsInPlaylist(dailyId)
        } else if (alarm.playlistId <= 0) {
            // 未绑定歌单：允许保存（用户可能有意用默认铃声），但明确提示
            return Pair(false, "未选择音乐歌单，响铃时将使用系统默认铃声")
        } else {
            playlistRepository.getSongsInPlaylist(alarm.playlistId)
        }

        if (songIds.isEmpty()) {
            return Pair(true, "所选歌单为空，无法设置为闹钟音乐")
        }

        val songs = songIds.mapNotNull { playlistRepository.getMusicById(it) }
        if (songs.isEmpty()) {
            return Pair(true, "所选歌单的歌曲数据已失效，请到音乐库重新扫描")
        }

        // 至少需要一首当前可播放的歌曲（磁盘检查放 IO 线程）
        val anyPlayable = withContext(Dispatchers.IO) {
            songs.any { isPathPlayable(context, it.path) }
        }
        if (anyPlayable) return Pair(false, null)
        return Pair(true, "所选歌单的歌曲文件均无法播放，请重新选择音乐")
    }

    // 检查路径是否可读（content URI 用 openFileDescriptor，裸路径用 File）
    private fun isPathPlayable(context: Context, path: String): Boolean {
        return try {
            when {
                path.startsWith("content://") -> {
                    context.contentResolver.openFileDescriptor(Uri.parse(path), "r")?.use { true } ?: false
                }
                path.startsWith("/") -> File(path).canRead()
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun loadAlarm(alarmId: Long) {
        viewModelScope.launch {
            val loadedAlarm = alarmRepository.getAlarm(alarmId)
            if (loadedAlarm != null) {
                _alarm.value = loadedAlarm
            }
        }
    }
}
