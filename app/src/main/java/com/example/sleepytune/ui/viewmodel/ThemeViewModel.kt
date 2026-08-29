package com.example.sleepytune.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleepytune.SleepyTuneApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    enum class ThemeMode {
        LIGHT,
        DARK,
        AUTO
    }

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadSavedTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(loadSavedDynamicColor())
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    // 加载保存的主题设置
    private fun loadSavedTheme(): ThemeMode {
        val saved = prefs.getString(KEY_THEME, ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name
        return try {
            ThemeMode.valueOf(saved)
        } catch (_: IllegalArgumentException) {
            ThemeMode.AUTO
        }
    }

    // 动态取色开关（Android 12+ 跟随壁纸；默认关闭，优先展示应用内置的"翡翠深夜"配色）
    private fun loadSavedDynamicColor(): Boolean {
        return prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    // 大师级主题切换 - 立即生效
    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        
        // 调用 Application 级别的主题切换 - 这会立即生效
        val appMode = when (mode) {
            ThemeMode.LIGHT -> SleepyTuneApplication.ThemeMode.LIGHT
            ThemeMode.DARK -> SleepyTuneApplication.ThemeMode.DARK
            ThemeMode.AUTO -> SleepyTuneApplication.ThemeMode.AUTO
        }
        SleepyTuneApplication.applyTheme(appMode)
    }

    companion object {
        private const val PREFS_NAME = "theme_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}
