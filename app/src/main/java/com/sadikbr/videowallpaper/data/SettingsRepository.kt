package com.sadikbr.videowallpaper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// One DataStore per process: the settings screen and the wallpaper service both use it,
// so a change made in the app reaches a running wallpaper immediately.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "wallpaper_settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<WallpaperSettings> = dataStore.data
        .map { prefs ->
            WallpaperSettings(
                videos = prefs[Keys.VIDEOS]
                    ?.split(SEPARATOR)
                    ?.filter { it.isNotBlank() }
                    .orEmpty(),
                shuffle = prefs[Keys.SHUFFLE] ?: false,
                clipSeconds = prefs[Keys.CLIP_SECONDS] ?: 0,
                scaleMode = prefs[Keys.SCALE_MODE]
                    ?.let { name -> ScaleMode.entries.firstOrNull { it.name == name } }
                    ?: ScaleMode.CROP,
                muted = prefs[Keys.MUTED] ?: true,
                speed = prefs[Keys.SPEED] ?: 1f,
            )
        }
        .distinctUntilChanged()

    /** Atomically replaces the video list with [transform] applied to the saved one. */
    suspend fun updateVideos(transform: (List<String>) -> List<String>) = dataStore.edit { prefs ->
        val current = prefs[Keys.VIDEOS]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
        prefs[Keys.VIDEOS] = transform(current).joinToString(SEPARATOR)
    }

    suspend fun setShuffle(value: Boolean) = dataStore.edit { it[Keys.SHUFFLE] = value }

    suspend fun setClipSeconds(value: Int) = dataStore.edit { it[Keys.CLIP_SECONDS] = value }

    suspend fun setScaleMode(value: ScaleMode) = dataStore.edit { it[Keys.SCALE_MODE] = value.name }

    suspend fun setMuted(value: Boolean) = dataStore.edit { it[Keys.MUTED] = value }

    suspend fun setSpeed(value: Float) = dataStore.edit { it[Keys.SPEED] = value }

    private object Keys {
        val VIDEOS = stringPreferencesKey("videos")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val CLIP_SECONDS = intPreferencesKey("clip_seconds")
        val SCALE_MODE = stringPreferencesKey("scale_mode")
        val MUTED = booleanPreferencesKey("muted")
        val SPEED = floatPreferencesKey("speed")
    }

    private companion object {
        // Content URIs are percent-encoded, so they never contain a newline.
        const val SEPARATOR = "\n"
    }
}
