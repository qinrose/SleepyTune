package com.example.sleepytune.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject

enum class AppLanguage(val code: String, val nativeName: String, val englishName: String, val chineseName: String) {
    SYSTEM("system", "Follow System", "Follow System", "跟随系统"),
    ENGLISH("en", "English", "English", "英语"),
    CHINESE("zh", "中文", "Chinese", "中文");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.find { it.code == code } ?: SYSTEM
    }
}

@HiltViewModel
class LanguageViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentLanguage = MutableStateFlow(loadSavedLanguage())
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    private fun loadSavedLanguage(): AppLanguage {
        val savedCode = prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.code)
        return AppLanguage.fromCode(savedCode ?: AppLanguage.SYSTEM.code)
    }

    fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
        prefs.edit().putString(KEY_LANGUAGE, language.code).apply()
        applyLanguage(language)
    }

    private fun applyLanguage(language: AppLanguage) {
        val appLocale = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.CHINESE -> LocaleListCompat.forLanguageTags("zh")
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun getLanguageDisplayName(language: AppLanguage): String {
        val currentLocale = getCurrentLocale()
        return if (currentLocale.language.startsWith("zh")) {
            language.chineseName
        } else {
            language.englishName
        }
    }

    private fun getCurrentLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            getApplication<Application>().resources.configuration.locale
        }
    }

    companion object {
        private const val PREFS_NAME = "language_settings"
        private const val KEY_LANGUAGE = "app_language"
    }
}
