package com.sleepytune.app.ui.screen.settings
import com.sleepytune.app.ui.theme.Radius

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sleepytune.app.R
import com.sleepytune.app.utils.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGuideScreen(
    onNavigateBack: () -> Unit,
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    
    // 权限状态
    var storageGranted by remember { mutableStateOf(PermissionUtils.checkStoragePermission(context)) }
    var notificationGranted by remember { mutableStateOf(PermissionUtils.checkNotificationPermission(context)) }
    var exactAlarmGranted by remember { mutableStateOf(PermissionUtils.checkExactAlarmPermission(context)) }
    // 后台弹出界面无法检测，默认显示需要设置
    var backgroundPopupGranted by remember { mutableStateOf(false) }
    
    // 检查是否基础权限全部授权
    val allGranted = storageGranted && notificationGranted && exactAlarmGranted
    
    // 刷新权限状态
    fun refreshPermissions() {
        storageGranted = PermissionUtils.checkStoragePermission(context)
        notificationGranted = PermissionUtils.checkNotificationPermission(context)
        exactAlarmGranted = PermissionUtils.checkExactAlarmPermission(context)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.permission_guide_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶部说明卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = Radius.lg,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.permission_intro_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.permission_intro_desc),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 必要权限列表
            Text(
                text = stringResource(R.string.permission_section_essential),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            PermissionItem(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.permission_storage_title),
                description = stringResource(R.string.permission_storage_desc),
                isGranted = storageGranted,
                onClick = {
                    PermissionUtils.requestStoragePermission(
                        context as androidx.activity.ComponentActivity
                    ) { refreshPermissions() }
                }
            )

            PermissionItem(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.permission_notification_title),
                description = stringResource(R.string.permission_notification_guide_desc),
                isGranted = notificationGranted,
                onClick = {
                    PermissionUtils.requestNotificationPermission(
                        context as androidx.activity.ComponentActivity
                    ) { refreshPermissions() }
                }
            )

            // 通知设置入口
            PermissionItem(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.settings_notifications),
                description = stringResource(R.string.permission_notification_settings_desc),
                isGranted = notificationGranted,
                isSystemSetting = true,
                onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            )
            
            PermissionItem(
                icon = Icons.Default.Alarm,
                title = stringResource(R.string.permission_exact_alarm),
                description = stringResource(R.string.permission_exact_alarm_guide_desc),
                isGranted = exactAlarmGranted,
                onClick = {
                    PermissionUtils.requestExactAlarmPermission(context) { refreshPermissions() }
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 重要权限（系统设置类）
            Text(
                text = stringResource(R.string.permission_section_important),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            // 后台弹出界面权限（国产手机特有）
            PermissionItem(
                icon = Icons.Default.Layers,
                title = stringResource(R.string.permission_background_popup),
                description = stringResource(R.string.permission_background_popup_guide_desc),
                isGranted = backgroundPopupGranted,
                isSystemSetting = true,
                onClick = {
                    PermissionUtils.requestBackgroundPopupPermission(context) {
                        backgroundPopupGranted = true
                    }
                }
            )
            
            PermissionItem(
                icon = Icons.Default.BatteryAlert,
                title = stringResource(R.string.battery_optimization_title),
                description = stringResource(R.string.permission_battery_desc),
                isGranted = false,
                isSystemSetting = true,
                onClick = {
                    PermissionUtils.openAppSettings(context)
                }
            )

            // 自启动权限（国产手机特有）
            PermissionItem(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = stringResource(R.string.permission_autostart_title),
                description = stringResource(R.string.permission_autostart_guide_desc),
                isGranted = false,
                isSystemSetting = true,
                onClick = {
                    PermissionUtils.requestAutoStartPermission(context) {
                        // 用户设置后刷新
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 基础权限授权提示
            AnimatedVisibility(
                visible = allGranted,
                enter = fadeIn() + slideInVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = Radius.lg,
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.permission_basic_granted),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4CAF50)
                            )
                            Text(
                                text = stringResource(R.string.permission_popup_hint),
                                fontSize = 14.sp,
                                color = Color(0xFF4CAF50).copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            // 手动刷新按钮
            OutlinedButton(
                onClick = { refreshPermissions() },
                modifier = Modifier.fillMaxWidth(),
                shape = Radius.sm
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.permission_refresh_status))
            }
            
            // 完成按钮
            if (allGranted) {
                Button(
                    onClick = onAllPermissionsGranted,
                    modifier = Modifier.fillMaxWidth(),
                    shape = Radius.sm
                ) {
                    Text(stringResource(R.string.permission_finish_setup), fontWeight = FontWeight.Medium)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isSystemSetting: Boolean = false,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isGranted) 1f else 0.98f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scale"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = Radius.md,
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                Color(0xFF4CAF50).copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        onClick = if (!isGranted) onClick else {{}}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) {
                            Color(0xFF4CAF50).copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else icon,
                    contentDescription = null,
                    tint = if (isGranted) {
                        Color(0xFF4CAF50)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 文字内容
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isSystemSetting && !isGranted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.permission_manual_badge),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
            
            // 状态指示
            if (!isGranted) {
                FilledTonalButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        if (isSystemSetting) stringResource(R.string.nav_settings) else stringResource(R.string.permission_grant),
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
