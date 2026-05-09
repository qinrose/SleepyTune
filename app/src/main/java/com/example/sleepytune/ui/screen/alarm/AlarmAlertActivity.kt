package com.example.sleepytune.ui.screen.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sleepytune.R
import com.example.sleepytune.service.AlarmPlaybackService
import com.example.sleepytune.ui.theme.AlarmColorConfig
import com.example.sleepytune.ui.theme.SleepyTuneTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class AlarmAlertActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AlarmAlertActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 显式设置导航栏和状态栏完全透明
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
                setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }
        
        Log.i(TAG, "闹钟提醒界面启动")

        val alarmId = intent.getLongExtra("alarm_id", -1L)
        val alarmLabel = intent.getStringExtra("label") ?: ""

        setContent {
            SleepyTuneTheme {
                AlarmAlertScreen(alarmId = alarmId, alarmLabel = alarmLabel)
            }
        }
    }

    override fun onBackPressed() {}
}

@Composable
fun AlarmAlertScreen(alarmId: Long, alarmLabel: String) {
    val context = LocalContext.current

    // 立即从跨进程状态读取初始歌曲信息
    var songTitle by remember { mutableStateOf("") }
    var songArtist by remember { mutableStateOf("") }
    var isVibrating by remember { mutableStateOf(false) }
    var shouldVibrate by remember { mutableStateOf(true) }
    
    // 时间相关状态
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var currentWeekday by remember { mutableStateOf("") }

    // 启动时立即读取跨进程的歌曲信息和震动状态
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("alarm_state", android.content.Context.MODE_MULTI_PROCESS)
        songTitle = prefs.getString("song_title", "") ?: ""
        songArtist = prefs.getString("song_artist", "") ?: ""
        shouldVibrate = prefs.getBoolean("should_vibrate", true)
        isVibrating = prefs.getBoolean("is_vibrating", false)
    }

    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                intent?.let {
                    songTitle = it.getStringExtra(AlarmPlaybackService.EXTRA_SONG_TITLE) ?: ""
                    songArtist = it.getStringExtra(AlarmPlaybackService.EXTRA_SONG_ARTIST) ?: ""
                }
            }
        }
        val filter = android.content.IntentFilter(AlarmPlaybackService.ACTION_UPDATE_PLAYBACK_INFO)
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }
    
    // 更新时间
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

    val btnColors = ButtonDefaults.buttonColors(containerColor = AlarmColorConfig.ButtonPrimary)
    val btnShape = RoundedCornerShape(16.dp)

    // 使用 LayeredBox：底层背景延伸到状态栏，内容避开导航栏
    Box(modifier = Modifier.fillMaxSize()) {
        // 底层背景 - 延伸到整个屏幕（包括状态栏）
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
                .noiseOverlay()
        )
        
        // 上层内容 - 只避开底部导航栏
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .statusBarsPadding()
        ) {
            // 时间显示区域 - 顶部
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text(
                    text = currentTime,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    color = AlarmColorConfig.TextPrimary,
                    letterSpacing = 4.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentDate,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = AlarmColorConfig.TextSecondary
                    )
                    Text(
                        text = "·",
                        fontSize = 16.sp,
                        color = AlarmColorConfig.TextSecondary
                    )
                    Text(
                        text = currentWeekday,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = AlarmColorConfig.TextSecondary
                    )
                }
            }
            
            // 歌曲信息区域 - 居中
            Spacer(modifier = Modifier.weight(1f))
            
            if (songTitle.isNotEmpty()) {
                SongInfoRow(
                    title = songTitle,
                    artist = songArtist,
                    onNextSong = {
                        context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                            action = AlarmPlaybackService.ACTION_NEXT_SONG
                        })
                    }
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))

            // 按钮区域
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                                action = AlarmPlaybackService.ACTION_SNOOZE_ALARM
                            })
                            (context as? AlarmAlertActivity)?.finish()
                        },
                        shape = btnShape,
                        colors = btnColors,
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Default.Snooze, null, modifier = Modifier.size(20.dp), tint = AlarmColorConfig.ButtonText)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.alarm_snooze), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AlarmColorConfig.ButtonText)
                    }

                    Button(
                        onClick = {
                            context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                                action = AlarmPlaybackService.ACTION_STOP_ALARM
                            })
                            (context as? AlarmAlertActivity)?.finish()
                        },
                        shape = btnShape,
                        colors = ButtonDefaults.buttonColors(containerColor = AlarmColorConfig.ButtonDanger),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(20.dp), tint = AlarmColorConfig.ButtonText)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.alarm_stop_button), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AlarmColorConfig.ButtonText)
                    }
                }

                // 只有在启用了震动且正在震动时才显示震动按钮
                if (shouldVibrate && isVibrating) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                                    action = AlarmPlaybackService.ACTION_PAUSE_VIBRATE
                                })
                                isVibrating = false
                            },
                            shape = btnShape,
                            colors = ButtonDefaults.buttonColors(containerColor = AlarmColorConfig.ButtonSecondary),
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Icon(Icons.Default.Pause, null, modifier = Modifier.size(20.dp), tint = AlarmColorConfig.ButtonText)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.alarm_pause_vibrate), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AlarmColorConfig.ButtonText)
                        }

                        Button(
                            onClick = {
                                context.startService(Intent(context, AlarmPlaybackService::class.java).apply {
                                    action = AlarmPlaybackService.ACTION_STOP_VIBRATE
                                })
                                isVibrating = false
                            },
                            shape = btnShape,
                            colors = ButtonDefaults.buttonColors(containerColor = AlarmColorConfig.ButtonSecondary),
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Icon(Icons.Default.Vibration, null, modifier = Modifier.size(20.dp), tint = AlarmColorConfig.ButtonText)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.alarm_stop_vibrate), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AlarmColorConfig.ButtonText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongInfoRow(title: String, artist: String, onNextSong: () -> Unit) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val containerWidth = with(LocalDensity.current) { 260.dp.toPx() }
    val textWidth = layoutResult?.size?.width?.toFloat() ?: 0f
    val isOverflow = textWidth > containerWidth

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isOverflow) {
                    val transition = rememberInfiniteTransition(label = "marquee")
                    val offset by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = -abs(textWidth - containerWidth),
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 4000, delayMillis = 800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "offset"
                    )
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = AlarmColorConfig.TextPrimary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.offset(x = with(LocalDensity.current) { offset.toDp() }),
                        onTextLayout = { layoutResult = it }
                    )
                } else {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = AlarmColorConfig.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { layoutResult = it }
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artist,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = AlarmColorConfig.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        FloatingActionButton(
            onClick = onNextSong,
            shape = CircleShape,
            containerColor = AlarmColorConfig.ButtonPrimary,
            contentColor = AlarmColorConfig.ButtonText,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(24.dp))
        }
    }
}

private fun Modifier.noiseOverlay(): Modifier = this.drawWithCache {
    val w = size.width.toInt().coerceAtLeast(1)
    val h = size.height.toInt().coerceAtLeast(1)
    val step = 3
    val noiseBitmap = androidx.compose.ui.graphics.ImageBitmap(w, h)
    val canvas = androidx.compose.ui.graphics.Canvas(noiseBitmap)
    val rng = java.util.Random(42)
    val paint = Paint().apply { alpha = AlarmColorConfig.NoiseAlpha }
    for (x in 0 until w step step) {
        for (y in 0 until h step step) {
            val gray = AlarmColorConfig.NoiseGrayMin + rng.nextInt(AlarmColorConfig.NoiseGrayMax - AlarmColorConfig.NoiseGrayMin + 1)
            paint.color = Color(gray, gray, gray)
            canvas.drawRect(androidx.compose.ui.geometry.Rect(x.toFloat(), y.toFloat(), (x + step).toFloat(), (y + step).toFloat()), paint)
        }
    }
    onDrawBehind {
        drawImage(noiseBitmap, alpha = AlarmColorConfig.NoiseAlpha)
    }
}
