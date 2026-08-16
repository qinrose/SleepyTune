package com.example.sleepytune.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.sleepytune.ui.screen.alarm.AlarmAlertActivity

class AlarmPlaybackReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmPlaybackReceiver"
        const val ACTION_FINISH_ACTIVITY = "com.example.sleepytune.FINISH_ACTIVITY"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        Log.i(TAG, "收到动作: $action")

        when (action) {
            AlarmPlaybackService.ACTION_STOP -> {
                // 先让 Activity 关闭，再彻底停止服务（同进程后可直接调用静态方法）
                AlarmAlertActivity.setShouldFinish(true)
                // 防止静态标志残留影响下一次闹钟提醒
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    AlarmAlertActivity.setShouldFinish(false)
                }, 5000)
                AlarmPlaybackService.stopAlarm()
                // 如果服务尚未创建（极端情况），再补一个停止命令
                if (AlarmPlaybackService.getInstance() == null) {
                    startServiceSafely(context, AlarmPlaybackService.ACTION_STOP_ALARM)
                }
                context.sendBroadcast(Intent(ACTION_FINISH_ACTIVITY))
                Log.i(TAG, "已停止闹钟")
            }

            AlarmPlaybackService.ACTION_SNOOZE -> {
                startServiceSafely(context, AlarmPlaybackService.ACTION_SNOOZE_ALARM)
                Log.i(TAG, "触发贪睡")
            }

            AlarmPlaybackService.ACTION_STOP_VIBRATE -> {
                startServiceSafely(context, AlarmPlaybackService.ACTION_STOP_VIBRATE)
                Log.i(TAG, "停止震动")
            }

            AlarmPlaybackService.ACTION_PAUSE_VIBRATE -> {
                startServiceSafely(context, AlarmPlaybackService.ACTION_PAUSE_VIBRATE)
                Log.i(TAG, "暂停震动")
            }
        }
    }

    private fun startServiceSafely(context: Context, action: String) {
        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
