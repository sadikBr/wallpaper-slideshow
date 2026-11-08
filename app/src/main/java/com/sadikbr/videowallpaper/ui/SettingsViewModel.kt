package com.sadikbr.videowallpaper.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sadikbr.videowallpaper.data.ScaleMode
import com.sadikbr.videowallpaper.data.SettingsRepository
import com.sadikbr.videowallpaper.data.TransitionStyle
import com.sadikbr.videowallpaper.data.MediaInfoLoader
import com.sadikbr.videowallpaper.data.WallpaperSettings
import android.text.format.DateFormat
import com.sadikbr.videowallpaper.data.BuiltInWallpapers
import com.sadikbr.videowallpaper.data.ClockSettings
import com.sadikbr.videowallpaper.data.DepthMasks
import com.sadikbr.videowallpaper.data.MediaKind
import com.sadikbr.videowallpaper.ui.studio.StudioResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    val mediaInfoLoader = MediaInfoLoader(application)

    /** Null until the saved settings have been read for the first time. */
    val settings: StateFlow<WallpaperSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Progress of the subject cut-outs used by the depth clock, per photo URI. */
    val depthStates: StateFlow<Map<String, DepthMasks.State>> = DepthMasks.states

    init {
        // With the depth clock on, make sure every photo has its cut-out (done once per photo).
        viewModelScope.launch(Dispatchers.IO) {
            repository.settings
                .map { if (it.clock.enabled) it.media else emptyList() }
                .distinctUntilChanged()
                .collect { media ->
                    val photos = media.filter { mediaInfoLoader.load(it).kind == MediaKind.IMAGE }
                    DepthMasks.ensure(getApplication(), photos)
                }
        }
    }

    /** Whether the system is set to 24-hour time (used when the clock follows the system). */
    fun systemUses24Hour(): Boolean = DateFormat.is24HourFormat(getApplication())

    fun addMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val resolver = getApplication<Application>().contentResolver
        val added = uris.mapNotNull { uri ->
            // Keep read access after a restart, so the wallpaper can still open the file.
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            uri.toString()
        }
        editMedia { current -> current + added.filterNot { it in current }.distinct() }
    }

    /** Adds a built-in wallpaper to the slideshow, or removes it if it is already in. */
    fun toggleBuiltIn(wallpaper: BuiltInWallpapers.Wallpaper) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val uri = BuiltInWallpapers.uriOf(app, wallpaper)
            if (uri in settings.value?.media.orEmpty()) {
                removeItem(uri)
            } else {
                BuiltInWallpapers.install(app, wallpaper)
                repository.updateMedia { current -> if (uri in current) current else current + uri }
            }
        }
    }

    fun builtInUri(wallpaper: BuiltInWallpapers.Wallpaper): String = BuiltInWallpapers.uriOf(getApplication(), wallpaper)

    fun removeItem(uri: String) {
        editMedia { current -> current - uri }
        launch {
            repository.removeClockOffset(uri)
            repository.setPhotoFraming(uri, null)
        }
        DepthMasks.delete(getApplication(), uri)
        runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun moveItem(from: Int, to: Int) = editMedia { current ->
        if (from !in current.indices || to !in current.indices) {
            current
        } else {
            current.toMutableList().apply { add(to, removeAt(from)) }
        }
    }

    fun setShuffle(value: Boolean) = launch { repository.setShuffle(value) }
    fun setClipSeconds(value: Int) = launch { repository.setClipSeconds(value) }
    fun setImageSeconds(value: Int) = launch { repository.setImageSeconds(value) }
    fun setScaleMode(value: ScaleMode) = launch { repository.setScaleMode(value) }
    fun setMuted(value: Boolean) = launch { repository.setMuted(value) }
    fun setSpeed(value: Float) = launch { repository.setSpeed(value) }
    fun setTransition(value: TransitionStyle) = launch { repository.setTransition(value) }
    fun setTransitionMs(value: Int) = launch { repository.setTransitionMs(value) }

    /** Saves what the wallpaper editor changed. */
    fun applyStudio(result: StudioResult) = launch {
        repository.updateClock { result.clock }
        result.clockOffsets.forEach { (uri, offset) -> repository.setClockOffset(uri, offset) }
        result.framings.forEach { (uri, framing) -> repository.setPhotoFraming(uri, framing) }
    }

    fun updateClock(transform: (ClockSettings) -> ClockSettings) = launch { repository.updateClock(transform) }
    fun setClockOffset(uri: String, offset: Float?) = launch { repository.setClockOffset(uri, offset) }

    private fun editMedia(transform: (List<String>) -> List<String>) = launch {
        repository.updateMedia(transform)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
