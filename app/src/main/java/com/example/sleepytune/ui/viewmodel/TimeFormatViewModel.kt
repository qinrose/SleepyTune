package com.example.sleepytune.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class TimeFormatViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    enum class TimeFormat {
        HOUR_24,
        HOUR_12,
        AUTO   // 跟随系统
    }

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _timeFormat = MutableStateFlow(loadSavedTimeFormat())
    val timeFormat: StateFlow<TimeFormat> = _timeFormat.asStateFlow()

    // 加载保存的时间制设置（默认跟随系统）
    private fun loadSavedTimeFormat(): TimeFormat {
        val saved = prefs.getString(KEY_TIME_FORMAT, TimeFormat.AUTO.name) ?: TimeFormat.AUTO.name
        return try {
            TimeFormat.valueOf(saved)
        } catch (_: IllegalArgumentException) {
            TimeFormat.AUTO
        }
    }

    // 设置时间制
    fun setTimeFormat(format: TimeFormat) {
        _timeFormat.value = format
        prefs.edit().putString(KEY_TIME_FORMAT, format.name).apply()
    }

    // 是否使用 24 小时制：AUTO 时跟随系统设置
    fun is24Hour(): Boolean {
        return when (_timeFormat.value) {
            TimeFormat.HOUR_24 -> true
            TimeFormat.HOUR_12 -> false
            TimeFormat.AUTO -> android.text.format.DateFormat.is24HourFormat(getApplication())
        }
    }

    companion object {
        private const val PREFS_NAME = "time_format_settings"
        private const val KEY_TIME_FORMAT = "time_format"
    }
}
