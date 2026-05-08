package com.example.sleepytune

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.sleepytune.service.AlarmPlaybackService
import com.example.sleepytune.service.AlarmStateManager
import com.example.sleepytune.ui.screen.alarm.AlarmAlertActivity
import com.example.sleepytune.ui.screen.alarmedit.AlarmEditScreen
import com.example.sleepytune.ui.screen.alarmlist.AlarmListScreen
import com.example.sleepytune.ui.screen.musiclibrary.MusicLibraryScreen
import com.example.sleepytune.ui.screen.playlist.PlaylistDetailScreen
import com.example.sleepytune.ui.screen.playlist.PlaylistScreen
import com.example.sleepytune.ui.screen.settings.LanguageSettingsScreen
import com.example.sleepytune.ui.screen.settings.PermissionGuideScreen
import com.example.sleepytune.ui.screen.settings.SettingsScreen
import com.example.sleepytune.ui.theme.SleepyTuneTheme
import com.example.sleepytune.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var alarmStateManager: AlarmStateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        PermissionUtils.initPermissionLaunchers(this)

        // 检查是否有活动的闹钟，如果有则直接启动闹钟界面
        checkAndLaunchAlarmAlert()

        setContent {
            // 主题已由 AppCompatDelegate 处理 - SleepyTuneTheme 使用 LocalConfiguration
            val isAlarmPlaying by alarmStateManager.isAlarmPlaying.collectAsState()
            val alarmLabel by alarmStateManager.alarmLabel.collectAsState()

            SleepyTuneTheme {
                if (isAlarmPlaying) {
                    AlarmPlayingOverlay(
                        alarmLabel = alarmLabel,
                        onStop = { stopCurrentAlarm() }
                    )
                } else {
                    MainAppContent()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 当从后台恢复时，检查是否有活动的闹钟
        checkAndLaunchAlarmAlert()
    }

    private fun checkAndLaunchAlarmAlert() {
        // 使用AlarmPlaybackService的静态方法检查闹钟是否正在播放
        if (AlarmPlaybackService.isPlaying() || alarmStateManager.isAlarmPlaying.value) {
            val alarmLabel = alarmStateManager.alarmLabel.value
            startActivity(Intent(this, AlarmAlertActivity::class.java).apply {
                putExtra("alarm_id", -1L)
                putExtra("label", alarmLabel)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
    }

    private fun stopCurrentAlarm() {
        val intent = Intent(this, AlarmPlaybackService::class.java)
        stopService(intent)
        alarmStateManager.clearState()
    }
}

@Composable
fun MainAppContent() {
    val context = LocalContext.current
    var showPermissionGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!PermissionUtils.checkAllPermissions(context)) {
            showPermissionGuide = true
        }
    }

    if (showPermissionGuide) {
        PermissionGuideScreen(
            onNavigateBack = { showPermissionGuide = false },
            onAllPermissionsGranted = { showPermissionGuide = false }
        )
    } else {
        MainScreen()
    }
}

@Composable
fun AlarmPlayingOverlay(alarmLabel: String, onStop: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = stringResource(R.string.alarm_title),
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.alarm_ringing),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (alarmLabel.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = alarmLabel,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(56.dp))

            Button(
                onClick = onStop,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = stringResource(R.string.alarm_stop),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.alarm_stop_button),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.alarm_stop_hint),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                NavItem.values().forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = { navController.navigate(item.route) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.getLabel()
                            )
                        },
                        label = {
                            Text(
                                text = item.getLabel(),
                                fontSize = 12.sp,
                                fontWeight = if (currentRoute == item.route) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "alarmList",
            modifier = Modifier.padding(padding)
        ) {
            composable("alarmList") {
                AlarmListScreen(navController = navController)
            }
            composable("alarmEdit") {
                AlarmEditScreen(navController = navController, alarmId = null)
            }
            composable("alarmEdit/{alarmId}") { backStackEntry ->
                val alarmId = backStackEntry.arguments?.getString("alarmId")?.toLongOrNull()
                AlarmEditScreen(navController = navController, alarmId = alarmId)
            }
            composable("playlist") {
                PlaylistScreen(navController = navController)
            }
            composable("playlist/{playlistId}") { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId")?.toLongOrNull()
                if (playlistId != null) {
                    PlaylistDetailScreen(navController = navController, playlistId = playlistId)
                }
            }
            composable("musicLibrary") {
                MusicLibraryScreen(navController = navController)
            }
            composable("settings") {
                SettingsScreen(navController = navController)
            }
            composable("languageSettings") {
                LanguageSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

enum class NavItem(val route: String, val labelRes: Int, val icon: ImageVector) {
    ALARM("alarmList", R.string.nav_alarms, Icons.Default.Alarm),
    PLAYLIST("playlist", R.string.nav_playlists, Icons.Default.MusicNote),
    MUSIC("musicLibrary", R.string.nav_music, Icons.Default.Folder),
    SETTINGS("settings", R.string.nav_settings, Icons.Default.Settings);

    @Composable
    fun getLabel(): String = stringResource(labelRes)
}
