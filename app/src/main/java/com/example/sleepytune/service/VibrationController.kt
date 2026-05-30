package com.example.sleepytune.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// 震动控制器：独立管理震动启停、延迟、暂停
@Singleton
class VibrationController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VibrationController"
    }

    private var vibrator: Vibrator? = null
    private var isVibrating = false
    private var vibrationPaused = false
    private var shouldVibrate = true
    private var delayJob: Job? = null
    private var pauseJob: Job? = null
    private var snoozeMinutes: Int = 5

    // 震动状态回调
    var onStateChanged: ((isVibrating: Boolean, isPaused: Boolean) -> Unit)? = null

    init {
        initVibrator()
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // 配置震动参数
    fun configure(shouldVibrate: Boolean, delayMinutes: Int = 0, snoozeMinutes: Int = 5) {
        this.shouldVibrate = shouldVibrate
        this.snoozeMinutes = snoozeMinutes
        vibrationPaused = false
        cancelAllJobs()

        if (shouldVibrate && delayMinutes > 0) {
            Log.i(TAG, "震动将在 ${delayMinutes} 分钟后开始")
            delayJob = CoroutineScope(Dispatchers.Main).launch {
                delay(delayMinutes * 60 * 1000L)
                if (!vibrationPaused) {
                    startVibration()
                    notifyState()
                }
            }
        }
    }

    // 立即开始震动
    fun start() {
        if (!shouldVibrate) return
        startVibration()
        notifyState()
    }

    private fun startVibration() {
        try {
            isVibrating = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(500, 500), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(500, 500), 0)
            }
            Log.i(TAG, "震动已启动")
        } catch (e: SecurityException) {
            Log.w(TAG, "无振动权限: ${e.message}")
        }
    }

    // 停止震动（永久）
    fun stop() {
        cancelAllJobs()
        stopVibration()
        shouldVibrate = false
        vibrationPaused = false
        notifyState()
        Log.i(TAG, "震动已停止")
    }

    // 暂停震动（稍后恢复）
    fun pause() {
        stopVibration()
        vibrationPaused = true
        pauseJob = CoroutineScope(Dispatchers.Main).launch {
            delay(snoozeMinutes * 60 * 1000L)
            if (vibrationPaused) {
                vibrationPaused = false
                startVibration()
                notifyState()
                Log.i(TAG, "稍后再震时间到，震动已恢复")
            }
        }
        notifyState()
        Log.i(TAG, "震动已暂停，${snoozeMinutes}分钟后恢复")
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
            isVibrating = false
        } catch (e: Exception) {
            Log.w(TAG, "停止振动失败: ${e.message}")
        }
    }

    // 释放资源
    fun release() {
        cancelAllJobs()
        stopVibration()
        shouldVibrate = true
        vibrationPaused = false
    }

    private fun cancelAllJobs() {
        delayJob?.cancel()
        delayJob = null
        pauseJob?.cancel()
        pauseJob = null
    }

    private fun notifyState() {
        onStateChanged?.invoke(isVibrating, vibrationPaused)
    }

    fun isVibrating(): Boolean = isVibrating
    fun isPaused(): Boolean = vibrationPaused
    fun shouldVibrate(): Boolean = shouldVibrate
}