package com.sadikbr.videowallpaper.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sadikbr.videowallpaper.data.WidgetData
import com.sadikbr.videowallpaper.ui.home.HomeActions
import com.sadikbr.videowallpaper.ui.home.HomeScreen
import com.sadikbr.videowallpaper.ui.home.HomeState
import com.sadikbr.videowallpaper.ui.home.SlideshowSheet
import com.sadikbr.videowallpaper.ui.studio.WallpaperStudio
import com.sadikbr.videowallpaper.wallpaper.VideoWallpaperService

/**
 * The app: the home screen, the slideshow settings sheet and the wallpaper editor, wired to
 * the saved settings. (The screens themselves are stateless, so they can be previewed.)
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val depthStates by viewModel.depthStates.collectAsStateWithLifecycle()
    val depthProgress by viewModel.depthProgress.collectAsStateWithLifecycle()
    val widgetSnapshot by WidgetData.snapshot.collectAsStateWithLifecycle()

    // Re-check on every return to the app, e.g. after the system "Set wallpaper" screen.
    var wallpaperActive by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        wallpaperActive = isOurWallpaperActive(context)
        onPauseOrDispose { }
    }

    /** The item the editor is open for ("" = no particular one), or null when closed. */
    var studioFor by remember { mutableStateOf<String?>(null) }
    var slideshowSheet by remember { mutableStateOf(false) }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.addMedia(uris) }
    val addMedia = { pickMedia.launch(arrayOf("image/*", "video/*")) }

    val current = settings
    if (current == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0920)))
        return
    }

    // The editor is a full page of its own (not a pop-up), so it fits between the system bars.
    val editing = studioFor
    if (editing != null) {
        WallpaperStudio(
            settings = current,
            startUri = editing.ifEmpty { null },
            loader = viewModel.mediaInfoLoader,
            depthStates = depthStates,
            systemUses24Hour = viewModel.systemUses24Hour(),
            onDone = { result ->
                viewModel.applyStudio(result)
                studioFor = null
            },
            onDismiss = { studioFor = null },
        )
        return
    }

    HomeScreen(
        state = HomeState(
            settings = current,
            wallpaperActive = wallpaperActive,
            depthStates = depthStates,
            depthProgress = depthProgress,
            widgetSnapshot = widgetSnapshot,
            systemUses24Hour = viewModel.systemUses24Hour(),
        ),
        actions = HomeActions(
            onAdd = addMedia,
            onEdit = { uri -> studioFor = uri ?: current.media.firstOrNull() ?: "" },
            onRemove = viewModel::removeItem,
            onMove = { from, to -> viewModel.moveItem(from, to) },
            onApply = { openWallpaperPicker(context) },
            onClockEverywhere = viewModel::setClockEverywhere,
            onOpenSlideshowSettings = { slideshowSheet = true },
            builtInUri = viewModel::builtInUri,
            onToggleBuiltIn = viewModel::toggleBuiltIn,
        ),
        loader = viewModel.mediaInfoLoader,
    )

    if (slideshowSheet) {
        SlideshowSheet(
            settings = current,
            onDismiss = { slideshowSheet = false },
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
