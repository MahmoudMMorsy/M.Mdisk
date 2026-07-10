package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun MyApplicationTheme(
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val isDark = LocalThemeIsDark.current
  val colorScheme = if (isDark) {
    darkColorScheme(
      primary = Color(0xFF00E5FF),
      secondary = Color(0xFF1E293B),
      tertiary = Color(0xFF00FF87),
      background = Color(0xFF0A0C10),
      surface = Color(0xFF121620),
      onBackground = Color(0xFFE2E8F0),
      onSurface = Color(0xFFE2E8F0),
      outline = Color(0xFF222B3C)
    )
  } else {
    lightColorScheme(
      primary = Color(0xFF0284C7),
      secondary = Color(0xFFF1F5F9),
      tertiary = Color(0xFF16A34A),
      background = Color(0xFFF1F5F9),
      surface = Color(0xFFFFFFFF),
      onBackground = Color(0xFF0F172A),
      onSurface = Color(0xFF0F172A),
      outline = Color(0xFFCBD5E1)
    )
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
