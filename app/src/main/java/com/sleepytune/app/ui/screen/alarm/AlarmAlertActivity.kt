package com.sleepytune.app.ui.screen.alarm
import com.sleepytune.app.ui.theme.Radius

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.sleepytune.app.R
import com.sleepytune.app.service.AlarmPlaybackReceiver
import com.sleepytune.app.service.AlarmPlaybackService
import com.sleepytune.app.service.AlarmReceiver
import com.sleepytune.app.ui.theme.AlarmColorConfig
import com.sleepytune.app.ui.theme.SleepyTuneTheme
import com.sleepytune.app.utils.LunarDate
import com.sleepytune.app.ui.viewmodel.SettingsViewModel
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
        // Android 10+ 关闭系统对透明导航栏的对比度遮罩，避免手势条区域出现浅白色横条
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

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
            // 系统栏图标明暗跟随主题（深色=白色图标，浅色=深色图标）
            val darkTheme = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            window.insetsController?.apply {
                setSystemBarsAppearance(
                    if (darkTheme) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
                setSystemBarsAppearance(
                    if (darkTheme) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
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
                AlarmAlertScreen(
                    alarmId = alarmId,
                    alarmLabel = alarmLabel,
                    snoozeEnabled = snoozeEnabled,
                    alarmVibrate = alarmVibrate
                )
            }
        }
    }

    // 响铃期间的物理键交互（Google Clock 同款习惯）：
    // 音量键 / 耳机线控键 = 贪睡（未启用贪睡则停止）；电源键 = 贪睡（设备相关，部分机型不派发）
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (AlarmPlaybackService.isPlaying()) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP,
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN,
                android.view.KeyEvent.KEYCODE_HEADSETHOOK -> {
                    handleHardwareKeyAction()
                    return true
                }
                android.view.KeyEvent.KEYCODE_POWER -> {
                    // 屏幕已亮且本 Activity 为前台时，部分设备会派发电源键事件
                    handleHardwareKeyAction()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // 物理键动作：贪睡或停止，并关闭响铃界面
    private fun handleHardwareKeyAction() {
        Log.i(TAG, "物理键操作: ${if (snoozeEnabled) "贪睡" else "停止"}")
        if (snoozeEnabled) {
            startService(Intent(this, AlarmPlaybackService::class.java).apply {
                action = AlarmPlaybackService.ACTION_SNOOZE_ALARM
            })
        } else {
            AlarmAlertActivity.setShouldFinish(true)
            startService(Intent(this, AlarmPlaybackService::class.java).apply {
                action = AlarmPlaybackService.ACTION_STOP_ALARM
            })
        }
        finish()
    }

    private fun restoreAlarmPlayback() {
        val prefs = getSharedPreferences("alarm_state", MODE_PRIVATE)

        val isAlarmPlaying = prefs.getBoolean("is_alarm_playing", false)
        if (!isAlarmPlaying) {
            Log.i(TAG, "闹钟未在播放状态，不恢复")
            return
        }

        val alarmId = prefs.getLong("alarm_id", -1L)
        val label = prefs.getString("alarm_label", getString(R.string.alarm_title)) ?: getString(R.string.alarm_title)
        val snoozeEnabled = prefs.getBoolean("snooze_enabled", false)

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
    // 贪睡必须由闹钟自身的开关决定，缺省关闭
    private var snoozeEnabled by mutableStateOf(false)
    // 闹钟是否配置了震动（决定响铃界面是否提供「立即停止震动」）
    private var alarmVibrate by mutableStateOf(true)

    private fun handleIntent(intent: Intent) {
        alarmId = intent.getLongExtra("alarm_id", -1L)
        alarmLabel = intent.getStringExtra("label") ?: ""
        snoozeEnabled = intent.getBooleanExtra("snooze_enabled", false)
        alarmVibrate = intent.getBooleanExtra("vibrate", true)
    }

    override fun onDestroy() {
        super.onDestroy()
        isActivityRunning = false
        shouldFinish = false
        Log.i(TAG, "闹钟提醒界面销毁")
    }
}

@Composable
fun AlarmAlertScreen(
    alarmId: Long,
    alarmLabel: String,
    snoozeEnabled: Boolean,
    alarmVibrate: Boolean = true
) {
    val context = LocalContext.current
    // 配色跟随应用主题（深色=深夜森林玻璃态，浅色=暖米纸杂志）
    val cfg = AlarmColorConfig.forTheme(isSystemInDarkTheme())

    var songTitle by remember { mutableStateOf("") }
    var songArtist by remember { mutableStateOf("") }
    var playbackWarning by remember { mutableStateOf<String?>(null) }
    var shouldVibrate by remember { mutableStateOf(alarmVibrate) }

    val fallbackDefaultReason = stringResource(R.string.fallback_music_failed)

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var currentWeekday by remember { mutableStateOf("") }
    var currentLunar by remember { mutableStateOf<String?>(null) }

    val activity = context as? AlarmAlertActivity
    val view = LocalView.current

    // 确保主题没有把状态栏/导航栏重新涂成不透明色，保持真正 edge-to-edge；
    // 图标明暗跟随主题
    val darkTheme = isSystemInDarkTheme()
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
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
        val statePrefs = context.getSharedPreferences("alarm_state", android.content.Context.MODE_PRIVATE)
        playbackWarning = statePrefs.getString("fallback_reason", null)
        // 震动开关以闹钟自身配置为权威来源（alarmVibrate 由 Intent 携带），
        // 持久化状态只用来兜底「当前是否正在震动」
        shouldVibrate = shouldVibrate || statePrefs.getBoolean("should_vibrate", false)
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
                            val reason = it.getStringExtra(AlarmPlaybackService.EXTRA_FALLBACK_REASON) ?: fallbackDefaultReason
                            val failedTitle = it.getStringExtra(AlarmPlaybackService.EXTRA_FALLBACK_TITLE)
                            if (!failedTitle.isNullOrEmpty()) songTitle = failedTitle
                            playbackWarning = reason
                        }
                        AlarmPlaybackService.ACTION_SNOOZE_ENDED -> {
                            val prefs = context.getSharedPreferences("alarm_state", android.content.Context.MODE_PRIVATE)
                            songTitle = prefs.getString("song_title", "") ?: ""
                            songArtist = prefs.getString("song_artist", "") ?: ""
                            // 震动开关以闹钟自身配置为准
                            shouldVibrate = alarmVibrate
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

    // 时间只到分钟，按秒轮询纯属浪费；但为了跨天/跨小时立刻刷新，
    // 仍然每秒检查一次，只有在显示值真的变化时才写入 State（避免无意义重组）。
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            val locale = Locale.getDefault()
            val time = SimpleDateFormat("HH:mm", locale).format(now.time)
            if (time != currentTime) currentTime = time

            val date = SimpleDateFormat(
                android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMd"),
                locale
            ).format(now.time)
            if (date != currentDate) currentDate = date

            // 完整星期名：中文「星期六」/ 英文「Saturday」
            val weekday = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, locale) ?: ""
            if (weekday != currentWeekday) currentWeekday = weekday

            // 农历只在中文环境展示，失败则留空
            val lunar = if (LunarDate.isChineseLocale(locale)) LunarDate.format(now.timeInMillis) else null
            if (lunar != currentLunar) currentLunar = lunar

            kotlinx.coroutines.delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        cfg.BackgroundTop,
                        cfg.BackgroundBottom
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
            // 杂志刊头：标题 + 日期
            Text(
                text = "SLEEPY TUNE",
                fontFamily = FontFamily.Serif,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                color = cfg.TextAccent
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 大标题：优先用户自定义标签；没有标签时用「星期几」当刊头标题
            Text(
                text = alarmLabel.ifEmpty { currentWeekday },
                fontFamily = FontFamily.Serif,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = cfg.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 日期行：星期几 · 几月几号 · 农历（中文环境）
            // 标题已经占用「星期几」时不再重复
            val lunarLabel = currentLunar?.takeIf { it.isNotEmpty() }
                ?.let { stringResource(R.string.alarm_lunar_date, it) }
            val dateDetail = listOfNotNull(
                currentWeekday.takeIf { alarmLabel.isNotEmpty() && it.isNotEmpty() },
                currentDate.takeIf { it.isNotEmpty() },
                lunarLabel
            ).joinToString("  ·  ")
            if (dateDetail.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = dateDetail,
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                    color = cfg.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp)
                    .height(1.dp)
                    .background(cfg.CardBorder)
            )

            Spacer(modifier = Modifier.weight(1f))

            // 杂志封面式大时间（日期/星期已上移到刊头，避免两处重复）
            Text(
                text = currentTime,
                fontFamily = FontFamily.Serif,
                fontSize = 96.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
                color = cfg.TextPrimary
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

            // 主操作区：贪睡类动作只保留一个——「稍后再震」（跟随闹钟的贪睡开关）；
            // 「停止闹钟」任何情况下都必须有。
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
                        shape = Radius.lg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, cfg.CardBorder),
                        modifier = Modifier.weight(1f).height(54.dp)
                    ) {
                        Icon(Icons.Default.Snooze, null, modifier = Modifier.size(20.dp), tint = cfg.TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.alarm_snooze),
                            color = cfg.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            AlarmAlertActivity.setShouldFinish(true)
                            context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                                action = AlarmPlaybackService.ACTION_STOP_ALARM
                            })
                            activity?.finish()
                        },
                        shape = Radius.lg,
                        colors = ButtonDefaults.buttonColors(containerColor = cfg.ButtonDanger),
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
                    shape = Radius.lg,
                    colors = ButtonDefaults.buttonColors(containerColor = cfg.ButtonDanger),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.alarm_stop_button), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // 震动操作区：不折叠、常驻可见。
            // 这里只保留「立即停止震动」——它是必须提供的一键动作；
            // 贪睡类动作已统一为上面唯一的一个「稍后再震」，不在震动区重复出现。
            if (shouldVibrate) {
                Spacer(modifier = Modifier.height(10.dp))
                SecondaryAlarmAction(
                    icon = Icons.Default.Vibration,
                    label = stringResource(R.string.alarm_stop_vibrate),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                            action = AlarmPlaybackService.ACTION_STOP_VIBRATE
                        })
                    }
                )
            }
        }
    }
}

// 次级动作按钮：细描边、无填充、低饱和文字，避免和主操作抢注意力
@Composable
private fun SecondaryAlarmAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cfg = AlarmColorConfig.forTheme(isSystemInDarkTheme())
    OutlinedButton(
        onClick = onClick,
        shape = Radius.md,
        border = androidx.compose.foundation.BorderStroke(1.dp, cfg.CardBorder),
        modifier = modifier.height(46.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = cfg.TextSecondary)
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = cfg.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PlaybackWarningBanner(text: String) {
    val cfg = AlarmColorConfig.forTheme(isSystemInDarkTheme())
    Surface(
        shape = Radius.md,
        color = cfg.WarningBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, cfg.WarningBorder),
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
                text = stringResource(R.string.alarm_fallback_warning, text),
                style = MaterialTheme.typography.bodyMedium,
                color = cfg.WarningText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MagazineSongCard(title: String, artist: String, onNextSong: () -> Unit) {
    val cfg = AlarmColorConfig.forTheme(isSystemInDarkTheme())
    Surface(
        shape = Radius.lg,
        color = cfg.CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, cfg.CardBorder),
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
                    color = cfg.TextAccent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = title,
                    fontFamily = FontFamily.Serif,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cfg.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (artist.isNotEmpty()) {
                    Text(
                        text = artist,
                        fontSize = 14.sp,
                        color = cfg.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            // 下一首：编辑风格的「细描边圆形」次级按钮。
            // 不用 FloatingActionButton —— 它的 6dp 投影会透过半透明容器显形，
            // 在浅色卡片上变成一块发灰的脏渐变。
            Surface(
                onClick = onNextSong,
                shape = CircleShape,
                color = cfg.ButtonSecondary,
                contentColor = cfg.IconAccent,
                border = androidx.compose.foundation.BorderStroke(1.dp, cfg.ButtonBorder),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.action_next_song),
                        tint = cfg.IconAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
