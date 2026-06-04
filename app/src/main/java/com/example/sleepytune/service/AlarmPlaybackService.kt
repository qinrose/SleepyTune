package com.example.sleepytune.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// 闹钟播放服务（协调者，约100行）
@AndroidEntryPoint
class AlarmPlaybackService : Service() {

    companion object {
        private const val TAG = "AlarmPlaybackService"
        const val ACTION_STOP_ALARM = "com.example.sleepytune.ACTION_STOP_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.sleepytune.SNOOZE"
        const val ACTION_NEXT_SONG = "com.example.sleepytune.ACTION_NEXT_SONG"
        const val ACTION_STOP_VIBRATE = "com.example.sleepytune.ACTION_STOP_VIBRATE"
        const val ACTION_PAUSE_VIBRATE = "com.example.sleepytune.ACTION_PAUSE_VIBRATE"
        const val ACTION_STOP = "com.example.sleepytune.ACTION_STOP"
        const val ACTION_SNOOZE = "com.example.sleepytune.ACTION_SNOOZE"
        
        // 播放信息广播
        const val ACTION_UPDATE_PLAYBACK_INFO = "com.example.sleepytune.UPDATE_PLAYBACK_INFO"
        const val ACTION_SNOOZE_ENDED = "com.example.sleepytune.SNOOZE_ENDED"
        const val EXTRA_SONG_TITLE = "song_title"
        const val EXTRA_SONG_ARTIST = "song_artist"
        const val EXTRA_CURRENT_INDEX = "current_index"
        const val EXTRA_TOTAL_SONGS = "total_songs"
        
        @Volatile private var instance: AlarmPlaybackService? = null
        fun getInstance(): AlarmPlaybackService? = instance
        fun isPlaying(): Boolean = instance?.isPlaying ?: false
        fun stopAlarm() { instance?.stopAlarm() }
    }

    @Inject lateinit var audioPlayer: AlarmAudioPlayer
    @Inject lateinit var playlistManager: PlaylistManager
    @Inject lateinit var volumeManager: VolumeManager
    @Inject lateinit var alarmStateManager: AlarmStateManager
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var vibrationController: VibrationController
    @Inject lateinit var alarmSnoozeManager: AlarmSnoozeManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var config: AlarmConfig? = null
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "onCreate")
        
        // 立即显示通知（Android 8.0+ 要求）
        startForeground(NotificationHelper.NOTIFICATION_ID_ALARM, 
            notificationHelper.buildAlarmNotification("闹钟", true))
        
        acquireWakeLock()
        
        // 设置播放器回调
        audioPlayer.setCallbacks(
            completed = { playNextSong() },
            failed = { handlePlaybackFailure() }
        )
        
        // 设置歌单回调
        playlistManager.setOnSongChanged { song, index, total ->
            updateNotification(song.title, song.artist, index, total)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: action=${intent?.action}")
        
        // 处理操作命令
        intent?.action?.let { action ->
            when (action) {
                ACTION_STOP_ALARM, ACTION_STOP -> {
                    stopAlarm()
                    return START_NOT_STICKY
                }
                ACTION_NEXT_SONG -> {
                    playNextSong()
                    return START_NOT_STICKY  // 切歌后直接返回
                }
                ACTION_SNOOZE_ALARM -> {
                    snooze()
                    return START_NOT_STICKY
                }
                ACTION_STOP_VIBRATE -> vibrationController.stop()
                ACTION_PAUSE_VIBRATE -> vibrationController.pause()
            }
        }
        
        // 防止重复启动
        if (isPlaying) {
            Log.i(TAG, "已在播放，忽略重复请求")
            return START_NOT_STICKY
        }
        
        // 解析配置并启动播放
        intent?.let { startAlarm(AlarmConfig.fromIntent(it)) }
        
        return START_NOT_STICKY
    }

    // 启动闹钟
    private fun startAlarm(config: AlarmConfig) {
        this.config = config
        isPlaying = true
        
        Log.i(TAG, "闹钟参数: ${config}")
        
        // 持久化状态
        persistState(config)
        
        // 启动震动
        if (config.vibrate) {
            vibrationController.start()
        }
        
        // 加载并播放（audioPlayer 内部会切换到主线程）
        CoroutineScope(Dispatchers.IO).launch {
            val hasPlaylist = playlistManager.loadPlaylist(config)
            
            withContext(Dispatchers.Main) {
                if (hasPlaylist) {
                    playlistManager.getCurrentSong()?.let { song ->
                        audioPlayer.playLocalFile(applicationContext, song.path, config, true)
                        updateNotification(song.title, song.artist, 1, playlistManager.getSize())
                        // 发送广播更新 Activity 歌曲信息
                        sendPlaybackInfoBroadcast(song.title, song.artist)
                    }
                } else if (config.ringtonePath != null) {
                    audioPlayer.playLocalFile(applicationContext, config.ringtonePath, config, true, true)
                } else {
                    audioPlayer.playDefaultRingtone(applicationContext, config, true)
                }
            }
        }
    }

    // 播放下一首
    private fun playNextSong() {
        if (playlistManager.isEmpty()) return
        
        val c = config
        if (c == null) {
            Log.e(TAG, "playNextSong: config is null")
            return
        }
        
        // 先停止当前播放，再播放下一首
        audioPlayer.stop()
        
        playlistManager.nextSong()?.let { song ->
            audioPlayer.playLocalFile(applicationContext, song.path, c, false)
            // 发送广播更新 Activity 歌曲信息
            sendPlaybackInfoBroadcast(song.title, song.artist)
        }
    }

    // 处理播放失败
    private fun handlePlaybackFailure() {
        val c = config
        if (c == null) {
            Log.e(TAG, "handlePlaybackFailure: config is null")
            return
        }
        
        if (audioPlayer.getFailures() >= 3) {
            audioPlayer.playDefaultRingtone(applicationContext, c, false)
        } else {
            playNextSong()
        }
    }

    // 贪睡
    private fun snooze() {
        audioPlayer.stop()
        vibrationController.stop()
        config?.let { c ->
            alarmSnoozeManager.scheduleSnoozeAlarm(
                alarmId = c.alarmId,
                playlistId = c.playlistId,
                ringtone = c.ringtonePath,
                vibrate = c.vibrate,
                volume = c.volume,
                label = c.label,
                shuffleMode = c.shuffleMode,
                fadeInEnabled = c.fadeInEnabled,
                fadeInDuration = c.fadeInDuration,
                snoozeMinutes = c.snoozeMinutes
            )
            notificationHelper.showSnoozeNotification(c.label, c.snoozeMinutes)
        }
    }

    // 停止闹钟
    fun stopAlarm() {
        isPlaying = false
        audioPlayer.stop()
        vibrationController.stop()
        volumeManager.stopFadeIn()
        alarmStateManager.persistAlarmPlaying(applicationContext, false)
        alarmSnoozeManager.cancelSnooze()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // 持久化状态
    private fun persistState(config: AlarmConfig) {
        alarmStateManager.persistAlarmPlaying(applicationContext, true, config.alarmId, config.label)
        alarmStateManager.persistPlaylistId(applicationContext, config.playlistId)
        alarmStateManager.persistRingtonePath(applicationContext, config.ringtonePath)
        alarmStateManager.persistSnoozeEnabled(applicationContext, config.snoozeEnabled)
        alarmStateManager.persistAlarmParams(
            applicationContext,
            config.volume,
            config.vibrate,
            config.vibrateDelayMinutes,
            config.shuffleMode,
            config.fadeInEnabled,
            config.fadeInDuration,
            config.snoozeMinutes
        )
    }

    // 更新通知
    private fun updateNotification(title: String, artist: String, index: Int, total: Int) {
        val notification = notificationHelper.buildAlarmNotification(
            alarmLabel = config?.label ?: "闹钟",
            isPlaying = true,
            songTitle = title,
            songIndex = index,
            songTotal = total
        )
        startForeground(NotificationHelper.NOTIFICATION_ID_ALARM, notification)
    }

    // 获取 WakeLock
    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepyTune:AlarmPlayback")
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    // 发送广播更新 Activity 歌曲信息
    private fun sendPlaybackInfoBroadcast(title: String, artist: String) {
        val intent = Intent(ACTION_UPDATE_PLAYBACK_INFO).apply {
            putExtra(EXTRA_SONG_TITLE, title)
            putExtra(EXTRA_SONG_ARTIST, artist)
        }
        sendBroadcast(intent)
        Log.i(TAG, "发送歌曲信息广播: $title - $artist")
    }

    override fun onDestroy() {
        instance = null
        isPlaying = false
        audioPlayer.release()
        vibrationController.stop()
        wakeLock?.release()
        super.onDestroy()
        Log.i(TAG, "onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}