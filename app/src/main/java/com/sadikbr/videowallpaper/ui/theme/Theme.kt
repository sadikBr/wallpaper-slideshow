package com.sadikbr.videowallpaper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Wallpaper Slideshow brand colours (they match the app icon). */
object Brand {
    val Indigo = Color(0xFF3B2DB5)
    val Violet = Color(0xFF7B3FE4)
    val Coral = Color(0xFFFF7A66)
    val Ink = Color(0xFF17123A)

    /** The signature diagonal gradient, as on the app icon. */
    val gradient = Brush.linearGradient(listOf(Indigo, Violet, Coral))
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3FD9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1C0A64),
    secondary = Color(0xFF625B78),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E0FA),
    onSecondaryContainer = Color(0xFF1F1832),
    tertiary = Color(0xFFB23F33),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDAD4),
    onTertiaryContainer = Color(0xFF410001),
    background = Color(0xFFFDF8FF),
    onBackground = Color(0xFF1C1A22),
    surface = Color(0xFFFDF8FF),
    onSurface = Color(0xFF1C1A22),
    surfaceVariant = Color(0xFFE6E0EE),
    onSurfaceVariant = Color(0xFF48454F),
    surfaceContainer = Color(0xFFF2ECF7),
    outlineVariant = Color(0xFFCAC4D2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCBBEFF),
    onPrimary = Color(0xFF2E1694),
    primaryContainer = Color(0xFF4630BF),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFCCC2E4),
    onSecondary = Color(0xFF332D48),
    secondaryContainer = Color(0xFF4A4360),
    onSecondaryContainer = Color(0xFFE8E0FA),
    tertiary = Color(0xFFFFB4A8),
    onTertiary = Color(0xFF690004),
    tertiaryContainer = Color(0xFF8F2A1F),
    onTertiaryContainer = Color(0xFFFFDAD4),
    background = Color(0xFF131022),
    onBackground = Color(0xFFE6E0EE),
    surface = Color(0xFF131022),
    onSurface = Color(0xFFE6E0EE),
    surfaceVariant = Color(0xFF48454F),
    onSurfaceVariant = Color(0xFFCAC4D2),
    surfaceContainer = Color(0xFF1F1B30),
    outlineVariant = Color(0xFF48454F),
)

@Composable
fun WallpaperSlideshowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
