package com.sadikbr.videowallpaper.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A5BC4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    secondaryContainer = Color(0xFFE0E0F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF2E7D5B),
    onTertiary = Color.White,
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceVariant = Color(0xFFE3E1EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBCC2FF),
    onPrimary = Color(0xFF1A2A94),
    primaryContainer = Color(0xFF3241AB),
    onPrimaryContainer = Color(0xFFDFE0FF),
    secondary = Color(0xFFC4C4DD),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFE0E0F9),
    tertiary = Color(0xFF8BD6B0),
    onTertiary = Color(0xFF003824),
    background = Color(0xFF121318),
    surface = Color(0xFF121318),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceVariant = Color(0xFF46464F),
)

@Composable
fun VideoSlideshowTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        // Android 12+ matches the app colors to the user's wallpaper.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
