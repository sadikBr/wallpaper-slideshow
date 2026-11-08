package com.sadikbr.videowallpaper.data

import org.json.JSONObject

/** The clock and widgets of one wallpaper. Each slideshow item can have its own. */
data class WallpaperLook(
    val clock: ClockSettings = ClockSettings(),
    val widgets: WidgetSettings = WidgetSettings(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("clock", clock.toJson())
        .put("widgets", widgets.toJson())

    companion object {
        fun fromJson(json: JSONObject, fallback: WallpaperLook) = WallpaperLook(
            clock = json.optJSONObject("clock")?.let { clockFromJson(it, fallback.clock) } ?: fallback.clock,
            widgets = json.optJSONObject("widgets")?.let { widgetsFromJson(it, fallback.widgets) } ?: fallback.widgets,
        )

        private fun ClockSettings.toJson() = JSONObject()
            .put("enabled", enabled)
            .put("color", color)
            .put("font", font.name)
            .put("width", width.toDouble())
            .put("stretch", stretch.toDouble())
            .put("opacity", opacity.toDouble())
            .put("fade", fade.toDouble())
            .put("timeFormat", timeFormat.name)
            .put("showDate", showDate)
            .put("position", position.toDouble())
            .put("autoPlace", autoPlace)
            .apply { waterline?.let { put("waterline", it.toDouble()) } }
            .put("waterGap", waterGap.toDouble())

        private fun clockFromJson(j: JSONObject, d: ClockSettings) = ClockSettings(
            enabled = j.optBoolean("enabled", d.enabled),
            color = j.optInt("color", d.color),
            font = ClockFont.entries.firstOrNull { it.name == j.optString("font") } ?: d.font,
            width = j.optDouble("width", d.width.toDouble()).toFloat(),
            stretch = j.optDouble("stretch", d.stretch.toDouble()).toFloat(),
            opacity = j.optDouble("opacity", d.opacity.toDouble()).toFloat(),
            fade = j.optDouble("fade", d.fade.toDouble()).toFloat(),
            timeFormat = TimeFormat.entries.firstOrNull { it.name == j.optString("timeFormat") } ?: d.timeFormat,
            showDate = j.optBoolean("showDate", d.showDate),
            position = j.optDouble("position", d.position.toDouble()).toFloat(),
            autoPlace = j.optBoolean("autoPlace", d.autoPlace),
            waterline = if (j.has("waterline")) j.optDouble("waterline").toFloat() else null,
            waterGap = j.optDouble("waterGap", d.waterGap.toDouble()).toFloat(),
        )

        private fun WidgetSettings.toJson() = JSONObject()
            .put("weather", weather)
            .put("airQuality", airQuality)
            .put("battery", battery)
            .put("calendar", calendar)
            .put("signature", signature)
            .put("signatureSize", signatureSize.toDouble())
            .put("style", style.name)
            .apply {
                position?.let { put("position", it.toDouble()) }
                fahrenheit?.let { put("fahrenheit", it) }
                signatureFont?.let { put("signatureFont", it.name) }
            }

        private fun widgetsFromJson(j: JSONObject, d: WidgetSettings) = WidgetSettings(
            weather = j.optBoolean("weather", d.weather),
            airQuality = j.optBoolean("airQuality", d.airQuality),
            battery = j.optBoolean("battery", d.battery),
            calendar = j.optBoolean("calendar", d.calendar),
            signature = j.optString("signature", d.signature),
            signatureFont = SignatureFont.entries.firstOrNull { it.name == j.optString("signatureFont") },
            signatureSize = j.optDouble("signatureSize", d.signatureSize.toDouble()).toFloat(),
            style = WidgetStyle.entries.firstOrNull { it.name == j.optString("style") } ?: d.style,
            position = if (j.has("position")) j.getDouble("position").toFloat() else null,
            fahrenheit = if (j.has("fahrenheit")) j.getBoolean("fahrenheit") else null,
        )
    }
}
