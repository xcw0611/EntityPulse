package com.entitypulse.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VipGold = Color(0xFFF59E0B)
val BrandBlue = Color(0xFF3B82F6)
val BrandCyan = Color(0xFF06B6D4)
val DarkBackground = Color(0xFF131418)
val DarkSurface = Color(0xFF1A1C23)
val DarkSurfaceElevated = Color(0xFF22252E)
val DarkBorder = Color(0xFF2E323E)

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = BrandCyan,
    onSecondary = Color.Black,
    surface = DarkSurface,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = Color(0xFF94A3B8),
    background = DarkBackground,
    onBackground = Color(0xFFF8FAFC),
    outline = DarkBorder
)

@Composable
fun EntityPulseDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
