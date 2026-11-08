package com.sadikbr.videowallpaper.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Current weather; temperatures in °C. */
data class Weather(
    val temperature: Double,
    /** WMO weather code (0 = clear, 1–3 cloudy, 45 fog, 51–67 rain, 71–77 snow, 80–82 showers, 95+ storm). */
    val code: Int,
    val isDay: Boolean,
    val high: Double,
    val low: Double,
)

data class CalendarEvent(val title: String, val begin: Long, val allDay: Boolean)

/** What the widgets show right now. Any part may be missing (not enabled, no permission, no data yet). */
data class WidgetSnapshot(
    val weather: Weather? = null,
    val airQuality: Int? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val nextEvent: CalendarEvent? = null,
)

/**
 * Collects the data shown by the widgets, for the wallpaper and the app's previews alike
 * (same process). Weather and air quality come from Open-Meteo (free, no account), using a
 * location saved while the app is open, so the wallpaper never needs background location.
 */
object WidgetData {

    private const val TAG = "WidgetData"
    private val WEATHER_MAX_AGE = TimeUnit.MINUTES.toMillis(30)
    private val CALENDAR_MAX_AGE = TimeUnit.MINUTES.toMillis(10)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(WidgetSnapshot())
    val snapshot: StateFlow<WidgetSnapshot> = _snapshot.asStateFlow()

    private var batteryReceiver: BroadcastReceiver? = null
    private var cacheLoaded = false
    private var latitude: Double? = null
    private var longitude: Double? = null
    private var weatherFetchedAt = 0L
    private var calendarReadAt = 0L

    /** Starts following the battery and loads cached weather. Safe to call repeatedly. */
    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        if (batteryReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = updateBattery(intent)
            }
            // The battery broadcast is sticky: registering returns the current state at once.
            ContextCompat.registerReceiver(
                app, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )?.let(::updateBattery)
            batteryReceiver = receiver
        }
        if (!cacheLoaded) {
            cacheLoaded = true
            scope.launch { loadCache(app) }
        }
    }

    /** Fetches weather / reads the calendar when stale (or always, with [force]). */
    fun refresh(context: Context, widgets: WidgetSettings, force: Boolean = false) {
        val app = context.applicationContext
        start(app)
        scope.launch {
            mutex.withLock {
                val now = System.currentTimeMillis()
                if ((widgets.weather || widgets.airQuality) && (force || now - weatherFetchedAt > WEATHER_MAX_AGE)) {
                    fetchWeather(app)
                }
                if (widgets.calendar && (force || now - calendarReadAt > CALENDAR_MAX_AGE)) {
                    readCalendar(app)
                }
            }
        }
    }

    /**
     * Saves the phone's approximate location for the weather, if permitted. Call while the
     * app is in the foreground.
     */
    @SuppressLint("MissingPermission")
    fun updateLocation(context: Context, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        if (!hasPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION)) return
        val manager = app.getSystemService(LocationManager::class.java) ?: return
        val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null) {
            saveLocation(app, last)
            onDone()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, app.mainExecutor) { location ->
                    if (location != null) saveLocation(app, location)
                    onDone()
                }
            }
        }
    }

    fun hasPermission(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Whether temperatures should be shown in °F. */
    fun usesFahrenheit(widgets: WidgetSettings): Boolean =
        widgets.fahrenheit ?: (Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "KY", "PW"))

    private fun saveLocation(context: Context, location: Location) {
        val moved = latitude == null ||
            Math.abs(location.latitude - (latitude ?: 0.0)) > 0.05 ||
            Math.abs(location.longitude - (longitude ?: 0.0)) > 0.05
        latitude = location.latitude
        longitude = location.longitude
        if (moved) weatherFetchedAt = 0L
        scope.launch { saveCache(context) }
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val percent = level * 100 / scale
        _snapshot.update { if (it.batteryPercent == percent && it.charging == charging) it else it.copy(batteryPercent = percent, charging = charging) }
    }

    private fun fetchWeather(context: Context) {
        val lat = latitude ?: return
        val lon = longitude ?: return
        try {
            val weatherJson = JSONObject(
                httpGet(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
                        "&forecast_days=1&timezone=auto",
                ),
            )
            val current = weatherJson.getJSONObject("current")
            val daily = weatherJson.getJSONObject("daily")
            val weather = Weather(
                temperature = current.getDouble("temperature_2m"),
                code = current.getInt("weather_code"),
                isDay = current.optInt("is_day", 1) == 1,
                high = daily.getJSONArray("temperature_2m_max").getDouble(0),
                low = daily.getJSONArray("temperature_2m_min").getDouble(0),
            )
            val aqi = runCatching {
                JSONObject(
                    httpGet("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi"),
                ).getJSONObject("current").getInt("us_aqi")
            }.getOrNull()
            weatherFetchedAt = System.currentTimeMillis()
            _snapshot.update { it.copy(weather = weather, airQuality = aqi ?: it.airQuality) }
            saveCache(context)
        } catch (e: Exception) {
            Log.w(TAG, "Weather update failed", e)
        }
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun readCalendar(context: Context) {
        if (!hasPermission(context, Manifest.permission.READ_CALENDAR)) return
        val now = System.currentTimeMillis()
        val endOfTomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }.timeInMillis
        val event = runCatching {
            CalendarContract.Instances.query(
                context.contentResolver,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
                now,
                endOfTomorrow,
            )?.use { cursor ->
                var best: CalendarEvent? = null
                while (cursor.moveToNext()) {
                    val title = cursor.getString(0).orEmpty()
                    val begin = cursor.getLong(1)
                    val end = cursor.getLong(2)
                    val allDay = cursor.getInt(3) == 1
                    if (end <= now || title.isBlank()) continue
                    // Prefer timed events; all-day ones only if nothing else.
                    val candidate = CalendarEvent(title, begin, allDay)
                    if (best == null || (best.allDay && !allDay) || (best.allDay == allDay && begin < best.begin)) best = candidate
                }
                best
            }
        }.getOrNull()
        calendarReadAt = now
        _snapshot.update { it.copy(nextEvent = event) }
    }

    private fun cacheFile(context: Context) = File(context.filesDir, "widgets.json")

    private fun loadCache(context: Context) = runCatching {
        val json = JSONObject(cacheFile(context).readText())
        if (json.has("lat")) {
            latitude = json.getDouble("lat")
            longitude = json.getDouble("lon")
        }
        weatherFetchedAt = json.optLong("fetchedAt", 0L)
        json.optJSONObject("weather")?.let { w ->
            val weather = Weather(w.getDouble("t"), w.getInt("code"), w.getBoolean("day"), w.getDouble("hi"), w.getDouble("lo"))
            _snapshot.update { it.copy(weather = weather, airQuality = if (json.has("aqi")) json.getInt("aqi") else null) }
        }
    }

    private fun saveCache(context: Context) = runCatching {
        val json = JSONObject()
        latitude?.let { json.put("lat", it) }
        longitude?.let { json.put("lon", it) }
        json.put("fetchedAt", weatherFetchedAt)
        _snapshot.value.weather?.let { w ->
            json.put("weather", JSONObject().put("t", w.temperature).put("code", w.code).put("day", w.isDay).put("hi", w.high).put("lo", w.low))
        }
        _snapshot.value.airQuality?.let { json.put("aqi", it) }
        cacheFile(context).writeText(json.toString())
    }
}
