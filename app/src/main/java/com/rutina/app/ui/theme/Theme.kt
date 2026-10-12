package com.rutina.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF9FA8DA)

private val Dark = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF111111),
    background = Color(0xFF151515),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB8B8B8),
    surfaceContainer = Color(0xFF222222),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color(0xFFEDEDED),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFE57373),
)

private val Light = lightColorScheme(
    primary = Color(0xFF5C6BC0),
    background = Color(0xFFF4F4F4),
    surface = Color.White,
    surfaceVariant = Color(0xFFE6E6E6),
    secondaryContainer = Color(0xFFE2E2E2),
)

@Composable
fun RutinaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
