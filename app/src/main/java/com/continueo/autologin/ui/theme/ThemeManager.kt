package com.continueo.autologin.ui.theme

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val title: String) {
    SYSTEM("Follow System"),
    LIGHT("Light Theme"),
    DARK("Dark Theme")
}

enum class ColorAccent(val title: String, val primaryColor: Long, val darkPrimaryColor: Long) {
    BLUE("Classic Blue", 0xFF1A3A6BL, 0xFF82A6D9L),
    EMERALD("Emerald Green", 0xFF059669L, 0xFF34D399L),
    PURPLE("Royal Purple", 0xFF6D28D9L, 0xFFA78BFAL),
    CRIMSON("Crimson Red", 0xFFB91C1CL, 0xFFF87171L),
    AMBER("Sunset Amber", 0xFFD97706L, 0xFFFBBF24L)
}

data class ThemeSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    val colorAccent: ColorAccent = ColorAccent.BLUE,
    val amoledDark: Boolean = false
)

class ThemeManager(context: Context) {
    private val prefs = context.getSharedPreferences("continueo_theme_prefs", Context.MODE_PRIVATE)

    private val _themeSettings = MutableStateFlow(loadSettings())
    val themeSettings: StateFlow<ThemeSettings> = _themeSettings.asStateFlow()

    private fun loadSettings(): ThemeSettings {
        val modeName = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val mode = try { AppThemeMode.valueOf(modeName) } catch (_: Exception) { AppThemeMode.SYSTEM }

        val dynamicDefault = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val dynamic = prefs.getBoolean("dynamic_color", dynamicDefault)

        val accentName = prefs.getString("color_accent", ColorAccent.BLUE.name) ?: ColorAccent.BLUE.name
        val accent = try { ColorAccent.valueOf(accentName) } catch (_: Exception) { ColorAccent.BLUE }

        val amoled = prefs.getBoolean("amoled_dark", false)

        return ThemeSettings(
            themeMode = mode,
            dynamicColor = dynamic,
            colorAccent = accent,
            amoledDark = amoled
        )
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeSettings.value = _themeSettings.value.copy(themeMode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
        _themeSettings.value = _themeSettings.value.copy(dynamicColor = enabled)
    }

    fun setColorAccent(accent: ColorAccent) {
        prefs.edit()
            .putString("color_accent", accent.name)
            .putBoolean("dynamic_color", false)
            .apply()
        _themeSettings.value = _themeSettings.value.copy(colorAccent = accent, dynamicColor = false)
    }

    fun setAmoledDark(enabled: Boolean) {
        prefs.edit().putBoolean("amoled_dark", enabled).apply()
        _themeSettings.value = _themeSettings.value.copy(amoledDark = enabled)
    }
}
