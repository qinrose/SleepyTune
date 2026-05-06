package com.example.sleepytune.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleepytune.data.local.entity.Alarm
import com.example.sleepytune.data.local.entity.Playlist
import com.example.sleepytune.data.repository.AlarmRepository
import com.example.sleepytune.data.repository.PlaylistRepository
import com.example.sleepytune.service.AlarmScheduler
import com.example.sleepytune.utils.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlarmEditViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val playlistRepository: PlaylistRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {

    private val _alarm = MutableStateFlow(Alarm(hour = 8, minute = 0, playlistId = 0L))
    val alarm: StateFlow<Alarm> = _alarm.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun saveAlarm(context: android.content.Context? = null) {
        val alarmToSave = _alarm.value

        if (context != null && !PermissionUtils.checkExactAlarmPermission(context)) {
            PermissionUtils.requestExactAlarmPermission(context) {
                doSaveAlarm(alarmToSave)
            }
            return
        }

        doSaveAlarm(alarmToSave)
    }

    private fun doSaveAlarm(alarmToSave: Alarm) {
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                android.util.Log.e("AlarmEditVM", "保存闹钟失败: ${e.message}", e)
            }
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
