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
    
    // 闹钟是否正在播放
    private val _isAlarmPlaying = MutableStateFlow(false)
    val isAlarmPlaying: StateFlow<Boolean> = _isAlarmPlaying.asStateFlow()
    
    // 当前闹钟ID
    private val _currentAlarmId = MutableStateFlow(-1L)
    val currentAlarmId: StateFlow<Long> = _currentAlarmId.asStateFlow()
    
    // 闹钟标签
    private val _alarmLabel = MutableStateFlow("")
    val alarmLabel: StateFlow<String> = _alarmLabel.asStateFlow()
    
    // 设置闹钟播放状态
    fun setAlarmPlaying(isPlaying: Boolean, alarmId: Long = -1L, label: String = "") {
        _isAlarmPlaying.value = isPlaying
        _currentAlarmId.value = alarmId
        _alarmLabel.value = label
    }
    
    // 停止闹钟
    fun stopAlarm(context: Context) {
        val intent = Intent(context, AlarmPlaybackService::class.java)
        context.stopService(intent)
        
        // 清除贪睡状态
        val snoozeIntent = Intent("com.example.sleepytune.CLEAR_SNOOZE")
        context.sendBroadcast(snoozeIntent)
        
        _isAlarmPlaying.value = false
        _currentAlarmId.value = -1L
        _alarmLabel.value = ""
    }
    
    // 清除状态
    fun clearState() {
        _isAlarmPlaying.value = false
        _currentAlarmId.value = -1L
        _alarmLabel.value = ""
    }
}
