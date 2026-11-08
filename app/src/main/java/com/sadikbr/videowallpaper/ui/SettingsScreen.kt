package com.sadikbr.videowallpaper.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sadikbr.videowallpaper.ui.components.EmptyVideosCard
import com.sadikbr.videowallpaper.ui.components.HeroCard
import com.sadikbr.videowallpaper.ui.components.PlaybackCard
import com.sadikbr.videowallpaper.ui.components.SectionHeader
import com.sadikbr.videowallpaper.ui.components.VideoTile
import com.sadikbr.videowallpaper.wallpaper.VideoWallpaperService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Re-check on every return to the app, e.g. after the system "Set wallpaper" screen.
    var wallpaperActive by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        wallpaperActive = isOurWallpaperActive(context)
        onPauseOrDispose { }
    }

    val pickVideos = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.addVideos(uris) }
    val addVideos = { pickVideos.launch(arrayOf("video/*")) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Video Slideshow") },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = addVideos,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add videos") },
            )
        },
    ) { padding ->
        val current = settings
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp, // room for the button
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            fullWidth("hero") {
                HeroCard(
                    settings = current,
                    loader = viewModel.videoInfoLoader,
                    wallpaperActive = wallpaperActive,
                    onApply = { openWallpaperPicker(context) },
                )
            }

            fullWidth("videos-header") {
                SectionHeader(
                    title = "Your videos",
                    trailing = if (current.videos.isEmpty()) null else "${current.videos.size}",
                    hint = if (current.videos.size > 1) "Tap a video to reorder or remove it" else null,
                )
            }

            if (current.videos.isEmpty()) {
                fullWidth("empty") { EmptyVideosCard(onAdd = addVideos) }
            }

            itemsIndexed(current.videos, key = { _, uri -> uri }) { index, uri ->
                VideoTile(
                    position = index + 1,
                    uri = uri,
                    loader = viewModel.videoInfoLoader,
                    canMoveEarlier = index > 0,
                    canMoveLater = index < current.videos.lastIndex,
                    onMoveEarlier = { viewModel.moveVideo(index, index - 1) },
                    onMoveLater = { viewModel.moveVideo(index, index + 1) },
                    onRemove = { viewModel.removeVideo(uri) },
                    modifier = Modifier.animateItem(),
                )
            }

            fullWidth("playback-header") {
                Spacer(Modifier.height(8.dp))
                SectionHeader(title = "Playback")
            }
            fullWidth("playback") {
                PlaybackCard(
                    settings = current,
                    onShuffleChange = viewModel::setShuffle,
                    onMutedChange = viewModel::setMuted,
                    onClipSecondsChange = viewModel::setClipSeconds,
                    onSpeedChange = viewModel::setSpeed,
                    onScaleModeChange = viewModel::setScaleMode,
                )
            }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun isOurWallpaperActive(context: Context): Boolean {
    val info = WallpaperManager.getInstance(context).wallpaperInfo ?: return false
    return info.packageName == context.packageName &&
        info.serviceName == VideoWallpaperService::class.java.name
}

/** Opens the system screen that previews this live wallpaper and lets the user apply it. */
private fun openWallpaperPicker(context: Context) {
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
        ComponentName(context, VideoWallpaperService::class.java),
    )
    try {
        context.startActivity(direct)
    } catch (e: ActivityNotFoundException) {
        try {
            // Some phones lack the direct screen; fall back to the list of live wallpapers.
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            Toast.makeText(context, "Choose \"Video Slideshow\" from the list", Toast.LENGTH_LONG).show()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "This phone doesn't support live wallpapers", Toast.LENGTH_LONG).show()
        }
    }
}
