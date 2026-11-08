package com.sadikbr.videowallpaper

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sadikbr.videowallpaper.ui.SettingsScreen
import com.sadikbr.videowallpaper.ui.theme.WallpaperSlideshowTheme

/** Settings screen: choose photos and videos and how the slideshow plays, then set the wallpaper. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark app: light status and navigation bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            WallpaperSlideshowTheme {
                SettingsScreen()
            }
        }
    }
}
