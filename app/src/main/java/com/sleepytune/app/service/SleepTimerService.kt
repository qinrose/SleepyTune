package com.sleepytune.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
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
class SleepTimerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationHelper: NotificationHelper,
    private val volumeManager: VolumeManager
) {
    companion object {
        private const val TAG = "SleepTimerService"
        const val ACTION_START = "com.sleepytune.app.SLEEP_TIMER_START"
        const val ACTION_CANCEL = "com.sleepytune.app.SLEEP_TIMER_CANCEL"
        
        // 预设时间选项(分钟)
        val PRESET_TIMES = listOf(15, 30, 45, 60, 90, 120)
    }
    
    private var timerJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    
    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()
    
    sealed class TimerState {
        object Idle : TimerState()
        data class Running(val remainingMinutes: Int, val totalMinutes: Int) : TimerState()
        data class Paused(val remainingMinutes: Int, val totalMinutes: Int) : TimerState()
        object Finished : TimerState()
    }
    
    // 开始睡眠定时器
    fun startTimer(
        durationMinutes: Int,
        fadeOutEnabled: Boolean = true,
        fadeOutDurationMs: Long = 30_000L
    ) {
        // 取消之前的定时器
        cancelTimer()
        
        _timerState.value = TimerState.Running(durationMinutes, durationMinutes)
        
        // 显示通知
        updateNotification(durationMinutes)
        
        timerJob = CoroutineScope(Dispatchers.Default).launch {
            var remainingSeconds = durationMinutes * 60
            
            while (remainingSeconds > 0 && isActive) {
                delay(1000)
                remainingSeconds--
                
                val remainingMinutes = (remainingSeconds + 59) / 60 // 向上取整
                
                // 更新状态
                _timerState.value = TimerState.Running(remainingMinutes, durationMinutes)
                
                // 每分钟更新通知
                if (remainingSeconds % 60 == 0) {
                    updateNotification(remainingMinutes)
                }
                
                // 最后30秒开始渐弱
                if (fadeOutEnabled && remainingSeconds <= 30 && remainingSeconds > 0) {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            // 渐弱音量
                            val progress = remainingSeconds / 30f
                            val volume = progress * volumeManager.getAlarmVolume()
                            player.setVolume(volume, volume)
                        }
                    }
                }
            }
            
            // 定时结束
            if (isActive) {
                stopPlayback()
                _timerState.value = TimerState.Finished
                notificationHelper.cancelSleepTimerNotification()
                Log.i(TAG, "睡眠定时器结束")
            }
        }
        
        Log.i(TAG, "睡眠定时器启动: ${durationMinutes}分钟")
    }
    
    // 暂停定时器
    fun pauseTimer() {
        timerJob?.cancel()
        val currentState = _timerState.value
        if (currentState is TimerState.Running) {
            _timerState.value = TimerState.Paused(currentState.remainingMinutes, currentState.totalMinutes)
            Log.i(TAG, "睡眠定时器暂停")
        }
    }
    
    // 恢复定时器
    fun resumeTimer() {
        val currentState = _timerState.value
        if (currentState is TimerState.Paused) {
            startTimer(currentState.remainingMinutes, fadeOutEnabled = false)
            Log.i(TAG, "睡眠定时器恢复")
        }
    }
    
    // 取消定时器
    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerState.value = TimerState.Idle
        notificationHelper.cancelSleepTimerNotification()
        Log.i(TAG, "睡眠定时器取消")
    }
    
    // 设置播放器引用(用于渐弱)
    fun setMediaPlayer(player: MediaPlayer?) {
        mediaPlayer = player
    }
    
    // 停止播放
    private fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.w(TAG, "停止播放异常: ${e.message}")
        }
    }
    
    // 更新通知
    private fun updateNotification(remainingMinutes: Int) {
        val notification = notificationHelper.buildSleepTimerNotification(remainingMinutes)
        
        // 使用系统通知管理器发送通知
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NotificationHelper.NOTIFICATION_ID_SLEEP_TIMER, notification)
    }
    
    // 获取剩余时间
    fun getRemainingMinutes(): Int {
        return when (val state = _timerState.value) {
            is TimerState.Running -> state.remainingMinutes
            is TimerState.Paused -> state.remainingMinutes
            else -> 0
        }
    }
    
    // 是否正在运行
    fun isRunning(): Boolean {
        return _timerState.value is TimerState.Running
    }
}
