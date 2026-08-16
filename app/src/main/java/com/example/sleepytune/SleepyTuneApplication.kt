package com.example.sleepytune

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.sleepytune.ui.viewmodel.AppLanguage
import com.example.sleepytune.utils.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SleepyTuneApplication : Application() {

    companion object {
        private const val TAG = "SleepyTuneApp"
        @Volatile private var instance: SleepyTuneApplication? = null

        fun getInstance(): SleepyTuneApplication? = instance

        // 主题切换方法 - 供全局调用
        fun applyTheme(mode: ThemeMode) {
            instance?.let { app ->
                val nightMode = when (mode) {
                    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    ThemeMode.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                // 保存设置
                app.getSharedPreferences("theme_settings", MODE_PRIVATE)
                    .edit()
                    .putString("theme_mode", mode.name)
                    .apply()
                // 应用主题 - 这会立即生效并触发生命周期重绘
                AppCompatDelegate.setDefaultNightMode(nightMode)
                Log.i(TAG, "主题已切换: $mode")
            }
        }
    }

    enum class ThemeMode {
        LIGHT, DARK, AUTO
    }

    @Inject lateinit var alarmScheduler: com.example.sleepytune.service.AlarmScheduler
    @Inject lateinit var alarmRepository: com.example.sleepytune.data.repository.AlarmRepository
    @Inject lateinit var alarmRescheduler: com.example.sleepytune.service.AlarmRescheduler
    @Inject lateinit var crashReporter: CrashReporter

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化主题设置（必须在UI之前）
        initTheme()

        // 初始化语言设置（必须在UI之前）
        initLanguage()

        // 初始化崩溃报告器
        try {
            crashReporter.init()
        } catch (e: Exception) {
            Log.e(TAG, "崩溃报告器初始化失败: ${e.message}")
        }

        // 每次启动都重新核对闹钟调度，避免因重启/强停/厂商省电策略导致漏响
        rescheduleAlarmsAfterBoot()

        Log.i(TAG, "SleepyTuneApplication onCreate 完成, 进程=${android.os.Process.myPid()}")
    }

    // 初始化应用主题 - 大师级实现
    private fun initTheme() {
        val prefs = getSharedPreferences("theme_settings", MODE_PRIVATE)
        val savedMode = prefs.getString("theme_mode", ThemeMode.AUTO.name)
        val nightMode = when (savedMode) {
            ThemeMode.LIGHT.name -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK.name -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
        Log.i(TAG, "主题初始化完成: mode=$savedMode, nightMode=$nightMode")
    }

    // 初始化应用语言
    private fun initLanguage() {
        val prefs = getSharedPreferences("language_settings", MODE_PRIVATE)
        val savedCode = prefs.getString("app_language", AppLanguage.SYSTEM.code)
        val language = AppLanguage.fromCode(savedCode ?: AppLanguage.SYSTEM.code)

        val appLocale = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.CHINESE -> LocaleListCompat.forLanguageTags("zh")
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
        Log.i(TAG, "语言初始化完成: ${language.code}")
    }

    // 供AlarmReceiver调用的恢复闹钟方法
    fun rescheduleAlarmsAfterBoot() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                alarmRescheduler.rescheduleAllAlarms()
                Log.i(TAG, "✅ 闹钟恢复完成")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 闹钟恢复失败: ${e.message}", e)
            }
        }
    }
}
