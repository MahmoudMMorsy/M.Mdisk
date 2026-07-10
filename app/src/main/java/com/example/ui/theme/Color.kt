package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

val LocalThemeIsDark = compositionLocalOf { true }

val Purple80: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFF00E5FF) else Color(0xFF0284C7) // Main cyan accent
val PurpleGrey80: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFF1E293B) else Color(0xFFF1F5F9)
val Pink80: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFF00FF87) else Color(0xFF16A34A) // Secondary green

val Purple40 = Color(0xFF00B0FF)
val PurpleGrey40 = Color(0xFF334155)
val Pink40 = Color(0xFF00E676)

// Cyberpunk / Terminal Color Palette
val SlateDark: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFF0A0C10) else Color(0xFFF1F5F9)
val SlateCard: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFF121620) else Color(0xFFFFFFFF)
val SlateBorder: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFF222B3C) else Color(0xFFCBD5E1)
val TerminalText: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFFE2E8F0) else Color(0xFF0F172A)

val NeonGreen: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFF00FF87) else Color(0xFF16A34A)  // Good block / healthy
val NeonLightGreen: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFFB9F6CA) else Color(0xFF4ADE80) // Fast block
val NeonAmber: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFFFF9F00) else Color(0xFFD97706)  // Slow block / warning
val NeonRed: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFFFF3366) else Color(0xFFDC2626)    // Critical / bad sector
val NeonBlue: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFF00E5FF) else Color(0xFF0284C7)   // Information / healing pulse
val DarkGreyBlock: Color @Composable get() = if (LocalThemeIsDark.current) Color(0xFF1E293B) else Color(0xFFE2E8F0) // Standard block
