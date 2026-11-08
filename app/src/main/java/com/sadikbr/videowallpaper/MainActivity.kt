package com.sadikbr.videowallpaper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sadikbr.videowallpaper.ui.SettingsScreen
import com.sadikbr.videowallpaper.ui.theme.VideoSlideshowTheme

/** Settings screen: choose photos and videos and how the slideshow plays, then set the wallpaper. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoSlideshowTheme {
                SettingsScreen()
            }
        }
    }
}
