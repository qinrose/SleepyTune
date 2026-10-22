package com.sleepytune.app.ui.viewmodel

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
class SettingsViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _lockScreenPopupEnabled = MutableStateFlow(loadLockScreenPopupEnabled())
    val lockScreenPopupEnabled: StateFlow<Boolean> = _lockScreenPopupEnabled.asStateFlow()

    private fun loadLockScreenPopupEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCK_SCREEN_POPUP, true)
    }

    fun setLockScreenPopupEnabled(enabled: Boolean) {
        _lockScreenPopupEnabled.value = enabled
        prefs.edit().putBoolean(KEY_LOCK_SCREEN_POPUP, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_LOCK_SCREEN_POPUP = "lock_screen_popup_enabled"

        fun isLockScreenPopupEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_LOCK_SCREEN_POPUP, true)
        }
    }
}
