package com.example.sleepytune.service

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
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
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        private const val LOCAL_MUSIC_VOLUME_FACTOR = 0.8f
        private const val PREPARE_TIMEOUT_MS = 10_000L
        private const val MAX_CONSECUTIVE_FAILURES = 3

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

        fun getInstance(): AlarmPlaybackService? = instance

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
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var alarmSnoozeManager: AlarmSnoozeManager
    @Inject lateinit var vibrationController: VibrationController

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isPlaying = false
    private var currentMusicIndex = 0
    private var musicList: List<com.example.sleepytune.data.local.entity.Music> = emptyList()
    private var isMusicLoading = false
    private var prepareTimeoutJob: Job? = null
    private var consecutiveFailures = 0

    private var alarmId: Long = -1L
    private var playlistId: Long = 0L
    private var ringtonePath: String? = null
    private var alarmVolume: Float = 1.0f
    private var followSystemVolume: Boolean = false
    private var alarmLabel: String = "闹钟"
    private var shuffleMode: Boolean = false
    private var fadeInEnabled: Boolean = true
    private var fadeInDuration: Int = 30
    private var snoozeMinutes: Int = 5
    private var snoozeEnabled: Boolean = true
    private var shouldVibrate: Boolean = true
    private var vibrateDelayMinutes: Int = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "AlarmPlaybackService onCreate")
        acquireWakeLock()
        vibrationController.onStateChanged = { isVibrating, isPaused ->
            alarmStateManager.persistVibrateState(applicationContext, true, isVibrating)
            updateNotification()
        }
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
                    handleNextSong()
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

        startForeground(
            NotificationHelper.NOTIFICATION_ID_ALARM,
            notificationHelper.buildAlarmNotification(
                alarmLabel = alarmLabel,
                isPlaying = true
            )
        )
        startAlarm()

        return START_NOT_STICKY
    }

    private fun startAlarm() {
        isPlaying = true
        isMusicLoading = true
        consecutiveFailures = 0
        alarmStateManager.persistAlarmPlaying(applicationContext, true, alarmId, alarmLabel)
        alarmStateManager.persistRingtonePath(applicationContext, ringtonePath)
        alarmStateManager.persistPlaylistId(applicationContext, playlistId)
        alarmStateManager.persistSnoozeEnabled(applicationContext, snoozeEnabled)

        // 使用 VibrationController 管理震动
        vibrationController.configure(shouldVibrate, vibrateDelayMinutes, snoozeMinutes)
        if (shouldVibrate && vibrateDelayMinutes == 0) {
            vibrationController.start()
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
        try {
            when {
                ringtonePath != null -> {
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
                            if (shuffleMode) musicList = musicList.shuffled()
                            currentMusicIndex = 0
                            persistPlaylistState()
                            playCurrentSong()
                            return
                        }
                    }
                    Log.w(TAG, "歌单为空或无效，使用默认铃声: playlistId=$playlistId")
                    playDefaultSound()
                }
                else -> playDefaultSound()
            }
        } finally {
            isMusicLoading = false
        }
    }

    // 持久化当前歌单状态
    private fun persistPlaylistState() {
        alarmStateManager.persistPlaylist(applicationContext, musicList, currentMusicIndex)
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
        Log.i(TAG, "播放歌曲 (${currentMusicIndex + 1}/${musicList.size}): ${music.title}")
        broadcastPlaybackInfo(music.title, music.artist, currentMusicIndex, musicList.size)
        persistPlaylistState()
        playAudioFile(music.path, isSingleFile = false, isFirstSong = false)
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
            cancelPrepareTimeout()
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
                    cancelPrepareTimeout()
                    consecutiveFailures = 0
                    val targetVolume = getActualVolume() * LOCAL_MUSIC_VOLUME_FACTOR

                    if (fadeInEnabled && isFirstSong) {
                        mp.setVolume(0.1f, 0.1f)
                        volumeManager.startFadeIn(mp, targetVolume, fadeInDuration * 1000L)
                    } else {
                        mp.setVolume(targetVolume, targetVolume)
                    }
                    mp.start()
                    updateNotification()
                    Log.i(TAG, "开始播放: $path")
                }

                setOnCompletionListener {
                    cancelPrepareTimeout()
                    volumeManager.stopFadeIn()
                    if (isSingleFile) {
                        mediaPlayer?.let { it.seekTo(0); it.start() }
                    } else {
                        playNextSong()
                    }
                }

                setOnErrorListener { _, what, extra ->
                    cancelPrepareTimeout()
                    Log.e(TAG, "MediaPlayer错误: what=$what, extra=$extra, path=$path")
                    consecutiveFailures++
                    handlePlaybackFailure(isSingleFile)
                    true
                }
            }

            startPrepareTimeout(path, isSingleFile)
        } catch (e: IOException) {
            Log.e(TAG, "无法播放文件: $path, ${e.message}")
            consecutiveFailures++
            handlePlaybackFailure(isSingleFile)
        } catch (e: Exception) {
            Log.e(TAG, "播放异常: ${e.message}", e)
            consecutiveFailures++
            handlePlaybackFailure(isSingleFile)
        }
    }

    // 播放失败处理：切歌或播放默认铃声
    private fun handlePlaybackFailure(isSingleFile: Boolean) {
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            Log.w(TAG, "连续失败${consecutiveFailures}次，播放默认铃声")
            playDefaultSound()
            return
        }
        if (!isSingleFile && musicList.isNotEmpty()) {
            playNextSong()
        } else {
            playDefaultSound()
        }
    }

    // prepareAsync 超时保护
    private fun startPrepareTimeout(path: String, isSingleFile: Boolean) {
        prepareTimeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(PREPARE_TIMEOUT_MS)
            Log.w(TAG, "播放准备超时: $path")
            consecutiveFailures++
            try { mediaPlayer?.stop() } catch (_: Exception) {}
            handlePlaybackFailure(isSingleFile)
        }
    }

    private fun cancelPrepareTimeout() {
        prepareTimeoutJob?.cancel()
        prepareTimeoutJob = null
    }

    // 处理"下一首"操作：加载中忽略，空列表尝试恢复
    private fun handleNextSong() {
        if (isMusicLoading) {
            Log.i(TAG, "歌单加载中，忽略下一首请求")
            return
        }
        if (musicList.isEmpty()) {
            tryRestorePlaylist()
            if (musicList.isEmpty()) {
                val savedPath = alarmStateManager.loadRingtonePath(applicationContext)
                if (!savedPath.isNullOrEmpty()) {
                    ringtonePath = savedPath
                    playAudioFile(savedPath, isSingleFile = true, isFirstSong = false)
                    return
                }
                playDefaultSound()
                return
            }
        }
        playNextSong()
    }

    // 尝试从持久化状态恢复歌单
    private fun tryRestorePlaylist() {
        val (savedList, savedIndex) = alarmStateManager.loadPlaylist(applicationContext)
        if (savedList.isNotEmpty()) {
            musicList = savedList
            currentMusicIndex = savedIndex
            playlistId = alarmStateManager.loadPlaylistId(applicationContext)
            ringtonePath = alarmStateManager.loadRingtonePath(applicationContext)
            Log.i(TAG, "从持久化恢复歌单: ${savedList.size}首, index=$savedIndex")
            return
        }
        if (playlistId > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                val songs = playlistRepository.getSongsInPlaylist(playlistId)
                musicList = songs.mapNotNull { playlistRepository.getMusicById(it) }
                if (musicList.isNotEmpty()) {
                    currentMusicIndex = 0
                    persistPlaylistState()
                    Log.i(TAG, "从数据库恢复歌单: ${musicList.size}首")
                }
            }
        }
    }

    private fun playNextSong() {
        if (musicList.isEmpty()) {
            playDefaultSound()
            return
        }
        currentMusicIndex = (currentMusicIndex + 1) % musicList.size
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

    fun stopVibrationOnly() {
        Log.i(TAG, "仅停止震动，继续播放音乐")
        vibrationController.stop()
    }

    fun pauseVibrationUntilSnooze() {
        Log.i(TAG, "暂停震动，稍后恢复")
        vibrationController.pause()
    }

    private fun snooze() {
        Log.i(TAG, "贪睡模式，${snoozeMinutes}分钟后重新响铃")

        vibrationController.release()
        stopAlarmSound()
        isPlaying = false
        alarmStateManager.persistAlarmPlaying(applicationContext, false)

        // 使用 AlarmSnoozeManager 调度贪睡（进程被杀后不丢失）
        alarmSnoozeManager.scheduleSnoozeAlarm(
            alarmId = alarmId,
            playlistId = playlistId,
            ringtone = ringtonePath,
            vibrate = shouldVibrate,
            volume = alarmVolume,
            label = alarmLabel,
            shuffleMode = shuffleMode,
            fadeInEnabled = fadeInEnabled,
            fadeInDuration = fadeInDuration,
            snoozeMinutes = snoozeMinutes
        )

        // 显示贪睡通知
        notificationHelper.showSnoozeNotification(alarmLabel, snoozeMinutes)

        // 停止前台服务，但保留贪睡状态
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopAlarm() {
        Log.i(TAG, "停止闹钟")
        alarmSnoozeManager.cancelSnooze()
        vibrationController.release()
        cancelPrepareTimeout()
        volumeManager.stopFadeIn()
        stopAlarmSound()
        isPlaying = false
        isMusicLoading = false
        consecutiveFailures = 0
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

    private fun updateNotification() {
        val songInfo = if (musicList.isNotEmpty() && currentMusicIndex < musicList.size) {
            musicList[currentMusicIndex]
        } else null
        notificationHelper.updateAlarmNotification(
            alarmLabel = alarmLabel,
            isPlaying = isPlaying,
            songTitle = songInfo?.title,
            songIndex = if (musicList.isNotEmpty()) currentMusicIndex + 1 else 0,
            songTotal = musicList.size,
            isVibrating = vibrationController.isVibrating()
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "AlarmPlaybackService onDestroy")
        cancelPrepareTimeout()
        stopAlarmSound()
        vibrationController.release()
        releaseWakeLock()
        volumeManager.stopFadeIn()
    }
}
