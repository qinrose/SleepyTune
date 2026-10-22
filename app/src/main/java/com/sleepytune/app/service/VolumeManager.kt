package com.sleepytune.app.service

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VolumeManager"
        private const val PREFS_NAME = "volume_prefs"
        private const val KEY_ALARM_VOLUME = "alarm_volume"
        private const val KEY_FADE_IN_ENABLED = "fade_in_enabled"
        private const val KEY_FADE_IN_DURATION = "fade_in_duration"
        
        const val DEFAULT_VOLUME = 1.0f
        const val DEFAULT_FADE_IN_DURATION = 30_000L // 30秒
        const val FADE_IN_STEP_DURATION = 500L // 每500ms增加一次
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var fadeInJob: Job? = null
    
    // 获取保存的闹钟音量 (0.0 - 1.0)
    fun getAlarmVolume(): Float {
        return prefs.getFloat(KEY_ALARM_VOLUME, DEFAULT_VOLUME)
    }
    
    // 保存闹钟音量
    fun setAlarmVolume(volume: Float) {
        prefs.edit().putFloat(KEY_ALARM_VOLUME, volume.coerceIn(0f, 1f)).apply()
        Log.i(TAG, "保存闹钟音量: $volume")
    }
    
    // 是否启用音量渐强
    fun isFadeInEnabled(): Boolean {
        return prefs.getBoolean(KEY_FADE_IN_ENABLED, true)
    }
    
    // 设置是否启用音量渐强
    fun setFadeInEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FADE_IN_ENABLED, enabled).apply()
        Log.i(TAG, "音量渐强: ${if (enabled) "启用" else "禁用"}")
    }
    
    // 获取渐强时长(毫秒)
    fun getFadeInDuration(): Long {
        return prefs.getLong(KEY_FADE_IN_DURATION, DEFAULT_FADE_IN_DURATION)
    }
    
    // 设置渐强时长
    fun setFadeInDuration(durationMs: Long) {
        prefs.edit().putLong(KEY_FADE_IN_DURATION, durationMs).apply()
        Log.i(TAG, "渐强时长: ${durationMs}ms")
    }
    
    // 开始音量渐强
    fun startFadeIn(
        mediaPlayer: MediaPlayer,
        targetVolume: Float = getAlarmVolume(),
        durationMs: Long = getFadeInDuration(),
        onVolumeChanged: ((Float) -> Unit)? = null
    ) {
        // 取消之前的渐强任务
        stopFadeIn()
        
        val steps = (durationMs / FADE_IN_STEP_DURATION).toInt()
        val volumeStep = targetVolume / steps
        
        fadeInJob = CoroutineScope(Dispatchers.Main).launch {
            var currentVolume = 0f
            
            Log.i(TAG, "开始音量渐强: targetVolume=$targetVolume, steps=$steps, stepVolume=$volumeStep")
            
            repeat(steps) { step ->
                if (!isActive) return@launch
                
                currentVolume = (volumeStep * (step + 1)).coerceAtMost(targetVolume)
                
                try {
                    mediaPlayer.setVolume(currentVolume, currentVolume)
                    onVolumeChanged?.invoke(currentVolume)
                    Log.d(TAG, "音量渐强: step=${step + 1}/$steps, volume=$currentVolume")
                } catch (e: Exception) {
                    Log.e(TAG, "设置音量失败: ${e.message}")
                }
                
                delay(FADE_IN_STEP_DURATION)
            }
            
            Log.i(TAG, "音量渐强完成: finalVolume=$currentVolume")
        }
    }
    
    // 停止音量渐强
    fun stopFadeIn() {
        fadeInJob?.cancel()
        fadeInJob = null
        Log.i(TAG, "停止音量渐强")
    }
    
    // 音量渐弱
    fun fadeOut(
        mediaPlayer: MediaPlayer,
        durationMs: Long = 3000L,
        onComplete: () -> Unit
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            val steps = (durationMs / FADE_IN_STEP_DURATION).toInt()
            val currentVolume = getAlarmVolume()
            val volumeStep = currentVolume / steps
            
            repeat(steps) { step ->
                if (!isActive) return@launch
                
                val newVolume = (currentVolume - volumeStep * (step + 1)).coerceAtLeast(0f)
                
                try {
                    mediaPlayer.setVolume(newVolume, newVolume)
                } catch (e: Exception) {
                    Log.e(TAG, "渐弱设置音量失败: ${e.message}")
                }
                
                delay(FADE_IN_STEP_DURATION)
            }
            
            onComplete()
            Log.i(TAG, "音量渐弱完成")
        }
    }
    
    // 获取系统闹钟音量 (用于显示)
    fun getSystemAlarmVolume(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
    }
    
    // 获取系统最大闹钟音量
    fun getSystemMaxAlarmVolume(): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    }
    
    // 音量百分比转系统音量
    fun volumePercentToSystem(percent: Float): Int {
        val maxVolume = getSystemMaxAlarmVolume()
        return (percent * maxVolume).toInt().coerceIn(0, maxVolume)
    }
}
