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
import com.sadikbr.videowallpaper.ui.components.EmptyMediaCard
import com.sadikbr.videowallpaper.ui.components.AdjustClockDialog
import com.sadikbr.videowallpaper.ui.components.ClockCard
import com.sadikbr.videowallpaper.data.DepthMasks
import com.sadikbr.videowallpaper.data.MediaInfoLoader
import com.sadikbr.videowallpaper.data.MediaKind
import com.sadikbr.videowallpaper.data.TimeFormat
import com.sadikbr.videowallpaper.ui.components.HeroCard
import com.sadikbr.videowallpaper.ui.components.PlaybackCard
import com.sadikbr.videowallpaper.ui.components.SectionHeader
import com.sadikbr.videowallpaper.ui.components.MediaTile
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

    val depthStates by viewModel.depthStates.collectAsStateWithLifecycle()
    var adjustingClockFor by remember { mutableStateOf<String?>(null) }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.addMedia(uris) }
    val addMedia = { pickMedia.launch(arrayOf("image/*", "video/*")) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Wallpaper Slideshow") },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = addMedia,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add photos & videos") },
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

        val use24Hour = when (current.clock.timeFormat) {
            TimeFormat.SYSTEM -> viewModel.systemUses24Hour()
            TimeFormat.H12 -> false
            TimeFormat.H24 -> true
        }

        adjustingClockFor?.let { uri ->
            AdjustClockDialog(
                uri = uri,
                loader = viewModel.mediaInfoLoader,
                clock = current.clock,
                use24Hour = use24Hour,
                scaleMode = current.scaleMode,
                depthState = depthStates[uri],
                savedOffset = current.clockOffsets[uri],
                onSave = { viewModel.setClockOffset(uri, it) },
                onDismiss = { adjustingClockFor = null },
            )
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
                    loader = viewModel.mediaInfoLoader,
                    wallpaperActive = wallpaperActive,
                    use24Hour = use24Hour,
                    depthStates = depthStates,
                    onApply = { openWallpaperPicker(context) },
                )
            }

            fullWidth("media-header") {
                SectionHeader(
                    title = "Your slideshow",
                    trailing = if (current.media.isEmpty()) null else "${current.media.size}",
                    hint = if (current.media.size > 1) "Tap an item to reorder or remove it" else null,
                )
            }

            if (current.media.isEmpty()) {
                fullWidth("empty") { EmptyMediaCard(onAdd = addMedia) }
            }

            itemsIndexed(current.media, key = { _, uri -> uri }) { index, uri ->
                MediaTile(
                    position = index + 1,
                    uri = uri,
                    loader = viewModel.mediaInfoLoader,
                    canMoveEarlier = index > 0,
                    canMoveLater = index < current.media.lastIndex,
                    onMoveEarlier = { viewModel.moveItem(index, index - 1) },
                    onMoveLater = { viewModel.moveItem(index, index + 1) },
                    onRemove = { viewModel.removeItem(uri) },
                    depthState = if (current.clock.enabled) depthStates[uri] else null,
                    onAdjustClock = if (current.clock.enabled) ({ adjustingClockFor = uri }) else null,
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
                    onImageSecondsChange = viewModel::setImageSeconds,
                    onSpeedChange = viewModel::setSpeed,
                    onScaleModeChange = viewModel::setScaleMode,
                    onTransitionChange = viewModel::setTransition,
                    onTransitionMsChange = viewModel::setTransitionMs,
                )
            }

            fullWidth("clock-header") {
                Spacer(Modifier.height(8.dp))
                SectionHeader(title = "Clock")
            }
            fullWidth("clock") {
                ClockCard(
                    clock = current.clock,
                    depthSummary = depthSummary(current.media, viewModel.mediaInfoLoader, depthStates),
                    onUpdate = viewModel::updateClock,
                )
            }
        }
    }
}

/** e.g. "Depth effect ready on 4 of 6 photos", or progress while photos are being analysed. */
private fun depthSummary(
    media: List<String>,
    loader: MediaInfoLoader,
    states: Map<String, DepthMasks.State>,
): String {
    val photos = media.filter { loader.cached(it)?.kind == MediaKind.IMAGE }
    if (photos.isEmpty()) return "Add photos for the depth effect; videos show the clock in front"
    val ready = photos.count { states[it] == DepthMasks.State.READY }
    val processing = photos.count { states[it] == DepthMasks.State.PROCESSING }
    return if (processing > 0) {
        "Finding subjects… ($ready of ${photos.size} photos ready)"
    } else {
        "Depth effect ready on $ready of ${photos.size} photos"
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
            Toast.makeText(context, "Choose \"Wallpaper Slideshow\" from the list", Toast.LENGTH_LONG).show()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "This phone doesn't support live wallpapers", Toast.LENGTH_LONG).show()
        }
    }
}
