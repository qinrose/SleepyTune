package com.example.sleepytune.ui.screen.alarm

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import kotlin.math.abs

class AlarmAlertActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AlarmAlertActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

    var songTitle by remember { mutableStateOf("") }
    var songArtist by remember { mutableStateOf("") }
    var isVibrating by remember { mutableStateOf(true) }

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

    val btnColors = ButtonDefaults.buttonColors(containerColor = AlarmColorConfig.ButtonPrimary)
    val btnShape = RoundedCornerShape(16.dp)

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
            .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .verticalScroll(rememberScrollState())
        ) {
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
                Spacer(modifier = Modifier.height(48.dp))
            }

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

            if (isVibrating) {
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

@Composable
private fun SongInfoRow(title: String, artist: String, onNextSong: () -> Unit) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val containerWidth = with(LocalDensity.current) { 280.dp.toPx() }
    val textWidth = layoutResult?.size?.width?.toFloat() ?: 0f
    val isOverflow = textWidth > containerWidth

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (isOverflow) {
                    val transition = rememberInfiniteTransition(label = "marquee")
                    val offset by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = -abs(textWidth - containerWidth),
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = title.length * 150, delayMillis = 1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "offset"
                    )
                    Text(
                        text = title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Normal,
                        color = AlarmColorConfig.TextPrimary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.offset(x = with(LocalDensity.current) { offset.toDp() }),
                        onTextLayout = { layoutResult = it }
                    )
                } else {
                    Text(
                        text = title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Normal,
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                color = AlarmColorConfig.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        FloatingActionButton(
            onClick = onNextSong,
            shape = CircleShape,
            containerColor = AlarmColorConfig.ButtonPrimary,
            contentColor = AlarmColorConfig.ButtonText,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(28.dp))
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
