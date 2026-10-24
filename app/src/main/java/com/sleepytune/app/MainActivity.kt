package com.sleepytune.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.sleepytune.app.service.AlarmStateManager
import com.sleepytune.app.wear.WearSyncManager
import com.sleepytune.app.ui.screen.alarm.AlarmAlertActivity
import com.sleepytune.app.ui.screen.alarmedit.AlarmEditScreen
import com.sleepytune.app.ui.screen.alarmlist.AlarmListScreen
import com.sleepytune.app.ui.screen.musiclibrary.MusicLibraryScreen
import com.sleepytune.app.ui.screen.playlist.PlaylistDetailScreen
import com.sleepytune.app.ui.screen.playlist.PlaylistScreen
import com.sleepytune.app.ui.screen.settings.LanguageSettingsScreen
import com.sleepytune.app.ui.screen.settings.PermissionGuideScreen
import com.sleepytune.app.ui.screen.settings.SettingsScreen
import com.sleepytune.app.ui.theme.SleepyTuneTheme
import com.sleepytune.app.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var alarmStateManager: AlarmStateManager
    @Inject lateinit var wearSyncManager: WearSyncManager

    private var lastAlarmAlertLaunchTime = 0L

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 关键：Android 10+ 即使系统栏已透明，系统仍可能自动叠加一层对比度遮罩，
        // 在手势导航区表现为一条浅白色横条（看起来像"没做边到边"）。显式关闭它。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        // 如果闹钟正在响，直接跳转到闹钟界面并结束当前Activity
        if (alarmStateManager.isAlarmPlayingCrossProcess(this)) {
            val alarmId = alarmStateManager.getAlarmIdCrossProcess(this)
            val alarmLabel = alarmStateManager.getAlarmLabelCrossProcess(this)
            val snoozeEnabled = alarmStateManager.getSnoozeEnabledCrossProcess(this)
            val vibrate = alarmStateManager.getVibrateStateCrossProcess(this).first
            startActivity(Intent(this, AlarmAlertActivity::class.java).apply {
                putExtra("alarm_id", alarmId)
                putExtra("label", alarmLabel)
                putExtra("snooze_enabled", snoozeEnabled)
                putExtra("vibrate", vibrate)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
            return
        }

        PermissionUtils.initPermissionLaunchers(this)

        setContent {
            // WindowSizeClass：驱动手机 / 折叠屏内外屏 / 平板的自适应布局
            val windowSizeClass = calculateWindowSizeClass(this)
            MainApp(widthSizeClass = windowSizeClass.widthSizeClass)
        }
    }

    override fun onResume() {
        super.onResume()
        // 应用回到前台时向手表推送一次最新闹钟数据（覆盖调度器等外部变更）
        lifecycleScope.launch { wearSyncManager.syncFromRepository() }
        // 如果闹钟正在响，直接跳转到闹钟界面
        // 添加防抖检查，避免短时间内重复启动
        val currentTime = System.currentTimeMillis()
        if (alarmStateManager.isAlarmPlayingCrossProcess(this) && 
            currentTime - lastAlarmAlertLaunchTime > 1000) {
            lastAlarmAlertLaunchTime = currentTime
            val alarmId = alarmStateManager.getAlarmIdCrossProcess(this)
            val alarmLabel = alarmStateManager.getAlarmLabelCrossProcess(this)
            val snoozeEnabled = alarmStateManager.getSnoozeEnabledCrossProcess(this)
            val vibrate = alarmStateManager.getVibrateStateCrossProcess(this).first
            startActivity(Intent(this, AlarmAlertActivity::class.java).apply {
                putExtra("alarm_id", alarmId)
                putExtra("label", alarmLabel)
                putExtra("snooze_enabled", snoozeEnabled)
                putExtra("vibrate", vibrate)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
    }
}

@Composable
fun MainApp(widthSizeClass: WindowWidthSizeClass) {
    // 使用 AppCompatDelegate 直接判定主题，避免 ViewModel 在 Activity 重建后持有旧值
    val darkTheme = when (AppCompatDelegate.getDefaultNightMode()) {
        AppCompatDelegate.MODE_NIGHT_NO -> false
        AppCompatDelegate.MODE_NIGHT_YES -> true
        else -> isSystemInDarkTheme() // MODE_NIGHT_FOLLOW_SYSTEM
    }

    // 动态取色开关（设置页可关，Android 12+ 生效）
    val themeViewModel: com.sleepytune.app.ui.viewmodel.ThemeViewModel = hiltViewModel()
    val dynamicColor by themeViewModel.dynamicColor.collectAsState()

    // edgeToEdge：系统栏透明、内容延伸到底；各页面 Scaffold/TopAppBar 自带 inset，
    // 内容不会顶到状态栏
    SleepyTuneTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, edgeToEdge = true) {
        MainAppContent(widthSizeClass = widthSizeClass)
    }
}

@Composable
fun MainAppContent(widthSizeClass: WindowWidthSizeClass) {
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
        MainScreen(widthSizeClass = widthSizeClass)
    }
}

// 统一导航过渡动画：所有页面淡入淡出，消除不一的滑入/浮现效果
private const val NAV_ANIM_DURATION = 250

// 展开宽度（≥600dp，平板/折叠屏内屏）下内容列的最大宽度，居中显示
private val ExpandedContentMaxWidth = 640.dp

@Composable
fun MainScreen(widthSizeClass: WindowWidthSizeClass) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 自适应导航：Compact（手机/折叠屏外屏）用底部导航栏，Medium/Expanded（折叠屏内屏/平板）用侧边导航栏
    val useNavigationRail = widthSizeClass != WindowWidthSizeClass.Compact

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // 顶部 inset 交给各页面自己的 Scaffold/TopAppBar 处理：
        // 外层若再消化一次状态栏，标题会被顶下去整整一个状态栏高度。
        // 底部仍由 NavigationBar 自己吸收手势条 inset。
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Bottom),
        bottomBar = {
            if (!useNavigationRail) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { navController.navigate(it) }
                )
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (useNavigationRail) {
                NavigationRail(
                    // 与页面同底色，平板/折叠屏展开态同样是连续的一片背景
                    containerColor = MaterialTheme.colorScheme.background,
                    header = {
                        // 侧栏顶部放应用标识，保持品牌感
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                ) {
                    NavItem.entries.forEach { item ->
                        NavigationRailItem(
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
                            }
                        )
                    }
                }
            }

            // 内容区：展开宽度下限宽居中（平板/折叠屏内屏的"居中列"标准模式）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.TopCenter
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "alarmList",
                    modifier = Modifier.widthIn(max = ExpandedContentMaxWidth)
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
    }
}

/**
 * 底部导航栏（手机 / 折叠屏外屏）
 *
 * 为什么不用 M3 NavigationBar：
 * 它的固定 80dp 高度 + 内建的上下留白，在"图标 + 文字"只有 40dp 的场景里
 * 会在分隔线下面留出一整条空白，看起来像没做完。这里用紧凑的自定义实现：
 * 分隔线 → 6dp → 图标胶囊 26dp → 3dp → 文字 → 6dp，总高约 63dp（不含手势条 inset），
 * 视觉上更贴近内容，也不再有多余的空带。
 */
@Composable
private fun AppBottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.background, tonalElevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 1.dp,
                color = scheme.outlineVariant.copy(alpha = 0.5f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem.entries.forEach { item ->
                    val selected = currentRoute == item.route
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelect(item.route) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 48.dp, height = 26.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    if (selected) scheme.primaryContainer else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.getLabel(),
                                modifier = Modifier.size(19.dp),
                                tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = item.getLabel(),
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
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
