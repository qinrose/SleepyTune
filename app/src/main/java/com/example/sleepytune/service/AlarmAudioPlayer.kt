package com.example.sleepytune.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

// 闹钟音频播放器（完整状态机，避免时序混乱）
@Singleton
class AlarmAudioPlayer @Inject constructor(
    private val volumeManager: VolumeManager
) {
    companion object {
        private const val TAG = "AlarmAudioPlayer"
        private const val VOLUME_FACTOR = 0.8f
        private const val TIMEOUT_MS = 10_000L
    }

    // 完整状态机
    enum class State { IDLE, PREPARING, PLAYING, STOPPING }
    private val state = AtomicReference(State.IDLE)
    private var failureCount = 0
    
    private var mediaPlayer: MediaPlayer? = null
    private var timeoutJob: Job? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 回调
    private var onCompleted: (() -> Unit)? = null
    private var onFailed: (() -> Unit)? = null

    fun setCallbacks(completed: () -> Unit, failed: () -> Unit) {
        onCompleted = completed
        onFailed = failed
    }

    // 播放本地文件
    fun playLocalFile(context: Context, path: String, config: AlarmConfig, fadeIn: Boolean, loop: Boolean = false) {
        mainScope.launch { doPlayLocalFile(context, path, config, fadeIn, loop) }
    }

    // 播放默认铃声
    fun playDefaultRingtone(context: Context, config: AlarmConfig, fadeIn: Boolean) {
        mainScope.launch { doPlayDefaultRingtone(context, config, fadeIn) }
    }

    // 实际播放逻辑
    private fun doPlayLocalFile(context: Context, path: String, config: AlarmConfig, fadeIn: Boolean, loop: Boolean) {
        // 状态检查：只有 IDLE 和 STOPPING 可以开始播放
        val current = state.get()
        if (current == State.PREPARING || current == State.PLAYING) {
            Log.w(TAG, "状态=$current，忽略播放请求")
            return
        }
        
        // 等待 STOPPING 完成
        if (current == State.STOPPING) {
            Log.w(TAG, "正在停止中，稍后重试")
            mainScope.launch {
                delay(100)
                doPlayLocalFile(context, path, config, fadeIn, loop)
            }
            return
        }
        
        state.set(State.PREPARING)
        releaseInternal()
        failureCount = 0
        
        try {
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(buildAudioAttributes())
                setDataSource(path)
                isLooping = loop
            }
            setupListeners(config, fadeIn, loop)
            mediaPlayer?.prepareAsync()
            startTimeout()
            Log.i(TAG, "开始准备: $path")
        } catch (e: Exception) {
            Log.e(TAG, "播放失败: ${e.message}")
            handleError()
        }
    }

    // 播放默认铃声
    private fun doPlayDefaultRingtone(context: Context, config: AlarmConfig, fadeIn: Boolean) {
        val current = state.get()
        if (current == State.PREPARING || current == State.PLAYING) {
            Log.w(TAG, "状态=$current，忽略")
            return
        }
        
        state.set(State.PREPARING)
        releaseInternal()
        
        try {
            val uri = Settings.System.DEFAULT_ALARM_ALERT_URI
            mediaPlayer = MediaPlayer().apply {
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setAudioAttributes(buildAudioAttributes(true))
                setDataSource(context, uri)
                isLooping = true
                prepare()
            }
            applyVolume(config, fadeIn)
            mediaPlayer?.start()
            state.set(State.PLAYING)
            Log.i(TAG, "播放默认铃声")
        } catch (e: Exception) {
            Log.e(TAG, "默认铃声失败: ${e.message}")
            handleError()
        }
    }

    // 统一设置监听器
    private fun setupListeners(config: AlarmConfig, fadeIn: Boolean, loop: Boolean) {
        mediaPlayer?.apply {
            setOnPreparedListener { mp ->
                cancelTimeout()
                state.set(State.PLAYING)
                applyVolume(config, fadeIn)
                mp.start()
                Log.i(TAG, "播放开始")
            }
            setOnCompletionListener {
                cancelTimeout()
                volumeManager.stopFadeIn()
                state.set(State.IDLE)
                if (!loop) onCompleted?.invoke()
            }
            setOnErrorListener { _, what, extra ->
                cancelTimeout()
                Log.e(TAG, "播放错误: $what, $extra")
                handleError()
                true
            }
        }
    }

    // 应用音量（安全调用）
    private fun applyVolume(config: AlarmConfig, fadeIn: Boolean) {
        val mp = mediaPlayer
        if (mp == null) {
            Log.e(TAG, "applyVolume: mediaPlayer is null")
            return
        }
        
        val target = calculateVolume(config)
        if (fadeIn && config.fadeInEnabled) {
            mp.setVolume(0.1f, 0.1f)
            volumeManager.startFadeIn(mp, target, config.fadeInDuration * 1000L)
        } else {
            mp.setVolume(target, target)
        }
    }

    // 计算音量
    private fun calculateVolume(config: AlarmConfig): Float {
        val base = if (config.followSystemVolume) {
            val max = volumeManager.getSystemMaxAlarmVolume()
            val cur = volumeManager.getSystemAlarmVolume()
            if (max > 0) cur.toFloat() / max else 1.0f
        } else config.volume.coerceIn(0f, 1f)
        return base * VOLUME_FACTOR
    }

    // 超时保护
    private fun startTimeout() {
        timeoutJob = mainScope.launch {
            delay(TIMEOUT_MS)
            Log.w(TAG, "准备超时")
            handleError()
        }
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    // 错误处理
    private fun handleError() {
        state.set(State.IDLE)
        failureCount++
        releaseInternal()
        onFailed?.invoke()
    }

    // 停止播放（同步执行）
    fun stop() {
        // 直接在主线程同步执行，避免竞态
        if (state.get() == State.IDLE) return
        
        state.set(State.STOPPING)
        cancelTimeout()
        volumeManager.stopFadeIn()
        
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止错误: ${e.message}")
        }
        
        mediaPlayer = null
        state.set(State.IDLE)
        Log.i(TAG, "已停止")
    }

    // 内部释放
    private fun releaseInternal() {
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    // 外部释放
    fun release() {
        cancelTimeout()
        releaseInternal()
        state.set(State.IDLE)
    }

    fun isPlaying(): Boolean = state.get() == State.PLAYING
    fun getFailures(): Int = failureCount
    fun resetFailures() { failureCount = 0 }
    fun getState(): State = state.get()

    private fun buildAudioAttributes(isRingtone: Boolean = false) = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(if (isRingtone) AudioAttributes.CONTENT_TYPE_SONIFICATION else AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
}