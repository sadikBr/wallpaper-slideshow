package com.sadikbr.videowallpaper.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sadikbr.videowallpaper.data.ScaleMode
import com.sadikbr.videowallpaper.data.TransitionStyle
import com.sadikbr.videowallpaper.data.WallpaperSettings
import com.sadikbr.videowallpaper.ui.components.PlaybackCard

/** The slideshow's playback settings (order, timing, transitions, sound), in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideshowSheet(
    settings: WallpaperSettings,
    onDismiss: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onMutedChange: (Boolean) -> Unit,
    onClipSecondsChange: (Int) -> Unit,
    onImageSecondsChange: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onScaleModeChange: (ScaleMode) -> Unit,
    onTransitionChange: (TransitionStyle) -> Unit,
    onTransitionMsChange: (Int) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF15122C),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                "Slideshow",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            )
            PlaybackCard(
                settings = settings,
                onShuffleChange = onShuffleChange,
                onMutedChange = onMutedChange,
                onClipSecondsChange = onClipSecondsChange,
                onImageSecondsChange = onImageSecondsChange,
                onSpeedChange = onSpeedChange,
                onScaleModeChange = onScaleModeChange,
                onTransitionChange = onTransitionChange,
                onTransitionMsChange = onTransitionMsChange,
            )
        }
    }
}
