package com.sleepytune.app.service

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音乐播放引擎（单例）。
 *
 * 负责：队列、播放/暂停、上一首/下一首、进度。
 * 通知栏由 [MusicPlaybackService] 承载，窗口界面由 NowPlayingBar 承载，
 * 两者读取同一份 [state]，因此音乐库页和歌单页的播放状态天然一致。
 */
@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MusicPlayerManager"

        /** 进度刷新间隔：500ms 足够让进度条平滑，又不会过度唤醒 */
        private const val PROGRESS_INTERVAL_MS = 500L

        /** 按下"上一首"时，播放超过这个时长就回到本曲开头（和主流播放器一致） */
        private const val RESTART_THRESHOLD_MS = 3_000L
    }

    /** 队列中的一首歌，只保留播放和展示需要的字段 */
    data class Track(
        val id: Long,
        val path: String,
        val title: String,
        val artist: String,
        val albumArtUri: String? = null,
        val durationMs: Long = 0L
    )

    data class PlaybackState(
        val track: Track? = null,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val queueSize: Int = 0,
        val index: Int = -1
    ) {
        val isActive: Boolean get() = track != null
        val hasNext: Boolean get() = index >= 0 && index < queueSize - 1
        val hasPrevious: Boolean get() = index > 0
        val progress: Float
            get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var player: MediaPlayer? = null
    private var queue: List<Track> = emptyList()
    private var currentIndex: Int = -1
    private var progressJob: Job? = null

    /** 播放队列，从 [startIndex] 开始 */
    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        queue = tracks
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        currentIndex = safeIndex
        startTrack(safeIndex)
    }

    /** 播放 / 暂停切换 */
    fun togglePlayPause() {
        val track = _state.value.track
        if (track == null) {
            // 队列还在，说明是"停止后再次播放"，从头恢复
            if (queue.isNotEmpty() && currentIndex >= 0) startTrack(currentIndex)
            return
        }
        if (_state.value.isPlaying) pause() else resume()
    }

    fun pause() {
        val mp = player ?: return
        if (mp.isPlaying) {
            runCatching { mp.pause() }
            stopProgressTracking()
            _state.value = _state.value.copy(
                isPlaying = false,
                positionMs = currentPositionSafely(mp)
            )
            Log.i(TAG, "已暂停: ${_state.value.track?.title}")
        }
    }

    fun resume() {
        val mp = player ?: return
        runCatching { mp.start() }
        _state.value = _state.value.copy(isPlaying = true, isBuffering = false)
        startProgressTracking()
        Log.i(TAG, "继续播放: ${_state.value.track?.title}")
    }

    /** 下一首；已经是最后一首时停止播放 */
    fun next() {
        if (queue.isEmpty()) return
        if (currentIndex >= queue.lastIndex) {
            stop()
            return
        }
        currentIndex += 1
        startTrack(currentIndex)
    }

    /** 上一首；播放超过 3 秒则先回到本曲开头 */
    fun previous() {
        if (queue.isEmpty()) return
        val mp = player
        if (mp != null && currentPositionSafely(mp) > RESTART_THRESHOLD_MS) {
            seekTo(0L)
            return
        }
        if (currentIndex <= 0) {
            seekTo(0L)
            return
        }
        currentIndex -= 1
        startTrack(currentIndex)
    }

    fun seekTo(positionMs: Long) {
        val mp = player ?: return
        runCatching {
            mp.seekTo(positionMs.toInt())
            _state.value = _state.value.copy(positionMs = positionMs)
        }
    }

    /** 彻底停止：释放播放器、清空状态、关闭通知 */
    fun stop() {
        stopProgressTracking()
        releasePlayer()
        _state.value = PlaybackState()
        // 通知由 MusicPlaybackService 观察 state 后自行移除，这里补一刀保证不会残留
        runCatching {
            context.stopService(Intent(context, MusicPlaybackService::class.java))
        }
        Log.i(TAG, "播放已停止")
    }

    /** 当前是否有活动播放会话（用于判断是否显示迷你播放条） */
    fun isActive(): Boolean = _state.value.isActive

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private fun startTrack(index: Int) {
        val track = queue.getOrNull(index) ?: return
        stopProgressTracking()
        releasePlayer()

        _state.value = PlaybackState(
            track = track,
            isPlaying = false,
            isBuffering = true,
            positionMs = 0L,
            durationMs = if (track.durationMs > 0) track.durationMs else 0L,
            queueSize = queue.size,
            index = index
        )

        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            if (track.path.startsWith("content://") || track.path.startsWith("file://")) {
                mp.setDataSource(context, Uri.parse(track.path))
            } else {
                mp.setDataSource(track.path)
            }
            mp.setOnPreparedListener { prepared ->
                val duration = runCatching { prepared.duration.toLong() }.getOrDefault(0L)
                _state.value = _state.value.copy(
                    isPlaying = true,
                    isBuffering = false,
                    durationMs = if (duration > 0) duration else track.durationMs
                )
                runCatching { prepared.start() }
                startProgressTracking()
                Log.i(TAG, "开始播放: ${track.title}")
            }
            mp.setOnCompletionListener {
                if (currentIndex < queue.lastIndex) {
                    currentIndex += 1
                    startTrack(currentIndex)
                } else {
                    stop()
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "播放失败: what=$what extra=$extra path=${track.path}")
                stop()
                true
            }
            mp.prepareAsync()

            // 通知栏跟随播放会话出现
            startPlaybackService()
        } catch (e: IOException) {
            Log.e(TAG, "无法读取文件: ${track.path}, ${e.message}")
            stop()
        } catch (e: Exception) {
            Log.e(TAG, "播放异常: ${e.message}", e)
            stop()
        }
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                val mp = player ?: break
                val position = currentPositionSafely(mp)
                val duration = runCatching { mp.duration.toLong() }.getOrDefault(0L)
                _state.value = _state.value.copy(
                    positionMs = position,
                    durationMs = if (duration > 0) duration else _state.value.durationMs
                )
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun currentPositionSafely(mp: MediaPlayer): Long =
        runCatching { mp.currentPosition.toLong() }.getOrDefault(0L)

    private fun releasePlayer() {
        player?.let { mp ->
            runCatching {
                mp.setOnPreparedListener(null)
                mp.setOnCompletionListener(null)
                mp.setOnErrorListener(null)
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.reset() }
            runCatching { mp.release() }
        }
        player = null
    }

    private fun startPlaybackService() {
        val intent = Intent(context, MusicPlaybackService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }.onFailure { Log.w(TAG, "启动播放服务失败: ${it.message}") }
    }

    /** 进程结束时释放资源（不停止服务，服务会自行收尾） */
    fun release() {
        stopProgressTracking()
        releasePlayer()
        _state.value = PlaybackState()
    }
}
