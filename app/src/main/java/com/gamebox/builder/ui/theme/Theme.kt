package com.gamebox.builder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GameBoxDarkColors = darkColorScheme(
    primary = Color(0xFF8E6BFF),
    secondary = Color(0xFF00E5FF),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF090A12),
    surface = Color(0xFF111320),
    surfaceVariant = Color(0xFF1A1D2E),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFEDEBFF),
    onSurface = Color(0xFFEDEBFF),
    onSurfaceVariant = Color(0xFFC9C4E8)
)

@Composable
fun GameBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GameBoxDarkColors,
        typography = Typography(),
        content = content
    )
}
