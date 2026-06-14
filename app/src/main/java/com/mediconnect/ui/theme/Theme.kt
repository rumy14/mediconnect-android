package com.mediconnect.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlue.copy(alpha = 0.12f),
    secondary = PrimaryPurple,
    tertiary = PrimaryCyan,
    background = LightBg,
    surface = LightSurface,
    surfaceContainerLow = Color(0xFFF1F5F9),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFCBD5E1),
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryBlue.copy(alpha = 0.15f),
    secondary = PrimaryPurple,
    tertiary = PrimaryCyan,
    background = DarkBg,
    surface = DarkBg2,
    surfaceContainerLow = Color(0xFF12122E),
    onBackground = TextWhite,
    onSurface = TextWhite,
    onSurfaceVariant = TextMuted,
    outline = Color(0xFF1E293B),
)

@Composable
fun MediConnectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
