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
                media = prefs[Keys.MEDIA]
                    ?.split(SEPARATOR)
                    ?.filter { it.isNotBlank() }
                    .orEmpty(),
                shuffle = prefs[Keys.SHUFFLE] ?: false,
                clipSeconds = prefs[Keys.CLIP_SECONDS] ?: 0,
                imageSeconds = prefs[Keys.IMAGE_SECONDS] ?: WallpaperSettings.DEFAULT_IMAGE_SECONDS,
                scaleMode = prefs[Keys.SCALE_MODE]
                    ?.let { name -> ScaleMode.entries.firstOrNull { it.name == name } }
                    ?: ScaleMode.CROP,
                muted = prefs[Keys.MUTED] ?: true,
                speed = prefs[Keys.SPEED] ?: 1f,
                transition = prefs[Keys.TRANSITION]
                    ?.let { name -> TransitionStyle.entries.firstOrNull { it.name == name } }
                    ?: TransitionStyle.CROSSFADE,
                transitionMs = prefs[Keys.TRANSITION_MS] ?: WallpaperSettings.DEFAULT_TRANSITION_MS,
            )
        }
        .distinctUntilChanged()

    /** Atomically replaces the media list with [transform] applied to the saved one. */
    suspend fun updateMedia(transform: (List<String>) -> List<String>) = dataStore.edit { prefs ->
        val current = prefs[Keys.MEDIA]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
        prefs[Keys.MEDIA] = transform(current).joinToString(SEPARATOR)
    }

    suspend fun setShuffle(value: Boolean) = dataStore.edit { it[Keys.SHUFFLE] = value }

    suspend fun setClipSeconds(value: Int) = dataStore.edit { it[Keys.CLIP_SECONDS] = value }

    suspend fun setImageSeconds(value: Int) = dataStore.edit { it[Keys.IMAGE_SECONDS] = value }

    suspend fun setScaleMode(value: ScaleMode) = dataStore.edit { it[Keys.SCALE_MODE] = value.name }

    suspend fun setMuted(value: Boolean) = dataStore.edit { it[Keys.MUTED] = value }

    suspend fun setSpeed(value: Float) = dataStore.edit { it[Keys.SPEED] = value }

    suspend fun setTransition(value: TransitionStyle) = dataStore.edit { it[Keys.TRANSITION] = value.name }

    suspend fun setTransitionMs(value: Int) = dataStore.edit { it[Keys.TRANSITION_MS] = value }

    private object Keys {
        // Holds photos too; the key name is kept so existing lists survive the update.
        val MEDIA = stringPreferencesKey("videos")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val CLIP_SECONDS = intPreferencesKey("clip_seconds")
        val IMAGE_SECONDS = intPreferencesKey("image_seconds")
        val SCALE_MODE = stringPreferencesKey("scale_mode")
        val MUTED = booleanPreferencesKey("muted")
        val SPEED = floatPreferencesKey("speed")
        val TRANSITION = stringPreferencesKey("transition")
        val TRANSITION_MS = intPreferencesKey("transition_ms")
    }

    private companion object {
        // Content URIs are percent-encoded, so they never contain a newline.
        const val SEPARATOR = "\n"
    }
}
