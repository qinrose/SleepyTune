package com.example.sleepytune.ui.screen.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sleepytune.R
import com.example.sleepytune.service.AlarmPlaybackReceiver
import com.example.sleepytune.service.AlarmPlaybackService
import com.example.sleepytune.service.AlarmReceiver
import com.example.sleepytune.ui.theme.AlarmColorConfig
import com.example.sleepytune.ui.theme.SleepyTuneTheme
import com.example.sleepytune.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.*

class AlarmAlertActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AlarmAlertActivity"
        @Volatile private var isActivityRunning = false
        @Volatile private var shouldFinish = false

        fun setShouldFinish(value: Boolean) {
            shouldFinish = value
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查是否应该关闭
        if (shouldFinish) {
            Log.i(TAG, "收到关闭标志，直接finish")
            finish()
            return
        }

        // 防止重复启动
        if (isActivityRunning) {
            Log.i(TAG, "Activity已在运行，检查闹钟状态")
            val prefs = getSharedPreferences("alarm_state", MODE_PRIVATE)
            val isPlaying = prefs.getBoolean("is_alarm_playing", false)
            if (!isPlaying) {
                Log.i(TAG, "闹钟已停止，关闭Activity")
                finish()
                return
            }
            return
        }
        isActivityRunning = true

        // 阻止系统返回键关闭闹钟提醒界面（替代已废弃的 onBackPressed()）
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 空实现：拦截返回键，防止误触关闭闹钟
            }
        })

        val lockScreenPopupEnabled = SettingsViewModel.isLockScreenPopupEnabled(this)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (lockScreenPopupEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
                setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
            }
        }

        // edge-to-edge（enableEdgeToEdge）已使状态栏/导航栏透明，无需再设置已废弃的 statusBarColor/navigationBarColor

        Log.i(TAG, "闹钟提醒界面启动")

        handleIntent(intent)

        if (AlarmPlaybackService.getInstance() == null) {
            Log.i(TAG, "Service 未运行，从持久化状态恢复")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                restoreAlarmPlayback()
            }
        }

        setContent {
            SleepyTuneTheme(edgeToEdge = true) {
                AlarmAlertScreen(alarmId = alarmId, alarmLabel = alarmLabel, snoozeEnabled = snoozeEnabled)
            }
        }
    }

    private fun restoreAlarmPlayback() {
        val prefs = getSharedPreferences("alarm_state", MODE_PRIVATE)

        val isAlarmPlaying = prefs.getBoolean("is_alarm_playing", false)
        if (!isAlarmPlaying) {
            Log.i(TAG, "闹钟未在播放状态，不恢复")
            return
        }

        val alarmId = prefs.getLong("alarm_id", -1L)
        val label = prefs.getString("alarm_label", "闹钟") ?: "闹钟"
        val snoozeEnabled = prefs.getBoolean("snooze_enabled", true)

        val playlistPrefs = getSharedPreferences("alarm_playlist", MODE_PRIVATE)
        val playlistId = playlistPrefs.getLong("playlist_id", 0L)
        val ringtonePath = playlistPrefs.getString("ringtone_path", null)

        val paramsPrefs = getSharedPreferences("alarm_params", MODE_PRIVATE)
        val volume = paramsPrefs.getFloat("volume", 1.0f)
        val vibrate = paramsPrefs.getBoolean("vibrate", true)
        val vibrateDelayMinutes = paramsPrefs.getInt("vibrate_delay_minutes", 0)
        val shuffleMode = paramsPrefs.getBoolean("shuffle_mode", false)
        val fadeInEnabled = paramsPrefs.getBoolean("fade_in_enabled", true)
        val fadeInDuration = paramsPrefs.getInt("fade_in_duration", 30)
        val snoozeMinutes = paramsPrefs.getInt("snooze_minutes", 5)

        if (alarmId >= 0) {
            Log.i(TAG, "恢复闹钟播放: alarmId=$alarmId, playlistId=$playlistId, volume=$volume, vibrate=$vibrate")
            val serviceIntent = Intent(this, AlarmPlaybackService::class.java).apply {
                putExtra("alarm_id", alarmId)
                putExtra("playlist_id", playlistId)
                putExtra("ringtone_path", ringtonePath)
                putExtra("label", label)
                putExtra("snooze_enabled", snoozeEnabled)
                putExtra("vibrate", vibrate)
                putExtra("vibrate_delay_minutes", vibrateDelayMinutes)
                putExtra("volume", volume)
                putExtra("shuffle_mode", shuffleMode)
                putExtra("fade_in_enabled", fadeInEnabled)
                putExtra("fade_in_duration", fadeInDuration)
                putExtra("snooze_minutes", snoozeMinutes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(TAG, "onNewIntent收到新Intent")

        if (shouldFinish) {
            Log.i(TAG, "收到关闭标志，finish")
            finish()
            return
        }

        val prefs = getSharedPreferences("alarm_state", MODE_PRIVATE)
        val isPlaying = prefs.getBoolean("is_alarm_playing", false)
        if (!isPlaying) {
            Log.i(TAG, "闹钟已停止，关闭Activity")
            finish()
            return
        }

        handleIntent(intent)
    }

    private var alarmId by mutableStateOf(-1L)
    private var alarmLabel by mutableStateOf("")
    private var snoozeEnabled by mutableStateOf(true)

    private fun handleIntent(intent: Intent) {
        alarmId = intent.getLongExtra("alarm_id", -1L)
        alarmLabel = intent.getStringExtra("label") ?: ""
        snoozeEnabled = intent.getBooleanExtra("snooze_enabled", true)
    }

    override fun onDestroy() {
        super.onDestroy()
        isActivityRunning = false
        shouldFinish = false
        Log.i(TAG, "闹钟提醒界面销毁")
    }
}

@Composable
fun AlarmAlertScreen(alarmId: Long, alarmLabel: String, snoozeEnabled: Boolean) {
    val context = LocalContext.current

    var songTitle by remember { mutableStateOf("") }
    var songArtist by remember { mutableStateOf("") }
    var playbackWarning by remember { mutableStateOf<String?>(null) }
    var isVibrating by remember { mutableStateOf(false) }
    var shouldVibrate by remember { mutableStateOf(true) }
    var showVibrationControls by remember { mutableStateOf(false) }

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var currentWeekday by remember { mutableStateOf("") }

    val activity = context as? AlarmAlertActivity
    val view = LocalView.current

    // 确保主题没有把状态栏/导航栏重新涂成不透明色，保持真正 edge-to-edge
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
    }

    LaunchedEffect(Unit) {
        val intent = activity?.intent
        val intentTitle = intent?.getStringExtra(AlarmReceiver.EXTRA_SONG_TITLE) ?: ""
        val intentArtist = intent?.getStringExtra(AlarmReceiver.EXTRA_SONG_ARTIST) ?: ""

        if (intentTitle.isNotEmpty()) {
            songTitle = intentTitle
            songArtist = intentArtist
        } else {
            val prefs = context.getSharedPreferences("alarm_state", android.content.Context.MODE_PRIVATE)
            songTitle = prefs.getString("song_title", "") ?: ""
            songArtist = prefs.getString("song_artist", "") ?: ""
        }
        // 界面可能晚于回退广播注册，从持久化状态补读
        playbackWarning = context.getSharedPreferences("alarm_state", android.content.Context.MODE_PRIVATE)
            .getString("fallback_reason", null)
        shouldVibrate = context.getSharedPreferences("alarm_state", android.content.Context.MODE_PRIVATE)
            .getBoolean("should_vibrate", true)
        isVibrating = context.getSharedPreferences("alarm_state", android.content.Context.MODE_PRIVATE)
            .getBoolean("is_vibrating", false)
    }

    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                intent?.let {
                    when (it.action) {
                        AlarmPlaybackService.ACTION_UPDATE_PLAYBACK_INFO -> {
                            songTitle = it.getStringExtra(AlarmPlaybackService.EXTRA_SONG_TITLE) ?: ""
                            songArtist = it.getStringExtra(AlarmPlaybackService.EXTRA_SONG_ARTIST) ?: ""
                        }
                        AlarmPlaybackService.ACTION_PLAYBACK_FALLBACK -> {
                            // 音频已降级为系统默认铃声：显示警告，不再"歌名对但声音是默认音"的错觉
                            val reason = it.getStringExtra(AlarmPlaybackService.EXTRA_FALLBACK_REASON) ?: "音乐无法播放"
                            val failedTitle = it.getStringExtra(AlarmPlaybackService.EXTRA_FALLBACK_TITLE)
                            if (!failedTitle.isNullOrEmpty()) songTitle = failedTitle
                            playbackWarning = reason
                        }
                        AlarmPlaybackService.ACTION_SNOOZE_ENDED -> {
                            val prefs = context.getSharedPreferences("alarm_state", android.content.Context.MODE_PRIVATE)
                            songTitle = prefs.getString("song_title", "") ?: ""
                            songArtist = prefs.getString("song_artist", "") ?: ""
                            shouldVibrate = prefs.getBoolean("should_vibrate", true)
                            isVibrating = prefs.getBoolean("is_vibrating", false)
                        }
                        AlarmPlaybackReceiver.ACTION_FINISH_ACTIVITY -> {
                            activity?.finish()
                        }
                    }
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(AlarmPlaybackService.ACTION_UPDATE_PLAYBACK_INFO)
            addAction(AlarmPlaybackService.ACTION_PLAYBACK_FALLBACK)
            addAction(AlarmPlaybackService.ACTION_SNOOZE_ENDED)
            addAction(AlarmPlaybackReceiver.ACTION_FINISH_ACTIVITY)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
            currentDate = SimpleDateFormat("MM月dd日", Locale.getDefault()).format(now.time)
            val weekdays = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
            currentWeekday = weekdays[now.get(Calendar.DAY_OF_WEEK) - 1]
            kotlinx.coroutines.delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AlarmColorConfig.BackgroundTop,
                        AlarmColorConfig.BackgroundBottom
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // 杂志刊头
            Text(
                text = "SLEEPY TUNE",
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                color = AlarmColorConfig.TextAccent
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = alarmLabel.ifEmpty { "MORNING EDITION" },
                fontFamily = FontFamily.Serif,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = AlarmColorConfig.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp)
                    .height(1.dp)
                    .background(AlarmColorConfig.CardBorder)
            )

            Spacer(modifier = Modifier.weight(1f))

            // 杂志封面式大时间
            Text(
                text = currentTime,
                fontFamily = FontFamily.Serif,
                fontSize = 96.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
                color = AlarmColorConfig.TextPrimary
            )
            Text(
                text = "$currentDate  ·  $currentWeekday",
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                color = AlarmColorConfig.TextSecondary
            )

            Spacer(modifier = Modifier.weight(1f))

            // 歌曲信息卡片：克制、杂志专题式
            // 音频已回退默认铃声时的警告条（界面与声音同步的关键）
            val warning = playbackWarning
            if (!warning.isNullOrEmpty()) {
                PlaybackWarningBanner(text = warning)
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (songTitle.isNotEmpty()) {
                MagazineSongCard(
                    title = songTitle,
                    artist = songArtist,
                    onNextSong = {
                        context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                            action = AlarmPlaybackService.ACTION_NEXT_SONG
                        })
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // 主操作区
            if (snoozeEnabled) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                                action = AlarmPlaybackService.ACTION_SNOOZE_ALARM
                            })
                            activity?.finish()
                        },
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AlarmColorConfig.CardBorder),
                        modifier = Modifier.weight(1f).height(54.dp)
                    ) {
                        Icon(Icons.Default.Snooze, null, modifier = Modifier.size(20.dp), tint = AlarmColorConfig.TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.alarm_snooze), color = AlarmColorConfig.TextPrimary)
                    }

                    Button(
                        onClick = {
                            AlarmAlertActivity.setShouldFinish(true)
                            context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                                action = AlarmPlaybackService.ACTION_STOP_ALARM
                            })
                            activity?.finish()
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AlarmColorConfig.ButtonDanger),
                        modifier = Modifier.weight(1.4f).height(54.dp)
                    ) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(20.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.alarm_stop_button), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = {
                        AlarmAlertActivity.setShouldFinish(true)
                        context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                            action = AlarmPlaybackService.ACTION_STOP_ALARM
                        })
                        activity?.finish()
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AlarmColorConfig.ButtonDanger),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.alarm_stop_button), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // 震动控制收纳为“更多”，避免主界面复杂
            if (shouldVibrate && isVibrating) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showVibrationControls = !showVibrationControls },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        if (showVibrationControls) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = AlarmColorConfig.TextSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (showVibrationControls) stringResource(R.string.alarm_pause_vibrate) else "震动控制",
                        color = AlarmColorConfig.TextSecondary
                    )
                }
                AnimatedVisibility(
                    visible = showVibrationControls,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                                    action = AlarmPlaybackService.ACTION_PAUSE_VIBRATE
                                })
                                isVibrating = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AlarmColorConfig.CardBorder),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.Pause, null, modifier = Modifier.size(18.dp), tint = AlarmColorConfig.TextSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.alarm_pause_vibrate), color = AlarmColorConfig.TextSecondary, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                                    action = AlarmPlaybackService.ACTION_STOP_VIBRATE
                                })
                                isVibrating = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AlarmColorConfig.CardBorder),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Icon(Icons.Default.Vibration, null, modifier = Modifier.size(18.dp), tint = AlarmColorConfig.TextSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.alarm_stop_vibrate), color = AlarmColorConfig.TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackWarningBanner(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AlarmColorConfig.WarningBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, AlarmColorConfig.WarningBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚠️",
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "已改用系统默认铃声：$text",
                style = MaterialTheme.typography.bodyMedium,
                color = AlarmColorConfig.WarningText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MagazineSongCard(title: String, artist: String, onNextSong: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AlarmColorConfig.CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, AlarmColorConfig.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NOW PLAYING",
                    fontFamily = FontFamily.Serif,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = AlarmColorConfig.TextAccent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AlarmColorConfig.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (artist.isNotEmpty()) {
                    Text(
                        text = artist,
                        fontSize = 14.sp,
                        color = AlarmColorConfig.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            FloatingActionButton(
                onClick = onNextSong,
                shape = RoundedCornerShape(16.dp),
                containerColor = AlarmColorConfig.ButtonPrimary,
                contentColor = AlarmColorConfig.TextPrimary,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(22.dp))
            }
        }
    }
}
