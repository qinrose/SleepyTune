package com.example.sleepytune

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
    // 使用 AppCompatDelegate 直接判定主题，避免 ViewModel 在 Activity 重建后持有旧值
    val darkTheme = when (AppCompatDelegate.getDefaultNightMode()) {
        AppCompatDelegate.MODE_NIGHT_NO -> false
        AppCompatDelegate.MODE_NIGHT_YES -> true
        else -> isSystemInDarkTheme() // MODE_NIGHT_FOLLOW_SYSTEM
    }

    // 动态取色开关（设置页可关，Android 12+ 生效）
    val themeViewModel: com.example.sleepytune.ui.viewmodel.ThemeViewModel = hiltViewModel()
    val dynamicColor by themeViewModel.dynamicColor.collectAsState()

    // edgeToEdge：系统栏透明、内容延伸到底；各页面 Scaffold/TopAppBar 自带 inset，
    // 内容不会顶到状态栏
    SleepyTuneTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, edgeToEdge = true) {
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

// 统一导航过渡动画：所有页面淡入淡出，消除不一的滑入/浮现效果
private const val NAV_ANIM_DURATION = 250

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
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
            // 统一淡入淡出过渡（所有页面一致，消除滑入/浮现不统一的问题）
            fun navAnim() = fadeIn(tween(NAV_ANIM_DURATION))
            fun navAnimOut() = fadeOut(tween(NAV_ANIM_DURATION))

            composable("alarmList", enterTransition = { navAnim() }, exitTransition = { navAnimOut() }, popEnterTransition = { navAnim() }, popExitTransition = { navAnimOut() }) {
                AlarmListScreen(navController = navController)
            }
            composable("playlist", enterTransition = { navAnim() }, exitTransition = { navAnimOut() }, popEnterTransition = { navAnim() }, popExitTransition = { navAnimOut() }) {
                PlaylistScreen(navController = navController)
            }
            composable("musicLibrary", enterTransition = { navAnim() }, exitTransition = { navAnimOut() }, popEnterTransition = { navAnim() }, popExitTransition = { navAnimOut() }) {
                MusicLibraryScreen(navController = navController)
            }
            composable("settings", enterTransition = { navAnim() }, exitTransition = { navAnimOut() }, popEnterTransition = { navAnim() }, popExitTransition = { navAnimOut() }) {
                SettingsScreen(navController = navController)
            }
            composable("alarmEdit", enterTransition = { navAnim() }, exitTransition = { navAnimOut() }, popEnterTransition = { navAnim() }, popExitTransition = { navAnimOut() }) {
                AlarmEditScreen(navController = navController, alarmId = null)
            }
            composable("alarmEdit/{alarmId}", enterTransition = { navAnim() }, exitTransition = { navAnimOut() }, popEnterTransition = { navAnim() }, popExitTransition = { navAnimOut() }) { backStackEntry ->
                val alarmId = backStackEntry.arguments?.getString("alarmId")?.toLongOrNull()
                AlarmEditScreen(navController = navController, alarmId = alarmId)
            }
            composable("playlist/{playlistId}", enterTransition = { navAnim() }, exitTransition = { navAnimOut() }, popEnterTransition = { navAnim() }, popExitTransition = { navAnimOut() }) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId")?.toLongOrNull()
                if (playlistId != null) {
                    PlaylistDetailScreen(navController = navController, playlistId = playlistId)
                }
            }
            composable("languageSettings", enterTransition = { navAnim() }, exitTransition = { navAnimOut() }, popEnterTransition = { navAnim() }, popExitTransition = { navAnimOut() }) {
                LanguageSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable("permissionGuide", enterTransition = { navAnim() }, exitTransition = { navAnimOut() }, popEnterTransition = { navAnim() }, popExitTransition = { navAnimOut() }) {
                PermissionGuideScreen(onNavigateBack = { navController.popBackStack() }, onAllPermissionsGranted = { navController.popBackStack() })
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
