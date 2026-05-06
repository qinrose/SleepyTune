package com.example.sleepytune.service

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoStopManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val volumeManager: VolumeManager
) {
    companion object {
        private const val TAG = "AutoStopManager"
        
        // 预设持续时间选项(分钟)
        val PRESET_DURATIONS = listOf(1, 3, 5, 10, 15, 30)
        const val DEFAULT_DURATION_MINUTES = 5
    }
    
    private var autoStopJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    
    private val _autoStopState = MutableStateFlow<AutoStopState>(AutoStopState.Idle)
    val autoStopState: StateFlow<AutoStopState> = _autoStopState.asStateFlow()
    
    sealed class AutoStopState {
        object Idle : AutoStopState()
        data class Running(val remainingSeconds: Int, val totalSeconds: Int) : AutoStopState()
        object Finished : AutoStopState()
    }
    
    // 开始自动停止计时
    fun startAutoStopTimer(
        durationMinutes: Int,
        player: MediaPlayer,
        onFinished: () -> Unit = {}
    ) {
        // 取消之前的计时
        cancelAutoStop()
        
        mediaPlayer = player
        val totalSeconds = durationMinutes * 60
        
        _autoStopState.value = AutoStopState.Running(totalSeconds, totalSeconds)
        
        autoStopJob = CoroutineScope(Dispatchers.Default).launch {
            var remainingSeconds = totalSeconds
            
            while (remainingSeconds > 0 && isActive) {
                delay(1000)
                remainingSeconds--
                
                // 更新状态
                _autoStopState.value = AutoStopState.Running(remainingSeconds, totalSeconds)
                
                // 最后30秒开始渐弱
                if (remainingSeconds <= 30 && remainingSeconds > 0) {
                    try {
                        val progress = remainingSeconds / 30f
                        val volume = progress * volumeManager.getAlarmVolume()
                        mediaPlayer?.setVolume(volume, volume)
                        Log.d(TAG, "渐弱音量: progress=$progress, volume=$volume")
                    } catch (e: Exception) {
                        Log.w(TAG, "设置音量失败: ${e.message}")
                    }
                }
            }
            
            // 计时结束
            if (isActive) {
                _autoStopState.value = AutoStopState.Finished
                onFinished()
                Log.i(TAG, "闹钟自动停止")
            }
        }
        
        Log.i(TAG, "自动停止计时启动: ${durationMinutes}分钟")
    }
    
    // 取消自动停止
    fun cancelAutoStop() {
        autoStopJob?.cancel()
        autoStopJob = null
        _autoStopState.value = AutoStopState.Idle
        Log.i(TAG, "自动停止计时取消")
    }
    
    // 获取剩余时间(秒)
    fun getRemainingSeconds(): Int {
        return when (val state = _autoStopState.value) {
            is AutoStopState.Running -> state.remainingSeconds
            else -> 0
        }
    }
    
    // 是否正在运行
    fun isRunning(): Boolean {
        return _autoStopState.value is AutoStopState.Running
    }
    
    // 格式化剩余时间
    fun formatRemainingTime(): String {
        val seconds = getRemainingSeconds()
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", minutes, secs)
    }
}
