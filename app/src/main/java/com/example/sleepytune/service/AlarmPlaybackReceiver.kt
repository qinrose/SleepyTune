package com.example.sleepytune.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmPlaybackReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "AlarmPlaybackReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        Log.i(TAG, "收到动作: $action")
        
        when (action) {
            AlarmPlaybackService.ACTION_STOP -> {
                // 停止闹钟服务
                val stopIntent = Intent(context, AlarmPlaybackService::class.java)
                stopIntent.setAction(AlarmPlaybackService.ACTION_STOP_ALARM)
                context.startService(stopIntent)
                Log.i(TAG, "已停止闹钟")
            }
            
            AlarmPlaybackService.ACTION_SNOOZE -> {
                // 触发贪睡
                val snoozeIntent = Intent(context, AlarmPlaybackService::class.java)
                snoozeIntent.setAction(AlarmPlaybackService.ACTION_SNOOZE_ALARM)
                context.startService(snoozeIntent)
                Log.i(TAG, "触发贪睡")
            }

            AlarmPlaybackService.ACTION_STOP_VIBRATE -> {
                // 停止震动
                val stopVibrateIntent = Intent(context, AlarmPlaybackService::class.java)
                stopVibrateIntent.setAction(AlarmPlaybackService.ACTION_STOP_VIBRATE)
                context.startService(stopVibrateIntent)
                Log.i(TAG, "停止震动")
            }

            AlarmPlaybackService.ACTION_PAUSE_VIBRATE -> {
                // 暂停震动
                val pauseVibrateIntent = Intent(context, AlarmPlaybackService::class.java)
                pauseVibrateIntent.setAction(AlarmPlaybackService.ACTION_PAUSE_VIBRATE)
                context.startService(pauseVibrateIntent)
                Log.i(TAG, "暂停震动")
            }
        }
    }
}
