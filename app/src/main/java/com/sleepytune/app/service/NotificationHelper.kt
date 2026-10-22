package com.sleepytune.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sleepytune.app.MainActivity
import com.sleepytune.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NotificationHelper"
        
        // 通知渠道ID
        const val CHANNEL_ID_ALARM = "alarm_channel"
        const val CHANNEL_ID_SNOOZE = "snooze_channel"
        const val CHANNEL_ID_SLEEP_TIMER = "sleep_timer_channel"
        const val CHANNEL_ID_MISSED = "missed_alarm_channel"
        const val CHANNEL_ID_PRE_ALARM = "pre_alarm_channel"
        
        // 通知ID
        const val NOTIFICATION_ID_ALARM = 1001
        const val NOTIFICATION_ID_SNOOZE = 1002
        const val NOTIFICATION_ID_SLEEP_TIMER = 1003
        const val NOTIFICATION_ID_MISSED = 1004
        const val NOTIFICATION_ID_PRE_ALARM = 1005
        
        // 通知动作
        const val ACTION_STOP = "com.sleepytune.app.ACTION_STOP"
        const val ACTION_SNOOZE = "com.sleepytune.app.ACTION_SNOOZE"
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannels()
    }
    
    // 创建所有通知渠道
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 闹钟播放渠道 - 不设置声音，由 AlarmPlaybackService 自己播放
            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM,
                context.getString(R.string.notification_channel_alarm_play),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_alarm_play_desc)
                enableVibration(false) // 禁用渠道自带震动，由 AlarmPlaybackService 控制
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // 不设置声音，避免和 AlarmPlaybackService 的音乐冲突
                setSound(null, null)
            }
            
            // 贪睡渠道
            val snoozeChannel = NotificationChannel(
                CHANNEL_ID_SNOOZE,
                context.getString(R.string.notification_channel_snooze_reminder),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_snooze_desc)
                enableVibration(true)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            // 睡眠定时器渠道
            val sleepTimerChannel = NotificationChannel(
                CHANNEL_ID_SLEEP_TIMER,
                context.getString(R.string.sleep_timer),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_sleep_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            // 错过闹钟提醒渠道
            val missedChannel = NotificationChannel(
                CHANNEL_ID_MISSED,
                context.getString(R.string.notification_channel_missed),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_missed_desc)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            // 响前提醒渠道（不打扰的默认重要性）
            val preAlarmChannel = NotificationChannel(
                CHANNEL_ID_PRE_ALARM,
                context.getString(R.string.notification_channel_pre_alarm),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_pre_alarm_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            notificationManager.createNotificationChannels(listOf(
                alarmChannel,
                snoozeChannel,
                sleepTimerChannel,
                missedChannel,
                preAlarmChannel
            ))
            
            Log.i(TAG, "通知渠道已创建")
        }
    }
    
    // 构建闹钟播放通知（含歌曲信息和震动按钮）
    fun buildAlarmNotification(
        alarmLabel: String,
        isPlaying: Boolean,
        songTitle: String? = null,
        songIndex: Int = 0,
        songTotal: Int = 0,
        isVibrating: Boolean = false,
        alarmId: Long = -1L,
        playlistName: String? = null,
        nextAlarmTime: String? = null
    ): Notification {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 停止按钮
        val stopIntent = Intent(context, AlarmPlaybackReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 贪睡按钮
        val snoozeIntent = Intent(context, AlarmPlaybackReceiver::class.java).apply {
            action = ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, 2, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 用户如果直接划掉通知，也应视为停止闹钟
        val deleteIntent = Intent(context, AlarmPlaybackReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context, 5, deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 构建通知内容
        val contentText = buildString {
            if (isPlaying) {
                if (!songTitle.isNullOrEmpty()) {
                    append("$songTitle")
                    if (songTotal > 1) append(" ($songIndex/$songTotal)")
                } else {
                    append(context.getString(R.string.playback_playing))
                }
            } else {
                append(context.getString(R.string.playback_snoozing))
            }
            playlistName?.let { append(" • $it") }
        }

        // 震动控制按钮
        val stopVibrateIntent = Intent(context, AlarmPlaybackReceiver::class.java).apply {
            action = "com.sleepytune.app.ACTION_STOP_VIBRATE"
        }
        val stopVibratePendingIntent = PendingIntent.getBroadcast(
            context, 3, stopVibrateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseVibrateIntent = Intent(context, AlarmPlaybackReceiver::class.java).apply {
            action = "com.sleepytune.app.ACTION_PAUSE_VIBRATE"
        }
        val pauseVibratePendingIntent = PendingIntent.getBroadcast(
            context, 4, pauseVibrateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setContentTitle(alarmLabel.ifEmpty { context.getString(R.string.alarm_title) })
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setDeleteIntent(deletePendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setSound(null)
            .setDefaults(0)
            .addAction(android.R.drawable.ic_media_pause, context.getString(R.string.alarm_stop), stopPendingIntent)
            .addAction(android.R.drawable.ic_media_next, context.getString(R.string.alarm_snooze_action), snoozePendingIntent)
            .apply {
                if (isVibrating) {
                    addAction(android.R.drawable.ic_lock_silent_mode, context.getString(R.string.alarm_stop_vibrate), stopVibratePendingIntent)
                    addAction(android.R.drawable.ic_media_pause, context.getString(R.string.alarm_pause_vibrate), pauseVibratePendingIntent)
                }
                nextAlarmTime?.let { setSubText(context.getString(R.string.notification_next_alarm, it)) }
            }
            .build()
    }
    
    // 构建睡眠定时器通知
    fun buildSleepTimerNotification(
        remainingMinutes: Int
    ): Notification {
        val contentIntent = Intent(context, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_ID_SLEEP_TIMER)
            .setContentTitle(context.getString(R.string.sleep_timer))
            .setContentText(context.getString(R.string.notification_sleep_remaining, remainingMinutes))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()
    }
    
    // 更新闹钟通知（含歌曲信息和震动状态）
    fun updateAlarmNotification(
        alarmLabel: String,
        isPlaying: Boolean,
        songTitle: String? = null,
        songIndex: Int = 0,
        songTotal: Int = 0,
        isVibrating: Boolean = false,
        playlistName: String? = null,
        nextAlarmTime: String? = null
    ) {
        val notification = buildAlarmNotification(
            alarmLabel = alarmLabel,
            isPlaying = isPlaying,
            songTitle = songTitle,
            songIndex = songIndex,
            songTotal = songTotal,
            isVibrating = isVibrating,
            playlistName = playlistName,
            nextAlarmTime = nextAlarmTime
        )
        notificationManager.notify(NOTIFICATION_ID_ALARM, notification)
    }
    
    // 取消闹钟通知
    fun cancelAlarmNotification() {
        notificationManager.cancel(NOTIFICATION_ID_ALARM)
        Log.i(TAG, "闹钟通知已取消")
    }
    
    // 取消睡眠定时器通知
    fun cancelSleepTimerNotification() {
        notificationManager.cancel(NOTIFICATION_ID_SLEEP_TIMER)
    }

    // 显示贪睡通知（显示剩余时间）
    fun showSnoozeNotification(alarmLabel: String, snoozeMinutes: Int) {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 取消贪睡按钮
        val cancelIntent = Intent(context, AlarmPlaybackReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context, 1, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SNOOZE)
            .setContentTitle(alarmLabel.ifEmpty { context.getString(R.string.alarm_title) })
            .setContentText(context.getString(R.string.notification_snooze_will_ring, snoozeMinutes))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, context.getString(R.string.notification_snooze_cancel), cancelPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SNOOZE, notification)
        Log.i(TAG, "贪睡通知已显示: ${snoozeMinutes}分钟后响铃")
    }

    // 取消贪睡通知
    fun cancelSnoozeNotification() {
        notificationManager.cancel(NOTIFICATION_ID_SNOOZE)
        Log.i(TAG, "贪睡通知已取消")
    }

    // 显示"错过的闹钟"通知（闹钟到点没响，通常是强停/厂商省电策略导致）
    fun showMissedAlarmNotification(missedTimeMillis: Long) {
        val timeText = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(missedTimeMillis))
        val contentIntent = PendingIntent.getActivity(
            context, 20, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MISSED)
            .setContentTitle(context.getString(R.string.notification_channel_missed))
            .setContentText(context.getString(R.string.notification_missed_alarm_text, timeText))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_MISSED, notification)
        Log.w(TAG, "已提醒错过的闹钟: $timeText")
    }

    // 响前 5 分钟提醒：闹钟即将响起，降低"到点突然响"的惊吓
    fun showPreAlarmNotification(alarmLabel: String, fireTimeMillis: Long) {
        val timeText = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(fireTimeMillis))
        val contentIntent = PendingIntent.getActivity(
            context, 21, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (alarmLabel.isBlank()) {
            context.getString(com.sleepytune.app.R.string.alarm_pre_ring_text, timeText)
        } else {
            context.getString(com.sleepytune.app.R.string.alarm_pre_ring_text_label, alarmLabel, timeText)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_PRE_ALARM)
            .setContentTitle(context.getString(com.sleepytune.app.R.string.alarm_pre_ring_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_PRE_ALARM, notification)
        Log.i(TAG, "已发送响前提醒: $timeText")
    }

    // 取消响前提醒（正式响铃时清理）
    fun cancelPreAlarmNotification() {
        notificationManager.cancel(NOTIFICATION_ID_PRE_ALARM)
    }
}
