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
        HOUR_12
    }

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _timeFormat = MutableStateFlow(loadSavedTimeFormat())
    val timeFormat: StateFlow<TimeFormat> = _timeFormat.asStateFlow()

    // 加载保存的时间制设置
    private fun loadSavedTimeFormat(): TimeFormat {
        val saved = prefs.getString(KEY_TIME_FORMAT, TimeFormat.HOUR_24.name) ?: TimeFormat.HOUR_24.name
        return try {
            TimeFormat.valueOf(saved)
        } catch (_: IllegalArgumentException) {
            TimeFormat.HOUR_24
        }
    }

    // 切换时间制
    fun setTimeFormat(format: TimeFormat) {
        _timeFormat.value = format
        prefs.edit().putString(KEY_TIME_FORMAT, format.name).apply()
    }

    // 切换时间制
    fun toggleTimeFormat() {
        val newFormat = if (_timeFormat.value == TimeFormat.HOUR_24) {
            TimeFormat.HOUR_12
        } else {
            TimeFormat.HOUR_24
        }
        setTimeFormat(newFormat)
    }

    companion object {
        private const val PREFS_NAME = "time_format_settings"
        private const val KEY_TIME_FORMAT = "time_format"
    }
}
