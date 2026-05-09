package com.example.sleepytune

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 如果闹钟正在响，直接跳转到闹钟界面并结束当前Activity
        if (alarmStateManager.isAlarmPlayingCrossProcess(this)) {
            val alarmLabel = alarmStateManager.getAlarmLabelCrossProcess(this)
            startActivity(Intent(this, AlarmAlertActivity::class.java).apply {
                putExtra("alarm_id", -1L)
                putExtra("label", alarmLabel)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
            return
        }

        PermissionUtils.initPermissionLaunchers(this)

        setContent {
            SleepyTuneTheme {
                MainAppContent()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 如果闹钟正在响，直接跳转到闹钟界面
        if (alarmStateManager.isAlarmPlayingCrossProcess(this)) {
            val alarmLabel = alarmStateManager.getAlarmLabelCrossProcess(this)
            startActivity(Intent(this, AlarmAlertActivity::class.java).apply {
                putExtra("alarm_id", -1L)
                putExtra("label", alarmLabel)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
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
