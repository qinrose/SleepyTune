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

        // 震动状态变化同步到跨进程存储，供闹钟界面显示控制按钮
        vibrationController.onStateChanged = { vibrating, _ ->
            alarmStateManager.persistVibrateState(
                applicationContext,
                vibrationController.shouldVibrate(),
                vibrating
            )
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
                    if (config == null) stopSelf() else playNextSong()
                    return START_NOT_STICKY  // 切歌后直接返回
                }
                ACTION_SNOOZE_ALARM -> {
                    snooze()
                    return START_NOT_STICKY
                }
                ACTION_STOP_VIBRATE -> {
                    if (config == null) stopSelf() else vibrationController.stop()
                    return START_NOT_STICKY
                }
                ACTION_PAUSE_VIBRATE -> {
                    if (config == null) stopSelf() else vibrationController.pause()
                    return START_NOT_STICKY
                }
            }
        }
        
        // 防止重复启动
        if (isPlaying) {
            Log.i(TAG, "已在播放，忽略重复请求")
            return START_STICKY
        }

        // 系统因 START_STICKY 重启服务且没有新 Intent 时，从持久化状态恢复。
        // 注意：正常闹钟触发的 Intent 没有 action，但带有 alarm_id 等参数，不能走恢复逻辑。
        if (intent == null || (intent.action == null && !intent.hasExtra("alarm_id"))) {
            if (restoreFromPersistence()) {
                Log.i(TAG, "已从持久化状态恢复闹钟播放")
                return START_STICKY
            }
            Log.w(TAG, "没有可恢复的闹钟状态，停止服务")
            stopSelf()
            return START_NOT_STICKY
        }

        // 解析配置并启动播放
        startAlarm(AlarmConfig.fromIntent(intent))

        return START_STICKY
    }

    // 启动闹钟
    private fun startAlarm(config: AlarmConfig) {
        this.config = config
        isPlaying = true
        
        Log.i(TAG, "闹钟参数: ${config}")
        
        // 持久化状态
        persistState(config)
        
        // 启动震动（支持延迟震动）
        if (config.vibrate) {
            vibrationController.configure(true, config.vibrateDelayMinutes, config.snoozeMinutes)
            if (config.vibrateDelayMinutes <= 0) {
                vibrationController.start()
            }
        } else {
            vibrationController.stop()
        }
        
        // 加载并播放（audioPlayer 内部会切换到主线程）
        CoroutineScope(Dispatchers.IO).launch {
            val hasPlaylist = playlistManager.loadPlaylist(config)
            
            withContext(Dispatchers.Main) {
                // 加载期间用户可能已经停止/贪睡，必须取消本次播放，避免“关了还响”
                if (!isPlaying) {
                    Log.w(TAG, "闹钟已停止，取消延迟播放")
                    return@withContext
                }
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
        if (!isPlaying) return
        val c = config
        if (c == null) {
            Log.e(TAG, "playNextSong: config is null")
            return
        }

        // 没有歌单时（单铃声/默认铃声），“下一首”就重播当前声音，避免静默
        if (playlistManager.isEmpty()) {
            Log.w(TAG, "playNextSong: 歌单为空，重播当前闹钟音")
            if (c.ringtonePath != null) {
                audioPlayer.playLocalFile(applicationContext, c.ringtonePath, c, false, true)
            } else {
                audioPlayer.playDefaultRingtone(applicationContext, c, false)
            }
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
        if (!isPlaying) return
        val c = config
        if (c == null) {
            Log.e(TAG, "handlePlaybackFailure: config is null")
            return
        }

        // 单铃声模式下文件失效：直接切到系统默认闹钟音，避免无声
        if (playlistManager.isEmpty() && c.ringtonePath != null) {
            Log.w(TAG, "单铃声播放失败，切换默认闹钟音")
            audioPlayer.playDefaultRingtone(applicationContext, c, false)
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
        val c = config
        if (c == null) {
            Log.w(TAG, "snooze: config is null")
            stopSelf()
            return
        }
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
        // 贪睡期间不应继续占用“正在播放”前台服务，交给 AlarmManager 到点再唤醒
        isPlaying = false
        alarmStateManager.persistAlarmPlaying(applicationContext, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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

    // 从持久化状态恢复（进程被系统重建后使用）
    private fun restoreFromPersistence(): Boolean {
        if (!alarmStateManager.isAlarmPlayingCrossProcess(this)) return false
        val alarmId = alarmStateManager.getAlarmIdCrossProcess(this)
        if (alarmId < 0) return false

        val label = alarmStateManager.getAlarmLabelCrossProcess(this)
        val snoozeEnabled = alarmStateManager.getSnoozeEnabledCrossProcess(this)
        val playlistId = alarmStateManager.loadPlaylistId(this)
        val ringtonePath = alarmStateManager.loadRingtonePath(this)
        val params = alarmStateManager.loadAlarmParams(this)

        val restored = AlarmConfig(
            alarmId = alarmId,
            playlistId = playlistId,
            ringtonePath = ringtonePath,
            volume = params.volume,
            vibrate = params.vibrate,
            vibrateDelayMinutes = params.vibrateDelayMinutes,
            label = label,
            shuffleMode = params.shuffleMode,
            fadeInEnabled = params.fadeInEnabled,
            fadeInDuration = params.fadeInDuration,
            snoozeMinutes = params.snoozeMinutes,
            snoozeEnabled = snoozeEnabled
        )
        startAlarm(restored)
        return true
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
        vibrationController.onStateChanged = null
        wakeLock?.release()
        super.onDestroy()
        Log.i(TAG, "onDestroy")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}