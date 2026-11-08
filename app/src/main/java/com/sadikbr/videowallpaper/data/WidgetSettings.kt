package com.sadikbr.videowallpaper.data

enum class WidgetStyle(val label: String) {
    /** Frosted translucent card behind the widgets. */
    GLASS("Glass"),

    /** Just icons and text. */
    PLAIN("Plain"),
}

/**
 * Widgets the wallpaper draws itself (Android has no lock-screen widgets), in front of the
 * photo and its subject.
 *
 * @property signature a line of your own text in a handwriting font; empty for none.
 * @property position top of the widgets as a share of the screen height, or null to sit just
 *   under the clock.
 * @property fahrenheit temperature unit; null follows the phone's region.
 */
data class WidgetSettings(
    val weather: Boolean = false,
    val airQuality: Boolean = false,
    val battery: Boolean = false,
    val calendar: Boolean = false,
    val signature: String = "",
    val style: WidgetStyle = WidgetStyle.GLASS,
    val position: Float? = null,
    val fahrenheit: Boolean? = null,
) {
    val anyEnabled get() = weather || airQuality || battery || calendar || signature.isNotBlank()

    companion object {
        const val MIN_POSITION = 0.08f
        const val MAX_POSITION = 0.85f
    }
}
