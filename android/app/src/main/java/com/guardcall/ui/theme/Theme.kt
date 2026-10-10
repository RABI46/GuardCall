package com.guardcall.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E5B7A),
    secondary = Color(0xFF4A90A4),
    tertiary = Color(0xFF6BB4C8),
    background = Color(0xFFF8FAFB),
    surface = Color.White
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6BB4C8),
    secondary = Color(0xFF4A90A4),
    tertiary = Color(0xFF1E5B7A),
    background = Color(0xFF0F1F28),
    surface = Color(0xFF1A2E3B)
)

@Composable
fun GuardCallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
