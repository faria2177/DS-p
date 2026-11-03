package com.docscan.util

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    LIGHT,
    DARK,
    SYSTEM_DEFAULT;

    companion object {
        fun fromString(value: String?): AppThemeMode {
            return when (value?.uppercase()) {
                "LIGHT" -> LIGHT
                "DARK" -> DARK
                "SYSTEM_DEFAULT", "SYSTEM" -> SYSTEM_DEFAULT
                else -> LIGHT
            }
        }
    }
}

object ThemeManager {
    private const val PREFS_NAME = "app_settings_prefs"
    private const val KEY_THEME_MODE = "app_theme_mode"

    // A snapshot state is necessary for custom colors used inside Canvas and callbacks.
    // The effective value is resolved by the Activity, including System Default mode.
    private val effectiveDarkState = mutableStateOf(false)
    val effectiveIsDark: Boolean get() = effectiveDarkState.value

    fun updateEffectiveDarkTheme(isDark: Boolean) {
        if (effectiveDarkState.value != isDark) effectiveDarkState.value = isDark
    }

    fun themeColor(dark: Color, light: Color): Color =
        if (effectiveDarkState.value) dark else light

    private val _themeMode = MutableStateFlow(AppThemeMode.LIGHT)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME_MODE, AppThemeMode.LIGHT.name)
        _themeMode.value = AppThemeMode.fromString(saved)
        // Consistent initial value until system preference has been resolved by the Activity.
        effectiveDarkState.value = _themeMode.value == AppThemeMode.DARK
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
        if (mode != AppThemeMode.SYSTEM_DEFAULT) updateEffectiveDarkTheme(mode == AppThemeMode.DARK)
    }

    @Composable
    fun isDarkThemeActive(mode: AppThemeMode): Boolean {
        return when (mode) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            AppThemeMode.SYSTEM_DEFAULT -> isSystemInDarkTheme()
        }
    }
}
