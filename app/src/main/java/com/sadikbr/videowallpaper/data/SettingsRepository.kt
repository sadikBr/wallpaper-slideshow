package com.sadikbr.videowallpaper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
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
                clock = readClock(prefs),
                clockOffsets = decodeOffsets(prefs[Keys.CLOCK_OFFSETS]),
                photoFramings = decodeFramings(prefs[Keys.PHOTO_FRAMINGS]),
                widgets = readWidgets(prefs),
                lockClockGuideDone = prefs[Keys.LOCK_CLOCK_GUIDE_DONE] ?: false,
                itemLooks = decodeLooks(prefs[Keys.ITEM_LOOKS], WallpaperLook(readClock(prefs), readWidgets(prefs))),
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

    suspend fun updateClock(transform: (ClockSettings) -> ClockSettings) = dataStore.edit { prefs ->
        writeClock(prefs, transform(readClock(prefs)))
    }

    private fun writeClock(prefs: MutablePreferences, updated: ClockSettings) {
        prefs[Keys.CLOCK_ENABLED] = updated.enabled
        prefs[Keys.CLOCK_COLOR] = updated.color
        prefs[Keys.CLOCK_WEIGHT] = updated.font.name
        prefs[Keys.CLOCK_WIDTH] = updated.width
        prefs[Keys.CLOCK_STRETCH] = updated.stretch
        prefs[Keys.CLOCK_OPACITY] = updated.opacity
        prefs[Keys.CLOCK_FADE] = updated.fade
        prefs[Keys.CLOCK_FORMAT] = updated.timeFormat.name
        prefs[Keys.CLOCK_SHOW_DATE] = updated.showDate
        prefs[Keys.CLOCK_POSITION] = updated.position
        prefs[Keys.CLOCK_AUTO_PLACE] = updated.autoPlace
    }

    /** Sets how far the clock is moved for one photo, or with null goes back to automatic. */
    suspend fun setClockOffset(uri: String, offset: Float?) = dataStore.edit { prefs ->
        val offsets = decodeOffsets(prefs[Keys.CLOCK_OFFSETS]).toMutableMap()
        if (offset == null) offsets.remove(uri) else offsets[uri] = offset
        prefs[Keys.CLOCK_OFFSETS] = encodeOffsets(offsets)
    }

    suspend fun removeClockOffset(uri: String) = setClockOffset(uri, null)

    /** Sets how one photo is zoomed and moved, or with null goes back to automatic framing. */
    suspend fun setPhotoFraming(uri: String, framing: PhotoFraming?) = dataStore.edit { prefs ->
        val framings = decodeFramings(prefs[Keys.PHOTO_FRAMINGS]).toMutableMap()
        if (framing == null) framings.remove(uri) else framings[uri] = framing
        prefs[Keys.PHOTO_FRAMINGS] = framings.entries.joinToString(SEPARATOR) { (key, value) ->
            "$key\t${value.zoom},${value.shift},${value.shiftX}"
        }
    }

    private fun decodeFramings(encoded: String?): Map<String, PhotoFraming> = encoded.orEmpty()
        .split(SEPARATOR)
        .mapNotNull { line ->
            val tab = line.lastIndexOf('\t')
            if (tab <= 0) return@mapNotNull null
            val parts = line.substring(tab + 1).split(',')
            val zoom = parts.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
            val shift = parts.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
            val shiftX = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
            line.substring(0, tab) to PhotoFraming(zoom, shift, shiftX)
        }
        .toMap()

    suspend fun updateWidgets(transform: (WidgetSettings) -> WidgetSettings) = dataStore.edit { prefs ->
        writeWidgets(prefs, transform(readWidgets(prefs)))
    }

    private fun writeWidgets(prefs: MutablePreferences, updated: WidgetSettings) {
        prefs[Keys.WIDGET_WEATHER] = updated.weather
        prefs[Keys.WIDGET_AIR] = updated.airQuality
        prefs[Keys.WIDGET_BATTERY] = updated.battery
        prefs[Keys.WIDGET_CALENDAR] = updated.calendar
        prefs[Keys.WIDGET_SIGNATURE] = updated.signature
        prefs[Keys.WIDGET_STYLE] = updated.style.name
        if (updated.position == null) prefs.remove(Keys.WIDGET_POSITION) else prefs[Keys.WIDGET_POSITION] = updated.position
        when (updated.fahrenheit) {
            null -> prefs.remove(Keys.WIDGET_FAHRENHEIT)
            else -> prefs[Keys.WIDGET_FAHRENHEIT] = updated.fahrenheit
        }
    }

    suspend fun setLockClockGuideDone(done: Boolean) = dataStore.edit { it[Keys.LOCK_CLOCK_GUIDE_DONE] = done }

    /** Sets (or with null, removes) the look of the given items. */
    suspend fun setItemLooks(looks: Map<String, WallpaperLook?>) = dataStore.edit { prefs ->
        val default = WallpaperLook(readClock(prefs), readWidgets(prefs))
        val current = decodeLooks(prefs[Keys.ITEM_LOOKS], default).toMutableMap()
        looks.forEach { (uri, look) -> if (look == null) current.remove(uri) else current[uri] = look }
        prefs[Keys.ITEM_LOOKS] = JSONObject().apply { current.forEach { (uri, look) -> put(uri, look.toJson()) } }.toString()
    }

    /** Applies [transform] to the default look and to every item's own look. */
    suspend fun updateAllLooks(transform: (WallpaperLook) -> WallpaperLook) = dataStore.edit { prefs ->
        val default = WallpaperLook(readClock(prefs), readWidgets(prefs))
        val current = decodeLooks(prefs[Keys.ITEM_LOOKS], default)
        val newDefault = transform(default)
        writeClock(prefs, newDefault.clock)
        writeWidgets(prefs, newDefault.widgets)
        prefs[Keys.ITEM_LOOKS] = JSONObject().apply {
            current.forEach { (uri, look) -> put(uri, transform(look).toJson()) }
        }.toString()
    }

    /** Makes [look] the default and every item's look (the editor's "Apply to all"). */
    suspend fun setLookForAll(look: WallpaperLook) = dataStore.edit { prefs ->
        writeClock(prefs, look.clock)
        writeWidgets(prefs, look.widgets)
        prefs.remove(Keys.ITEM_LOOKS)
    }

    private fun decodeLooks(encoded: String?, default: WallpaperLook): Map<String, WallpaperLook> = runCatching {
        val json = JSONObject(encoded ?: return emptyMap())
        json.keys().asSequence().associateWith { WallpaperLook.fromJson(json.getJSONObject(it), default) }
    }.getOrDefault(emptyMap())

    private fun readWidgets(prefs: Preferences) = WidgetSettings(
        weather = prefs[Keys.WIDGET_WEATHER] ?: false,
        airQuality = prefs[Keys.WIDGET_AIR] ?: false,
        battery = prefs[Keys.WIDGET_BATTERY] ?: false,
        calendar = prefs[Keys.WIDGET_CALENDAR] ?: false,
        signature = prefs[Keys.WIDGET_SIGNATURE] ?: "",
        style = enumOrDefault(prefs[Keys.WIDGET_STYLE], WidgetStyle.GLASS),
        position = prefs[Keys.WIDGET_POSITION],
        fahrenheit = prefs[Keys.WIDGET_FAHRENHEIT],
    )

    private fun readClock(prefs: Preferences) = ClockSettings(
        enabled = prefs[Keys.CLOCK_ENABLED] ?: false,
        color = prefs[Keys.CLOCK_COLOR] ?: ClockSettings.COLORS.first(),
        font = enumOrDefault(prefs[Keys.CLOCK_WEIGHT], ClockSettings().font),
        width = prefs[Keys.CLOCK_WIDTH]
            ?: prefs[Keys.CLOCK_SIZE]?.let { ClockSettings.LEGACY_SIZES[it] }
            ?: ClockSettings.DEFAULT_WIDTH,
        stretch = prefs[Keys.CLOCK_STRETCH] ?: if (prefs[Keys.CLOCK_WEIGHT] != null) 1f else ClockSettings().stretch,
        opacity = prefs[Keys.CLOCK_OPACITY] ?: if (prefs[Keys.CLOCK_WEIGHT] != null) 1f else ClockSettings().opacity,
        fade = prefs[Keys.CLOCK_FADE] ?: 0f,
        timeFormat = enumOrDefault(prefs[Keys.CLOCK_FORMAT], TimeFormat.SYSTEM),
        showDate = prefs[Keys.CLOCK_SHOW_DATE] ?: true,
        position = prefs[Keys.CLOCK_POSITION] ?: ClockSettings.DEFAULT_POSITION,
        autoPlace = prefs[Keys.CLOCK_AUTO_PLACE] ?: true,
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private fun decodeOffsets(encoded: String?): Map<String, Float> = encoded.orEmpty()
        .split(SEPARATOR)
        .mapNotNull { line ->
            val tab = line.lastIndexOf('\t')
            if (tab <= 0) null else line.substring(tab + 1).toFloatOrNull()?.let { line.substring(0, tab) to it }
        }
        .toMap()

    private fun encodeOffsets(offsets: Map<String, Float>): String =
        offsets.entries.joinToString(SEPARATOR) { (uri, offset) -> "$uri\t$offset" }

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
        val CLOCK_ENABLED = booleanPreferencesKey("clock_enabled")
        val CLOCK_COLOR = intPreferencesKey("clock_color")
        val CLOCK_SIZE = stringPreferencesKey("clock_size")
        val CLOCK_WEIGHT = stringPreferencesKey("clock_weight")
        val CLOCK_FORMAT = stringPreferencesKey("clock_format")
        val CLOCK_SHOW_DATE = booleanPreferencesKey("clock_show_date")
        val CLOCK_POSITION = floatPreferencesKey("clock_position")
        val CLOCK_OFFSETS = stringPreferencesKey("clock_offsets")
        val CLOCK_AUTO_PLACE = booleanPreferencesKey("clock_auto_place")
        val CLOCK_WIDTH = floatPreferencesKey("clock_width")
        val CLOCK_STRETCH = floatPreferencesKey("clock_stretch")
        val CLOCK_OPACITY = floatPreferencesKey("clock_opacity")
        val CLOCK_FADE = floatPreferencesKey("clock_fade")
        val PHOTO_FRAMINGS = stringPreferencesKey("photo_framings")
        val WIDGET_WEATHER = booleanPreferencesKey("widget_weather")
        val WIDGET_AIR = booleanPreferencesKey("widget_air_quality")
        val WIDGET_BATTERY = booleanPreferencesKey("widget_battery")
        val WIDGET_CALENDAR = booleanPreferencesKey("widget_calendar")
        val WIDGET_SIGNATURE = stringPreferencesKey("widget_signature")
        val WIDGET_STYLE = stringPreferencesKey("widget_style")
        val WIDGET_POSITION = floatPreferencesKey("widget_position")
        val WIDGET_FAHRENHEIT = booleanPreferencesKey("widget_fahrenheit")
        val ITEM_LOOKS = stringPreferencesKey("item_looks")
        val LOCK_CLOCK_GUIDE_DONE = booleanPreferencesKey("lock_clock_guide_done")
    }

    private companion object {
        // Content URIs are percent-encoded, so they never contain a newline.
        const val SEPARATOR = "\n"
    }
}
