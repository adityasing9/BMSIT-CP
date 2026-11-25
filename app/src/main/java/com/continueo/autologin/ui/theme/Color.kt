package com.continueo.autologin.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Default Blue
val PrimaryBlue = Color(0xFF1A3A6B)
val SecondaryBlue = Color(0xFF4A7AB7)
val TertiaryBlue = Color(0xFF8BA9D1)
val DarkPrimaryBlue = Color(0xFF82A6D9)
val DarkSecondaryBlue = Color(0xFF5A8BCF)
val DarkTertiaryBlue = Color(0xFF336399)

// Emerald Green
val PrimaryEmerald = Color(0xFF059669)
val SecondaryEmerald = Color(0xFF10B981)
val TertiaryEmerald = Color(0xFF34D399)
val DarkPrimaryEmerald = Color(0xFF34D399)
val DarkSecondaryEmerald = Color(0xFF10B981)
val DarkTertiaryEmerald = Color(0xFF059669)

// Royal Purple
val PrimaryPurple = Color(0xFF6D28D9)
val SecondaryPurple = Color(0xFF8B5CF6)
val TertiaryPurple = Color(0xFFA78BFA)
val DarkPrimaryPurple = Color(0xFFA78BFA)
val DarkSecondaryPurple = Color(0xFF8B5CF6)
val DarkTertiaryPurple = Color(0xFF6D28D9)

// Crimson Red
val PrimaryCrimson = Color(0xFFB91C1C)
val SecondaryCrimson = Color(0xFFDC2626)
val TertiaryCrimson = Color(0xFFEF4444)
val DarkPrimaryCrimson = Color(0xFFF87171)
val DarkSecondaryCrimson = Color(0xFFEF4444)
val DarkTertiaryCrimson = Color(0xFFB91C1C)

// Sunset Amber
val PrimaryAmber = Color(0xFFD97706)
val SecondaryAmber = Color(0xFFF59E0B)
val TertiaryAmber = Color(0xFFFBBF24)
val DarkPrimaryAmber = Color(0xFFFBBF24)
val DarkSecondaryAmber = Color(0xFFF59E0B)
val DarkTertiaryAmber = Color(0xFFD97706)

fun getAppColorScheme(accent: ColorAccent, isDark: Boolean, isAmoled: Boolean): ColorScheme {
    val bg = if (isDark) {
        if (isAmoled) Color.Black else Color(0xFF14171F)
    } else {
        Color(0xFFFFFBFE)
    }
    val surface = if (isDark) {
        if (isAmoled) Color(0xFF0D0E12) else Color(0xFF1E222B)
    } else {
        Color(0xFFFFFBFE)
    }
    val onBg = if (isDark) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
    val onSurface = if (isDark) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)

    return if (isDark) {
        val (pri, sec, ter) = when (accent) {
            ColorAccent.BLUE -> Triple(DarkPrimaryBlue, DarkSecondaryBlue, DarkTertiaryBlue)
            ColorAccent.EMERALD -> Triple(DarkPrimaryEmerald, DarkSecondaryEmerald, DarkTertiaryEmerald)
            ColorAccent.PURPLE -> Triple(DarkPrimaryPurple, DarkSecondaryPurple, DarkTertiaryPurple)
            ColorAccent.CRIMSON -> Triple(DarkPrimaryCrimson, DarkSecondaryCrimson, DarkTertiaryCrimson)
            ColorAccent.AMBER -> Triple(DarkPrimaryAmber, DarkSecondaryAmber, DarkTertiaryAmber)
        }
        darkColorScheme(
            primary = pri,
            secondary = sec,
            tertiary = ter,
            background = bg,
            surface = surface,
            onPrimary = Color(0xFF00193F),
            onSecondary = Color(0xFF00193F),
            onTertiary = Color(0xFF00193F),
            onBackground = onBg,
            onSurface = onSurface
        )
    } else {
        val (pri, sec, ter) = when (accent) {
            ColorAccent.BLUE -> Triple(PrimaryBlue, SecondaryBlue, TertiaryBlue)
            ColorAccent.EMERALD -> Triple(PrimaryEmerald, SecondaryEmerald, TertiaryEmerald)
            ColorAccent.PURPLE -> Triple(PrimaryPurple, SecondaryPurple, TertiaryPurple)
            ColorAccent.CRIMSON -> Triple(PrimaryCrimson, SecondaryCrimson, TertiaryCrimson)
            ColorAccent.AMBER -> Triple(PrimaryAmber, SecondaryAmber, TertiaryAmber)
        }
        lightColorScheme(
            primary = pri,
            secondary = sec,
            tertiary = ter,
            background = bg,
            surface = surface,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.White,
            onBackground = onBg,
            onSurface = onSurface
        )
    }
}
