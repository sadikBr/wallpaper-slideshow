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

    fun removeItem(uri: String) {
        editMedia { current -> current - uri }
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

    private fun editMedia(transform: (List<String>) -> List<String>) = launch {
        repository.updateMedia(transform)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
