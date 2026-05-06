package com.example.sleepytune.utils

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

object PermissionUtils {
    
    fun checkStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun checkNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    // 检查悬浮窗权限
    fun checkOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    // 检查精确闹钟权限
    fun checkExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
    
    // 检查所有必要权限
    fun checkAllPermissions(context: Context): Boolean {
        return checkStoragePermission(context) &&
                checkNotificationPermission(context) &&
                checkExactAlarmPermission(context)
    }
    
    // 获取缺失的权限列表
    fun getMissingPermissions(context: Context): List<PermissionType> {
        val missing = mutableListOf<PermissionType>()
        
        if (!checkStoragePermission(context)) {
            missing.add(PermissionType.STORAGE)
        }
        if (!checkNotificationPermission(context)) {
            missing.add(PermissionType.NOTIFICATION)
        }
        if (!checkExactAlarmPermission(context)) {
            missing.add(PermissionType.EXACT_ALARM)
        }
        
        return missing
    }
    
    // 权限类型枚举
    enum class PermissionType(val displayName: String, val description: String, val isSystemSetting: Boolean = false) {
        STORAGE("存储权限", "扫描本地音乐文件"),
        NOTIFICATION("通知权限", "显示闹钟提醒通知"),
        EXACT_ALARM("精确闹钟", "确保闹钟准时响起"),
        BACKGROUND_POPUP("后台弹出界面", "闹钟响时在屏幕上弹出", isSystemSetting = true),
        OVERLAY("悬浮窗权限", "在其他应用上层显示"),
        BATTERY_OPTIMIZATION("电池优化", "关闭电池优化确保闹钟可靠运行", isSystemSetting = true),
        AUTO_START("自启动权限", "确保闹钟在系统启动后自动运行", isSystemSetting = true)
    }
    
    // 权限请求启动器
    private var storagePermissionLauncher: ActivityResultLauncher<String>? = null
    private var notificationPermissionLauncher: ActivityResultLauncher<String>? = null
    
    // 回调函数
    private var storagePermissionCallback: (() -> Unit)? = null
    private var notificationPermissionCallback: (() -> Unit)? = null
    
    // 初始化所有权限启动器
    fun initPermissionLaunchers(activity: ComponentActivity) {
        storagePermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                storagePermissionCallback?.invoke()
                android.util.Log.i("PermissionUtils", "存储权限已授予")
            } else {
                showPermissionDeniedDialog(activity, "需要存储权限来扫描本地音乐")
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher = activity.registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    notificationPermissionCallback?.invoke()
                    android.util.Log.i("PermissionUtils", "通知权限已授予")
                } else {
                    showPermissionDeniedDialog(activity, "需要通知权限来显示闹钟通知")
                }
            }
        }
    }
    
    fun requestStoragePermission(activity: ComponentActivity, onGranted: () -> Unit) {
        if (storagePermissionLauncher == null) {
            initPermissionLaunchers(activity)
        }
        storagePermissionCallback = onGranted
        
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        storagePermissionLauncher?.launch(permission)
    }
    
    fun requestNotificationPermission(activity: ComponentActivity, onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (notificationPermissionLauncher == null) {
                initPermissionLaunchers(activity)
            }
            notificationPermissionCallback = onGranted
            notificationPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onGranted()
        }
    }
    
    // 请求悬浮窗权限
    fun requestOverlayPermission(context: Context, onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            AlertDialog.Builder(context)
                .setTitle("需要悬浮窗权限")
                .setMessage("为了让闹钟响铃时能在屏幕上弹出提醒界面，请授予\"显示在其他应用上层\"权限。\n\n点击确定后，请在设置中开启此权限。")
                .setPositiveButton("去设置") { _, _ ->
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        openAppSettings(context)
                    }
                }
                .setNegativeButton("取消") { dialog, _ ->
                    dialog.dismiss()
                    onGranted()
                }
                .setOnDismissListener {
                    if (checkOverlayPermission(context)) {
                        android.util.Log.i("PermissionUtils", "悬浮窗权限已授予")
                        onGranted()
                    }
                }
                .show()
        } else {
            onGranted()
        }
    }
    
    // 请求精确闹钟权限
    fun requestExactAlarmPermission(context: Context, onGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                AlertDialog.Builder(context)
                    .setTitle("需要精确闹钟权限")
                    .setMessage("为了让闹钟准时响铃，请授予\"精确闹钟\"权限。\n\n点击确定后，请在设置中开启此权限。")
                    .setPositiveButton("去设置") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            openAppSettings(context)
                        }
                    }
                    .setNegativeButton("取消") { dialog, _ ->
                        dialog.dismiss()
                        onGranted()
                    }
                    .setOnDismissListener {
                        if (checkExactAlarmPermission(context)) {
                            android.util.Log.i("PermissionUtils", "精确闹钟权限已授予")
                            onGranted()
                        }
                    }
                    .show()
            } else {
                onGranted()
            }
        } else {
            onGranted()
        }
    }
    
    // 引导用户开启后台弹出界面权限（国产手机特有）
    fun requestBackgroundPopupPermission(context: Context, onGranted: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("需要后台弹出界面权限")
            .setMessage("为了让闹钟响铃时能弹出提醒界面，请开启\"后台弹出界面\"权限。\n\n" +
                    "操作步骤：\n" +
                    "1. 点击\"去设置\"\n" +
                    "2. 找到\"后台弹出界面\"或\"后台显示界面\"\n" +
                    "3. 开启此权限")
            .setPositiveButton("去设置") { _, _ ->
                openAppSettings(context)
            }
            .setNegativeButton("稍后设置") { dialog, _ ->
                dialog.dismiss()
                onGranted()
            }
            .show()
    }
    
    // 引导用户开启自启动权限（国产手机特有）
    fun requestAutoStartPermission(context: Context, onGranted: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("需要自启动权限")
            .setMessage("为了让闹钟在系统启动后能自动运行，请开启\"自启动\"权限。\n\n" +
                    "操作步骤：\n" +
                    "1. 点击\"去设置\"\n" +
                    "2. 找到\"自启动\"或\"开机自启动\"\n" +
                    "3. 开启此权限")
            .setPositiveButton("去设置") { _, _ ->
                openAutoStartSettings(context)
            }
            .setNegativeButton("稍后设置") { dialog, _ ->
                dialog.dismiss()
                onGranted()
            }
            .show()
    }
    
    // 打开自启动设置页面（适配不同厂商）
    private fun openAutoStartSettings(context: Context) {
        try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val intent = when (manufacturer) {
                "xiaomi", "redmi" -> {
                    // 小米/红米
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    }
                }
                "oppo" -> {
                    // OPPO
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    }
                }
                "vivo" -> {
                    // VIVO
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    }
                }
                "huawei", "honor" -> {
                    // 华为/荣耀
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }
                }
                "meizu" -> {
                    // 魅族
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.meizu.safe",
                            "com.meizu.safe.security.SHOW_APPSEC"
                        )
                        putExtra("packageName", context.packageName)
                    }
                }
                "samsung" -> {
                    // 三星
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.battery.ui.BatteryActivity"
                        )
                    }
                }
                else -> {
                    // 其他厂商，打开应用设置页面
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // 如果打开失败，回退到应用设置页面
            openAppSettings(context)
        }
    }
    
    // 打开应用设置页面
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("PermissionUtils", "无法打开设置页面: ${e.message}")
        }
    }
    
    private fun showPermissionDeniedDialog(context: Context, message: String) {
        AlertDialog.Builder(context)
            .setTitle("权限被拒绝")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ -> openAppSettings(context) }
            .setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
