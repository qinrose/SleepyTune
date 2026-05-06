package com.example.sleepytune.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sleepytune.R

// 电池优化设置页面
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryOptimizationScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(checkBatteryOptimization(context))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.battery_optimization_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标
            Icon(
                imageVector = if (isIgnoringBatteryOptimizations) {
                    Icons.Default.BatteryFull
                } else {
                    Icons.Default.BatteryAlert
                },
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = if (isIgnoringBatteryOptimizations) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 状态标题
            Text(
                text = if (isIgnoringBatteryOptimizations) {
                    if (java.util.Locale.getDefault().language == "zh") {
                        "电池优化已关闭"
                    } else {
                        "Battery Optimization Disabled"
                    }
                } else {
                    if (java.util.Locale.getDefault().language == "zh") {
                        "需要关闭电池优化"
                    } else {
                        "Battery Optimization Required"
                    }
                },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 说明文字
            Text(
                text = stringResource(R.string.battery_optimization_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 操作按钮
            if (!isIgnoringBatteryOptimizations) {
                Button(
                    onClick = {
                        requestIgnoreBatteryOptimization(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.battery_optimization_disable))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 手动设置按钮
            OutlinedButton(
                onClick = {
                    openBatteryOptimizationSettings(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (java.util.Locale.getDefault().language == "zh") {
                        "打开系统设置"
                    } else {
                        "Open System Settings"
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 说明卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (java.util.Locale.getDefault().language == "zh") {
                            "为什么需要关闭电池优化？"
                        } else {
                            "Why disable battery optimization?"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (java.util.Locale.getDefault().language == "zh") {
                            """
                            • 确保闹钟准时响起
                            • 防止系统在后台终止应用
                            • 保证贪睡功能正常工作
                            • 支持设备重启后恢复闹钟
                            """.trimIndent()
                        } else {
                            """
                            • Ensure alarms ring on time
                            • Prevent system from killing the app
                            • Keep snooze functionality working
                            • Support alarm recovery after reboot
                            """.trimIndent()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 提示卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (java.util.Locale.getDefault().language == "zh") {
                            "提示"
                        } else {
                            "Tip"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (java.util.Locale.getDefault().language == "zh") {
                            "不同手机厂商的设置路径可能不同，请根据您的设备在设置中找到 Sleepy Tune 并允许后台运行。"
                        } else {
                            "Settings path may vary by manufacturer. Please find Sleepy Tune in your device settings and allow background running."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// 检查是否忽略电池优化
private fun checkBatteryOptimization(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    return true
}

// 请求忽略电池优化
private fun requestIgnoreBatteryOptimization(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openBatteryOptimizationSettings(context)
        }
    }
}

// 打开电池优化设置
private fun openBatteryOptimizationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(intent)
    } catch (e: Exception) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}
