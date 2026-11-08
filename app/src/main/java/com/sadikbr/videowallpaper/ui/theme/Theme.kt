package com.sadikbr.videowallpaper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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
    // A wallpaper app is about the pictures: always the dark, immersive look.
    MaterialTheme(colorScheme = DarkColors, content = content)
}
