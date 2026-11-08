package com.sadikbr.videowallpaper.data

enum class ClockSize(val label: String, /** Height of the time digits, as a share of the screen width. */ val digitHeight: Float) {
    SMALL("Small", 0.17f),
    MEDIUM("Medium", 0.23f),
    LARGE("Large", 0.29f),
}

enum class ClockWeight(val label: String, /** Android font family. */ val fontFamily: String) {
    THIN("Thin", "sans-serif-thin"),
    REGULAR("Regular", "sans-serif"),
    BOLD("Bold", "sans-serif-black"),
}

enum class TimeFormat(val label: String) {
    SYSTEM("Auto"),
    H12("12-hour"),
    H24("24-hour"),
}

/**
 * The clock the wallpaper draws itself. For photos with a clear subject, the clock is drawn
 * between the photo and the subject (the "depth effect").
 *
 * @property position vertical centre of the time, as a share of the screen height.
 */
data class ClockSettings(
    val enabled: Boolean = false,
    val color: Int = COLORS.first(),
    val size: ClockSize = ClockSize.LARGE,
    val weight: ClockWeight = ClockWeight.REGULAR,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val showDate: Boolean = true,
    val position: Float = DEFAULT_POSITION,
) {
    companion object {
        /** Clock colour choices (ARGB). */
        val COLORS = listOf(
            0xFFFFFFFF.toInt(), // White
            0xFFD8E6FF.toInt(), // Ice blue
            0xFFFFE7A3.toInt(), // Warm yellow
            0xFFFFD1E0.toInt(), // Pink
            0xFFCDEFD8.toInt(), // Mint
            0xFF1B1B1F.toInt(), // Black
        )
        const val DEFAULT_POSITION = 0.22f
        const val MIN_POSITION = 0.10f
        const val MAX_POSITION = 0.60f

        /** How far a single photo can move its clock up or down, as a share of the screen height. */
        const val MAX_PHOTO_OFFSET = 0.25f
    }
}
