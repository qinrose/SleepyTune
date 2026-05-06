package com.example.sleepytune.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MusicPlayerManager"
        private const val PREVIEW_DURATION_MS = 30_000L // 预览30秒
    }
    
    private var previewPlayer: MediaPlayer? = null
    private var previewJob: Job? = null
    
    private val _previewState = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val previewState: StateFlow<PreviewState> = _previewState.asStateFlow()
    
    sealed class PreviewState {
        object Idle : PreviewState()
        data class Loading(val path: String) : PreviewState()
        data class Playing(val path: String, val progress: Float = 0f) : PreviewState()
        data class Error(val message: String) : PreviewState()
    }
    
    // 预览音乐文件
    fun playPreview(
        path: String,
        durationMs: Long = PREVIEW_DURATION_MS,
        volume: Float = 1.0f
    ) {
        // 停止之前的预览
        stopPreview()
        
        _previewState.value = PreviewState.Loading(path)
        
        try {
            previewPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                
                // 检查是文件路径还是URI
                if (path.startsWith("content://") || path.startsWith("file://")) {
                    setDataSource(context, Uri.parse(path))
                } else {
                    setDataSource(path)
                }
                
                setVolume(volume, volume)
                prepareAsync()
                
                setOnPreparedListener { mp ->
                    mp.start()
                    _previewState.value = PreviewState.Playing(path, 0f)
                    Log.i(TAG, "开始预览: $path")
                    
                    // 启动进度更新和自动停止
                    startPreviewProgressTracker(path, durationMs)
                }
                
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "预览播放错误: what=$what, extra=$extra")
                    _previewState.value = PreviewState.Error("播放失败")
                    true
                }
                
                setOnCompletionListener {
                    _previewState.value = PreviewState.Idle
                    Log.i(TAG, "预览完成")
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "无法播放文件: $path, ${e.message}")
            _previewState.value = PreviewState.Error("无法播放文件")
        } catch (e: Exception) {
            Log.e(TAG, "预览异常: ${e.message}", e)
            _previewState.value = PreviewState.Error(e.message ?: "未知错误")
        }
    }
    
    // 启动进度追踪
    private fun startPreviewProgressTracker(path: String, durationMs: Long) {
        previewJob = CoroutineScope(Dispatchers.Main).launch {
            val startTime = System.currentTimeMillis()
            
            while (true) {
                delay(100)
                
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                
                // 更新进度
                _previewState.value = PreviewState.Playing(path, progress)
                
                // 超时自动停止
                if (elapsed >= durationMs) {
                    stopPreview()
                    break
                }
            }
        }
    }
    
    // 停止预览
    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "停止预览异常: ${e.message}")
        }
        previewPlayer = null
        
        _previewState.value = PreviewState.Idle
        Log.i(TAG, "预览已停止")
    }
    
    // 暂停预览
    fun pausePreview() {
        try {
            previewPlayer?.pause()
            Log.i(TAG, "预览已暂停")
        } catch (e: Exception) {
            Log.w(TAG, "暂停预览异常: ${e.message}")
        }
    }
    
    // 恢复预览
    fun resumePreview() {
        try {
            previewPlayer?.start()
            Log.i(TAG, "预览已恢复")
        } catch (e: Exception) {
            Log.w(TAG, "恢复预览异常: ${e.message}")
        }
    }
    
    // 是否正在播放
    fun isPlaying(): Boolean {
        return previewPlayer?.isPlaying == true
    }
    
    // 获取当前播放位置
    fun getCurrentPosition(): Int {
        return previewPlayer?.currentPosition ?: 0
    }
    
    // 获取总时长
    fun getDuration(): Int {
        return previewPlayer?.duration ?: 0
    }
    
    // 跳转到指定位置
    fun seekTo(positionMs: Int) {
        try {
            previewPlayer?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.w(TAG, "跳转异常: ${e.message}")
        }
    }
    
    // 清理资源
    fun release() {
        stopPreview()
        Log.i(TAG, "MusicPlayerManager 资源已释放")
    }
}
