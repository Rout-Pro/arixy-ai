package com.rout.arixy.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/** ARIXY black minimal liquid-glass palette. */
object ArixyColors {
    val Black = Color(0xFF000000)
    val NearBlack = Color(0xFF080808)
    val Glass = Color(0x14FFFFFF)          // subtle translucent glass surface
    val GlassStrong = Color(0x1FFFFFFF)
    val Border = Color(0x22FFFFFF)         // thin low-contrast grey border
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9A9AA3)  // muted grey
    val Accent = Color(0xFFE8E8F0)         // restrained silver-white
    val AccentViolet = Color(0xFF8B7CFF)   // subtle violet highlight
    val AccentBlue = Color(0xFF6EA8FF)     // subtle electric-blue highlight
    val Success = Color(0xFF7BC796)
    val Error = Color(0xFFD98A8A)
    val Warning = Color(0xFFC9A96A)
}

val ArixyColorScheme = darkColorScheme(
    primary = ArixyColors.Accent,
    onPrimary = ArixyColors.Black,
    background = ArixyColors.Black,
    onBackground = ArixyColors.TextPrimary,
    surface = ArixyColors.NearBlack,
    onSurface = ArixyColors.TextPrimary,
    surfaceVariant = ArixyColors.Glass,
    onSurfaceVariant = ArixyColors.TextSecondary,
    outline = ArixyColors.Border
)
