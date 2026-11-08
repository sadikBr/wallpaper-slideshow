package com.sadikbr.videowallpaper.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sadikbr.videowallpaper.data.ScaleMode
import com.sadikbr.videowallpaper.data.VideoInfo
import com.sadikbr.videowallpaper.data.VideoInfoLoader
import com.sadikbr.videowallpaper.data.WallpaperSettings
import com.sadikbr.videowallpaper.wallpaper.VideoWallpaperService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // Re-check on every return to the app, e.g. after the system "Set wallpaper" screen.
    var wallpaperActive by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        wallpaperActive = isOurWallpaperActive(context)
        onPauseOrDispose { }
    }

    val pickVideos = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.addVideos(uris) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Video Slideshow") }) },
        bottomBar = {
            ApplyBar(
                wallpaperActive = wallpaperActive,
                enabled = settings?.videos?.isNotEmpty() == true,
                onApply = { openWallpaperPicker(context) },
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionTitle("Videos (${current.videos.size})")
            }
            if (current.videos.isEmpty()) {
                item {
                    Text(
                        "No videos yet. Add some videos and they'll play one after another as your background.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(current.videos, key = { _, uri -> uri }) { index, uri ->
                VideoRow(
                    uri = uri,
                    loader = viewModel.videoInfoLoader,
                    canMoveUp = index > 0,
                    canMoveDown = index < current.videos.lastIndex,
                    onMoveUp = { viewModel.moveVideo(index, index - 1) },
                    onMoveDown = { viewModel.moveVideo(index, index + 1) },
                    onRemove = { viewModel.removeVideo(uri) },
                    modifier = Modifier.animateItem(),
                )
            }
            item {
                FilledTonalButton(
                    onClick = { pickVideos.launch(arrayOf("video/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add videos")
                }
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                PlaybackSettings(current, viewModel)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun VideoRow(
    uri: String,
    loader: VideoInfoLoader,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var info by remember(uri) { mutableStateOf<VideoInfo?>(loader.cached(uri)) }
    LaunchedEffect(uri) {
        info = loader.load(uri)
    }

    Card(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val thumbnail = info?.thumbnail
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        if (info?.available == false) Icons.Default.Warning else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    info?.name ?: "Video",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = when {
                    info == null -> "Loading…"
                    info?.available == false -> "Can't open this video — it will be skipped"
                    else -> info?.durationMs?.let(::formatDuration) ?: ""
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (info?.available == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Column {
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackSettings(settings: WallpaperSettings, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Playback")

        SwitchRow(
            title = "Shuffle",
            description = "Play videos in random order",
            checked = settings.shuffle,
            onCheckedChange = viewModel::setShuffle,
        )
        SwitchRow(
            title = "Sound",
            description = "Play the videos' audio",
            checked = !settings.muted,
            onCheckedChange = { viewModel.setMuted(!it) },
        )

        SettingLabel("Length of each video")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WallpaperSettings.CLIP_OPTIONS.forEach { seconds ->
                FilterChip(
                    selected = settings.clipSeconds == seconds,
                    onClick = { viewModel.setClipSeconds(seconds) },
                    label = { Text(if (seconds == 0) "Full video" else "${seconds}s") },
                )
            }
        }

        SettingLabel("Fit to screen")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.scaleMode == ScaleMode.CROP,
                onClick = { viewModel.setScaleMode(ScaleMode.CROP) },
                label = { Text("Fill screen (crop)") },
            )
            FilterChip(
                selected = settings.scaleMode == ScaleMode.FIT,
                onClick = { viewModel.setScaleMode(ScaleMode.FIT) },
                label = { Text("Whole video (bars)") },
            )
        }

        SettingLabel("Speed")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WallpaperSettings.SPEED_OPTIONS.forEach { speed ->
                FilterChip(
                    selected = settings.speed == speed,
                    onClick = { viewModel.setSpeed(speed) },
                    label = { Text("${formatSpeed(speed)}×") },
                )
            }
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ApplyBar(wallpaperActive: Boolean, enabled: Boolean, onApply: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            if (wallpaperActive) {
                Text(
                    "Your slideshow is the current wallpaper. Changes apply right away.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = onApply, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (wallpaperActive) "Open wallpaper preview" else "Set as wallpaper")
            }
        }
    }
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

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
