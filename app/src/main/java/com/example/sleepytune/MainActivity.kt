package com.example.sleepytune

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import com.example.sleepytune.ui.viewmodel.ThemeViewModel
import com.example.sleepytune.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var alarmStateManager: AlarmStateManager
    
    private var lastAlarmAlertLaunchTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 如果闹钟正在响，直接跳转到闹钟界面并结束当前Activity
        if (alarmStateManager.isAlarmPlayingCrossProcess(this)) {
            val alarmId = alarmStateManager.getAlarmIdCrossProcess(this)
            val alarmLabel = alarmStateManager.getAlarmLabelCrossProcess(this)
            val snoozeEnabled = alarmStateManager.getSnoozeEnabledCrossProcess(this)
            startActivity(Intent(this, AlarmAlertActivity::class.java).apply {
                putExtra("alarm_id", alarmId)
                putExtra("label", alarmLabel)
                putExtra("snooze_enabled", snoozeEnabled)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
            return
        }

        PermissionUtils.initPermissionLaunchers(this)

        setContent {
            MainApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // 如果闹钟正在响，直接跳转到闹钟界面
        // 添加防抖检查，避免短时间内重复启动
        val currentTime = System.currentTimeMillis()
        if (alarmStateManager.isAlarmPlayingCrossProcess(this) && 
            currentTime - lastAlarmAlertLaunchTime > 1000) {
            lastAlarmAlertLaunchTime = currentTime
            val alarmId = alarmStateManager.getAlarmIdCrossProcess(this)
            val alarmLabel = alarmStateManager.getAlarmLabelCrossProcess(this)
            val snoozeEnabled = alarmStateManager.getSnoozeEnabledCrossProcess(this)
            startActivity(Intent(this, AlarmAlertActivity::class.java).apply {
                putExtra("alarm_id", alarmId)
                putExtra("label", alarmLabel)
                putExtra("snooze_enabled", snoozeEnabled)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
    }
}

@Composable
fun MainApp() {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
    
    // 根据主题模式确定是否使用深色主题
    val darkTheme = when (themeMode) {
        ThemeViewModel.ThemeMode.LIGHT -> false
        ThemeViewModel.ThemeMode.DARK -> true
        ThemeViewModel.ThemeMode.AUTO -> isSystemInDarkTheme()
    }

    SleepyTuneTheme(darkTheme = darkTheme) {
        MainAppContent()
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
                                contentDescription = item.getLabel(),
                                tint = if (currentRoute == item.route) 
                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                text = item.getLabel(),
                                fontSize = 12.sp,
                                fontWeight = if (currentRoute == item.route) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (currentRoute == item.route) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
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
            composable("permissionGuide") {
                PermissionGuideScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onAllPermissionsGranted = { navController.popBackStack() }
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
