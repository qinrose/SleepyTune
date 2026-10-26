package com.sleepytune.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.LruCache
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 音乐播放前台服务。
 *
 * 存在的唯一目的：让"音乐库 / 歌单页"的播放拥有一个常驻的通知栏控制条，
 * 并且用户可以直接在通知栏暂停、切歌、关闭。
 *
 * 播放本体在 [MusicPlayerManager]（单例）里，服务只做三件事：
 * 1. 把播放状态同步成通知（MediaStyle + 播放/暂停/上一首/下一首/关闭）；
 * 2. 接收通知按钮的动作并转交给播放引擎；
 * 3. 播放结束时收掉通知和前台状态。
 */
@AndroidEntryPoint
class MusicPlaybackService : Service() {

    companion object {
        private const val TAG = "MusicPlaybackService"

        const val ACTION_TOGGLE = "com.sleepytune.app.MUSIC_TOGGLE"
        const val ACTION_NEXT = "com.sleepytune.app.MUSIC_NEXT"
        const val ACTION_PREVIOUS = "com.sleepytune.app.MUSIC_PREVIOUS"
        const val ACTION_STOP = "com.sleepytune.app.MUSIC_STOP"

        fun stopService(context: Context) {
            runCatching { context.stopService(Intent(context, MusicPlaybackService::class.java)) }
        }
    }

    @Inject lateinit var playerManager: MusicPlayerManager
    @Inject lateinit var notificationHelper: NotificationHelper

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val artCache = LruCache<String, Bitmap>(6)
    private var inForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 状态变化 -> 通知。只在"歌曲 / 播放中"变化时重建，避免 500ms 一次的无谓刷新
        scope.launch {
            playerManager.state
                .distinctUntilChangedBy { Triple(it.track?.id, it.isPlaying, it.queueSize) }
                // 首个发射是"当前状态"（通常为空）。空状态交给 onStartCommand 判断，
                // 否则服务刚创建就会被自己关掉，通知永远出不来。
                .drop(1)
                .collect { state -> syncNotification(state) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> playerManager.togglePlayPause()
            ACTION_NEXT -> playerManager.next()
            ACTION_PREVIOUS -> playerManager.previous()
            ACTION_STOP -> playerManager.stop()
        }

        val state = playerManager.state.value
        if (!state.isActive) {
            // 没有播放内容：不进入前台，直接收尾，避免"空通知"
            shutdown()
            return START_NOT_STICKY
        }
        syncNotification(state)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (inForeground) {
            inForeground = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        // 服务被系统回收时，播放也不该继续"哑跑"
        if (playerManager.state.value.isActive) playerManager.stop()
        super.onDestroy()
    }

    private fun syncNotification(state: MusicPlayerManager.PlaybackState) {
        val track = state.track
        if (track == null) {
            shutdown()
            return
        }

        scope.launch {
            val largeIcon = withContext(Dispatchers.IO) { loadArtwork(track.albumArtUri) }
            val notification = notificationHelper.buildMusicNotification(
                title = track.title.ifBlank { track.path.substringAfterLast('/') },
                artist = track.artist,
                isPlaying = state.isPlaying,
                largeIcon = largeIcon,
                index = state.index + 1,
                total = state.queueSize
            )
            if (!inForeground) {
                try {
                    ServiceCompat.startForeground(
                        this@MusicPlaybackService,
                        NotificationHelper.NOTIFICATION_ID_MUSIC,
                        notification,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        } else {
                            0
                        }
                    )
                    inForeground = true
                } catch (e: Exception) {
                    Log.e(TAG, "进入前台失败: ${e.message}", e)
                }
            } else {
                notificationHelper.notifyMusic(notification)
            }
        }
    }

    private fun shutdown() {
        notificationHelper.cancelMusicNotification()
        if (inForeground) {
            inForeground = false
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    /** 读取专辑封面，失败就返回 null（通知里只显示文字，不影响功能） */
    private fun loadArtwork(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        artCache.get(uriString)?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(Uri.parse(uriString))?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / sample > 512) sample *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = contentResolver.openInputStream(Uri.parse(uriString))?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            bitmap?.let { artCache.put(uriString, it) }
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "读取封面失败: ${e.message}")
            null
        }
    }
}
