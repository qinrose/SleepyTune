package com.example.sleepytune.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.sleepytune.R
import com.example.sleepytune.ui.screen.alarm.AlarmAlertActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class AlarmPlaybackService : android.app.Service() {

    companion object {
        private const val TAG = "AlarmPlaybackService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "alarm_playback_v2"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        private const val LOCAL_MUSIC_VOLUME_FACTOR = 0.8f

        const val ACTION_STOP = "com.example.sleepytune.ACTION_STOP"
        const val ACTION_SNOOZE = "com.example.sleepytune.ACTION_SNOOZE"
        const val ACTION_SNOOZE_ALARM = "com.example.sleepytune.SNOOZE"
        const val ACTION_STOP_VIBRATE = "com.example.sleepytune.ACTION_STOP_VIBRATE"
        const val ACTION_PAUSE_VIBRATE = "com.example.sleepytune.ACTION_PAUSE_VIBRATE"
        const val ACTION_STOP_ALARM = "com.example.sleepytune.ACTION_STOP_ALARM"
        const val ACTION_NEXT_SONG = "com.example.sleepytune.ACTION_NEXT_SONG"

        const val ACTION_UPDATE_PLAYBACK_INFO = "com.example.sleepytune.UPDATE_PLAYBACK_INFO"
        const val ACTION_SNOOZE_ENDED = "com.example.sleepytune.SNOOZE_ENDED"
        const val EXTRA_SONG_TITLE = "song_title"
        const val EXTRA_SONG_ARTIST = "song_artist"
        const val EXTRA_CURRENT_INDEX = "current_index"
        const val EXTRA_TOTAL_SONGS = "total_songs"

        @Volatile
        private var instance: AlarmPlaybackService? = null

        fun isPlaying(): Boolean = instance?.isPlaying ?: false

        fun stopAlarm() {
            instance?.stopAlarm()
        }

        fun stopVibrationOnly() {
            instance?.stopVibrationOnly()
        }
    }

    @Inject lateinit var playlistRepository: com.example.sleepytune.data.repository.PlaylistRepository
    @Inject lateinit var volumeManager: VolumeManager
    @Inject lateinit var alarmStateManager: AlarmStateManager

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isPlaying = false
    private var snoozeJob: Job? = null
    private var vibrateDelayJob: Job? = null
    private var snoozeVibrateJob: Job? = null
    private var currentMusicIndex = 0
    private var musicList: List<com.example.sleepytune.data.local.entity.Music> = emptyList()

    private var alarmId: Long = -1L
    private var playlistId: Long = 0L
    private var ringtonePath: String? = null
    private var shouldVibrate: Boolean = true
    private var vibrateDelayMinutes: Int = 0
    private var alarmVolume: Float = 1.0f
    private var followSystemVolume: Boolean = false
    private var alarmLabel: String = "闹钟"
    private var shuffleMode: Boolean = false
    private var fadeInEnabled: Boolean = true
    private var fadeInDuration: Int = 30
    private var snoozeMinutes: Int = 5
    private var snoozeEnabled: Boolean = true
    private var isVibrating: Boolean = false
    private var vibrationPaused: Boolean = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "AlarmPlaybackService onCreate")
        createNotificationChannel()
        acquireWakeLock()
        initVibrator()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "SleepyTune:AlarmWakeLock"
            )
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
            Log.i(TAG, "WakeLock已获取")
        } catch (e: Exception) {
            Log.e(TAG, "获取WakeLock失败: ${e.message}", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "WakeLock已释放")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "释放WakeLock失败: ${e.message}")
        }
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // 获取实际音量，支持跟随系统闹钟音量
    private fun getActualVolume(): Float {
        return if (followSystemVolume) {
            val maxVolume = volumeManager.getSystemMaxAlarmVolume()
            val currentVolume = volumeManager.getSystemAlarmVolume()
            if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 1.0f
        } else {
            alarmVolume.coerceIn(0f, 1f)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")

        intent?.action?.let { action ->
            when (action) {
                ACTION_SNOOZE_ALARM -> {
                    snooze()
                    return START_NOT_STICKY
                }
                ACTION_STOP_VIBRATE -> {
                    stopVibrationOnly()
                    return START_NOT_STICKY
                }
                ACTION_PAUSE_VIBRATE -> {
                    pauseVibrationUntilSnooze()
                    return START_NOT_STICKY
                }
                ACTION_STOP_ALARM -> {
                    stopAlarm()
                    return START_NOT_STICKY
                }
                ACTION_NEXT_SONG -> {
                    playNextSong()
                    return START_NOT_STICKY
                }
            }
        }

        intent?.let { data ->
            alarmId = data.getLongExtra("alarm_id", -1L)
            playlistId = data.getLongExtra("playlist_id", 0L)
            ringtonePath = data.getStringExtra("ringtone_path")
            shouldVibrate = data.getBooleanExtra("vibrate", true)
            vibrateDelayMinutes = data.getIntExtra("vibrate_delay_minutes", 0)
            alarmVolume = data.getFloatExtra("volume", 1.0f)
            followSystemVolume = alarmVolume < 0
            alarmLabel = data.getStringExtra("label") ?: "闹钟"
            shuffleMode = data.getBooleanExtra("shuffle_mode", false)
            fadeInEnabled = data.getBooleanExtra("fade_in_enabled", true)
            fadeInDuration = data.getIntExtra("fade_in_duration", 30)
            snoozeMinutes = data.getIntExtra("snooze_minutes", 5)
            snoozeEnabled = data.getBooleanExtra("snooze_enabled", true)

            Log.i(TAG, "闹钟参数: id=$alarmId, playlist=$playlistId, vibrate=$shouldVibrate, delay=$vibrateDelayMinutes, vol=$alarmVolume, shuffle=$shuffleMode, fadeIn=$fadeInEnabled/${fadeInDuration}s, snooze=$snoozeMinutes, snoozeEnabled=$snoozeEnabled")
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startAlarm()

        return START_NOT_STICKY
    }

    private fun startAlarm() {
        isPlaying = true
        alarmStateManager.persistAlarmPlaying(applicationContext, true, alarmId, alarmLabel)

        // 持久化震动状态
        val isCurrentlyVibrating = shouldVibrate && vibrateDelayMinutes == 0
        alarmStateManager.persistVibrateState(applicationContext, shouldVibrate, isCurrentlyVibrating)

        // 持久化贪睡启用状态
        alarmStateManager.persistSnoozeEnabled(applicationContext, snoozeEnabled)

        if (vibrationPaused) {
            Log.i(TAG, "震动处于暂停状态，本次不启动震动")
        }
        // 延迟震动逻辑
        else if (shouldVibrate && vibrateDelayMinutes > 0) {
            Log.i(TAG, "震动将在 ${vibrateDelayMinutes} 分钟后开始")
            vibrateDelayJob = CoroutineScope(Dispatchers.Main).launch {
                delay(vibrateDelayMinutes * 60 * 1000L)
                if (isPlaying && !vibrationPaused) {
                    startVibration()
                    alarmStateManager.persistVibrateState(applicationContext, true, true)
                }
            }
        } else if (shouldVibrate) {
            startVibration()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                loadAndPlayMusic()
            } catch (e: Exception) {
                Log.e(TAG, "启动闹钟失败: ${e.message}", e)
                playDefaultSound()
            }
        }
    }

    private suspend fun loadAndPlayMusic() {
        when {
            ringtonePath != null -> {
                // 单个铃声文件，提取文件名作为歌名
                val fileName = ringtonePath!!.substringAfterLast("/").substringBeforeLast(".")
                broadcastPlaybackInfo(fileName, "闹钟铃声", 0, 1)
                playAudioFile(ringtonePath!!, isSingleFile = true, isFirstSong = true)
            }
            playlistId > 0 -> {
                val playlistSongs = playlistRepository.getSongsInPlaylist(playlistId)
                if (playlistSongs.isNotEmpty()) {
                    musicList = playlistSongs.mapNotNull { songId ->
                        playlistRepository.getMusicById(songId)
                    }

                    if (musicList.isNotEmpty()) {
                        if (shuffleMode) {
                            musicList = musicList.shuffled()
                            Log.i(TAG, "随机播放模式：已打乱歌单顺序")
                        }
                        currentMusicIndex = 0
                        playCurrentSong()
                        return
                    }
                }
                Log.w(TAG, "歌单为空或无效，使用默认铃声: playlistId=$playlistId")
                playDefaultSound()
            }
            else -> {
                playDefaultSound()
            }
        }
    }

    private fun playCurrentSong() {
        if (musicList.isEmpty()) {
            playDefaultSound()
            return
        }

        if (currentMusicIndex >= musicList.size) {
            currentMusicIndex = 0
        }

        val music = musicList[currentMusicIndex]
        val isFirstSong = (currentMusicIndex == 0)
        Log.i(TAG, "播放歌曲 (${currentMusicIndex + 1}/${musicList.size}): ${music.title} by ${music.artist}")

        broadcastPlaybackInfo(music.title, music.artist, currentMusicIndex, musicList.size)

        playAudioFile(music.path, isSingleFile = false, isFirstSong = isFirstSong)
    }

    private fun broadcastPlaybackInfo(title: String, artist: String, index: Int, total: Int) {
        alarmStateManager.setSongInfo(title, artist)
        alarmStateManager.persistSongInfo(applicationContext, title, artist)
        
        val intent = Intent(ACTION_UPDATE_PLAYBACK_INFO).apply {
            putExtra(EXTRA_SONG_TITLE, title)
            putExtra(EXTRA_SONG_ARTIST, artist)
            putExtra(EXTRA_CURRENT_INDEX, index)
            putExtra(EXTRA_TOTAL_SONGS, total)
        }
        sendBroadcast(intent)
    }

    private fun playAudioFile(path: String, isSingleFile: Boolean = false, isFirstSong: Boolean = false) {
        try {
            val oldPlayer = mediaPlayer
            mediaPlayer = null
            oldPlayer?.release()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(path)
                prepareAsync()

                setOnPreparedListener { mp ->
                    val targetVolume = getActualVolume() * LOCAL_MUSIC_VOLUME_FACTOR

                    if (fadeInEnabled && isFirstSong) {
                        mp.setVolume(0.1f, 0.1f)
                        volumeManager.startFadeIn(mp, targetVolume, fadeInDuration * 1000L)
                        Log.i(TAG, "开始播放(渐强): $path, 目标音量=$targetVolume")
                    } else {
                        mp.setVolume(targetVolume, targetVolume)
                        Log.i(TAG, "开始播放: $path, 音量=$targetVolume")
                    }

                    mp.start()
                    updateNotification()
                }

                setOnCompletionListener {
                    volumeManager.stopFadeIn()

                    if (isSingleFile) {
                        mediaPlayer?.let { player ->
                            player.seekTo(0)
                            player.start()
                        }
                    } else {
                        playNextSong()
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer错误: what=$what, extra=$extra")
                    if (!isSingleFile && musicList.isNotEmpty()) {
                        playNextSong()
                    } else {
                        playDefaultSound()
                    }
                    true
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "无法播放文件: $path, ${e.message}")
            if (!isSingleFile && musicList.isNotEmpty()) {
                playNextSong()
            } else {
                playDefaultSound()
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放异常: ${e.message}", e)
            playDefaultSound()
        }
    }

    private fun playNextSong() {
        currentMusicIndex++
        if (currentMusicIndex >= musicList.size) {
            currentMusicIndex = 0
        }
        playCurrentSong()
    }

    private fun playDefaultSound() {
        try {
            releaseMediaPlayer()

            // 发送默认铃声信息
            broadcastPlaybackInfo("默认铃声", "系统", 0, 1)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                val uri = android.net.Uri.parse(
                    "${android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI}"
                )
                setDataSource(applicationContext, uri)
                prepare()

                val targetVolume = getActualVolume()

                if (volumeManager.isFadeInEnabled()) {
                    setVolume(0.1f, 0.1f)
                    volumeManager.startFadeIn(this, targetVolume)
                } else {
                    setVolume(targetVolume, targetVolume)
                }

                isLooping = true
                start()

                Log.i(TAG, "播放默认闹钟铃声, 音量=$targetVolume")
            }
        } catch (e: Exception) {
            Log.e(TAG, "无法播放默认铃声: ${e.message}", e)
        }
    }

    private fun startVibration() {
        try {
            isVibrating = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(500, 500), 0)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(500, 500), 0)
            }
            Log.i(TAG, "震动已启动")
        } catch (e: SecurityException) {
            Log.w(TAG, "无振动权限: ${e.message}")
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
            isVibrating = false
            vibrateDelayJob?.cancel()
            Log.i(TAG, "震动已停止")
        } catch (e: Exception) {
            Log.w(TAG, "停止振动失败: ${e.message}")
        }
    }

    fun stopVibrationOnly() {
        Log.i(TAG, "仅停止震动，继续播放音乐")
        stopVibration()
        snoozeVibrateJob?.cancel()
        snoozeVibrateJob = null
        shouldVibrate = false
        vibrationPaused = false
        updateNotification()
    }

    fun pauseVibrationUntilSnooze() {
        val snoozeDelayMs = snoozeMinutes * 60 * 1000L
        Log.i(TAG, "暂停震动，${snoozeMinutes}分钟后恢复震动")
        stopVibration()
        vibrationPaused = true
        snoozeVibrateJob?.cancel()
        snoozeVibrateJob = CoroutineScope(Dispatchers.Main).launch {
            delay(snoozeDelayMs)
            if (isPlaying && vibrationPaused) {
                vibrationPaused = false
                startVibration()
                updateNotification()
                Log.i(TAG, "稍后再震时间到，震动已恢复")
            }
        }
        updateNotification()
    }

    private fun snooze() {
        val snoozeDelayMs = snoozeMinutes * 60 * 1000L
        Log.i(TAG, "贪睡模式，${snoozeMinutes}分钟后重新响铃")

        snoozeVibrateJob?.cancel()
        stopAlarmSound()
        stopVibration()
        isPlaying = false
        alarmStateManager.persistAlarmPlaying(applicationContext, false)
        updateNotification()

        snoozeJob = CoroutineScope(Dispatchers.Main).launch {
            delay(snoozeDelayMs)
            if (isPlaying.not()) {
                // 贪睡后重置震动暂停标志
                vibrationPaused = false
                
                // 发送贪睡结束广播，通知Activity重新显示
                val snoozeEndIntent = Intent(ACTION_SNOOZE_ENDED).apply {
                    putExtra("alarm_id", alarmId)
                    putExtra("label", alarmLabel)
                }
                sendBroadcast(snoozeEndIntent)
                
                startAlarm()
            }
        }
    }

    private fun stopAlarm() {
        Log.i(TAG, "停止闹钟")

        snoozeJob?.cancel()
        vibrateDelayJob?.cancel()
        snoozeVibrateJob?.cancel()
        volumeManager.stopFadeIn()
        stopAlarmSound()
        stopVibration()
        isPlaying = false
        shouldVibrate = true
        vibrationPaused = false
        alarmStateManager.clearPersistedState(applicationContext)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopAlarmSound() {
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "停止播放异常: ${e.message}")
        }
        releaseMediaPlayer()
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "闹钟播放",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟正在播放中"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, AlarmPlaybackReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, AlarmPlaybackReceiver::class.java).apply {
            action = ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this, 2, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopVibrateIntent = Intent(this, AlarmPlaybackReceiver::class.java).apply {
            action = ACTION_STOP_VIBRATE
        }
        val stopVibratePendingIntent = PendingIntent.getBroadcast(
            this, 3, stopVibrateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseVibrateIntent = Intent(this, AlarmPlaybackReceiver::class.java).apply {
            action = ACTION_PAUSE_VIBRATE
        }
        val pauseVibratePendingIntent = PendingIntent.getBroadcast(
            this, 4, pauseVibrateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (musicList.isNotEmpty() && currentMusicIndex < musicList.size) {
            "${musicList[currentMusicIndex].title} (${currentMusicIndex + 1}/${musicList.size})"
        } else {
            if (isPlaying) "正在播放..." else "贪睡中..."
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(alarmLabel)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setDefaults(0)
            .setSound(null)
            .addAction(android.R.drawable.ic_media_pause, "停止", stopPendingIntent)
            .addAction(android.R.drawable.ic_media_next, "贪睡", snoozePendingIntent)

        // 震动相关按钮
        if (isVibrating) {
            builder.addAction(android.R.drawable.ic_lock_silent_mode, "关闭震动", stopVibratePendingIntent)
            builder.addAction(android.R.drawable.ic_media_pause, "稍后再震", pauseVibratePendingIntent)
        }

        return builder.build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "AlarmPlaybackService onDestroy")
        stopAlarmSound()
        stopVibration()
        releaseWakeLock()
        snoozeJob?.cancel()
        vibrateDelayJob?.cancel()
        snoozeVibrateJob?.cancel()
        volumeManager.stopFadeIn()
        alarmStateManager.clearPersistedState(applicationContext)
    }
}
