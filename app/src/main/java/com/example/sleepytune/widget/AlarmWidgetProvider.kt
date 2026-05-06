package com.example.sleepytune.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.example.sleepytune.MainActivity
import com.example.sleepytune.R
import com.example.sleepytune.data.local.database.AppDatabase
import com.example.sleepytune.ui.viewmodel.TimeFormatViewModel
import com.example.sleepytune.utils.TimeFormatHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "AlarmWidget"
        const val ACTION_TOGGLE_ALARM = "com.example.sleepytune.TOGGLE_ALARM"
        const val ACTION_UPDATE_WIDGET = "com.example.sleepytune.UPDATE_WIDGET"
        
        // 更新所有Widget
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, AlarmWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            context.sendBroadcast(intent)
        }
    }
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.i(TAG, "onUpdate: ${appWidgetIds.size}个Widget")
        
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_TOGGLE_ALARM -> {
                val alarmId = intent.getLongExtra("alarm_id", -1L)
                if (alarmId != -1L) {
                    toggleAlarm(context, alarmId)
                }
            }
            ACTION_UPDATE_WIDGET -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, AlarmWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
        }
    }
    
    override fun onEnabled(context: Context) {
        Log.i(TAG, "Widget已启用")
    }
    
    override fun onDisabled(context: Context) {
        Log.i(TAG, "Widget已禁用")
    }
    
    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // 异步获取下一个闹钟
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val alarms = database.alarmDao().getAllAlarmsOnce()
                val enabledAlarms = alarms.filter { it.isEnabled }
                
                val nextAlarm = findNextAlarm(enabledAlarms)
                
                // 读取时间制设置
                val prefs = context.getSharedPreferences("time_format_settings", Context.MODE_PRIVATE)
                val savedFormat = prefs.getString("time_format", TimeFormatViewModel.TimeFormat.HOUR_24.name) 
                    ?: TimeFormatViewModel.TimeFormat.HOUR_24.name
                val timeFormat = try {
                    TimeFormatViewModel.TimeFormat.valueOf(savedFormat)
                } catch (_: IllegalArgumentException) {
                    TimeFormatViewModel.TimeFormat.HOUR_24
                }
                
                // 创建RemoteViews
                val views = RemoteViews(context.packageName, R.layout.alarm_widget)
                
                if (nextAlarm != null) {
                    views.setTextViewText(
                        R.id.tv_next_alarm_time,
                        TimeFormatHelper.formatTime(nextAlarm.hour, nextAlarm.minute, timeFormat)
                    )
                    views.setTextViewText(
                        R.id.tv_alarm_label,
                        nextAlarm.label ?: "闹钟"
                    )
                    
                    // 设置点击打开应用
                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    val openPendingIntent = PendingIntent.getActivity(
                        context, 0, openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.tv_next_alarm_time, openPendingIntent)
                    
                    // 设置开关点击
                    val toggleIntent = Intent(context, AlarmWidgetProvider::class.java).apply {
                        action = ACTION_TOGGLE_ALARM
                        putExtra("alarm_id", nextAlarm.id)
                    }
                    val togglePendingIntent = PendingIntent.getBroadcast(
                        context, nextAlarm.id.toInt(), toggleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.iv_toggle, togglePendingIntent)
                    
                } else {
                    views.setTextViewText(R.id.tv_next_alarm_time, "--:--")
                    views.setTextViewText(R.id.tv_alarm_label, "无闹钟")
                }
                
                appWidgetManager.updateAppWidget(appWidgetId, views)
                
            } catch (e: Exception) {
                Log.e(TAG, "更新Widget失败: ${e.message}")
            }
        }
    }
    
    private fun findNextAlarm(alarms: List<com.example.sleepytune.data.local.entity.Alarm>): com.example.sleepytune.data.local.entity.Alarm? {
        if (alarms.isEmpty()) return null
        
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK) // 1=周日, 2=周一...
        
        var nextAlarm: com.example.sleepytune.data.local.entity.Alarm? = null
        var minMinutes = Long.MAX_VALUE
        
        for (alarm in alarms) {
            val alarmMinutes = calculateMinutesUntilAlarm(
                alarm.hour, alarm.minute, alarm.repeatDays,
                currentHour, currentMinute, currentDay
            )
            
            if (alarmMinutes < minMinutes) {
                minMinutes = alarmMinutes
                nextAlarm = alarm
            }
        }
        
        return nextAlarm
    }
    
    private fun calculateMinutesUntilAlarm(
        alarmHour: Int, alarmMinute: Int,
        repeatDays: Set<Int>?,
        currentHour: Int, currentMinute: Int,
        currentDay: Int
    ): Long {
        val alarmMinutes = alarmHour * 60 + alarmMinute
        val nowMinutes = currentHour * 60 + currentMinute
        
        if (repeatDays == null || repeatDays.isEmpty()) {
            // 一次性闹钟
            return if (alarmMinutes > nowMinutes) {
                (alarmMinutes - nowMinutes).toLong()
            } else {
                (24 * 60 - nowMinutes + alarmMinutes).toLong()
            }
        }
        
        // 重复闹钟
        val currentDayOfWeek = if (currentDay == 1) 7 else currentDay - 1 // 转换为1=周一
        
        var minDiff = Long.MAX_VALUE
        for (day in repeatDays) {
            var dayDiff = day - currentDayOfWeek
            if (dayDiff < 0) dayDiff += 7
            
            val totalMinutes = dayDiff.toLong() * 24 * 60 + alarmMinutes - nowMinutes
            if (totalMinutes < 0) continue
            if (totalMinutes < minDiff) minDiff = totalMinutes
        }
        
        return if (minDiff == Long.MAX_VALUE) 0 else minDiff
    }
    
    private fun toggleAlarm(context: Context, alarmId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val alarm = database.alarmDao().getAlarmById(alarmId)
                if (alarm != null) {
                    val updated = alarm.copy(isEnabled = !alarm.isEnabled)
                    database.alarmDao().update(updated)
                    
                    // 更新Widget
                    updateAllWidgets(context)
                    
                    Log.i(TAG, "闹钟${if (updated.isEnabled) "启用" else "禁用"}: $alarmId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "切换闹钟状态失败: ${e.message}")
            }
        }
    }
}
