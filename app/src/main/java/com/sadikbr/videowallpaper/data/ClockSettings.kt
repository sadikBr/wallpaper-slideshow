package com.sadikbr.videowallpaper.data

/**
 * Typefaces for the clock. System fonts are named by family; bundled ones by asset file
 * (`assets/fonts/`, all SIL Open Font License).
 *
 * The first four names match the earlier "style" setting, so saved choices carry over.
 */
enum class ClockFont(
    val label: String,
    /** Android system font family, or null for a bundled font. */
    val family: String? = null,
    /** Bundled font file in `assets/fonts/`. */
    val asset: String? = null,
    /** Built-in vertical stretch of this font on top of [ClockSettings.stretch]. */
    val heightScale: Float = 1f,
    /** Horizontal squeeze. */
    val widthScale: Float = 1f,
) {
    TALL("Tall", family = "sans-serif-condensed-light", heightScale = 1.35f, widthScale = 0.86f),
    THIN("Thin", family = "sans-serif-thin"),
    REGULAR("Regular", family = "sans-serif"),
    BOLD("Bold", family = "sans-serif-black"),
    ANTONIO_THIN("Antonio", asset = "fonts/antonio_thin.ttf"),
    ANTONIO_BOLD("Antonio Bold", asset = "fonts/antonio_bold.ttf"),
    OSWALD("Oswald", asset = "fonts/oswald_light.ttf"),
    BEBAS("Bebas", asset = "fonts/bebas_neue.ttf"),
    BIG_SHOULDERS("Shoulders", asset = "fonts/big_shoulders_thin.ttf"),
}

enum class TimeFormat(val label: String) {
    SYSTEM("Auto"),
    H12("12-hour"),
    H24("24-hour"),
}

/**
 * The clock the wallpaper draws itself. For photos with a clear subject, the time is drawn
 * between the photo and the subject (the "depth effect").
 *
 * @property width how wide the time is, as a share of the screen width (measured on "00:00", so
 *   the size doesn't change with the time).
 * @property stretch vertical stretch of the digits (1 = the font's natural shape), like the
 *   stretchable iOS clock.
 * @property opacity 1 = solid; lower values give a see-through "glass" clock.
 * @property fade how much the digits fade out towards their bottom (0 = not at all).
 * @property position top of the time digits, as a share of the screen height (stretching makes
 *   the clock grow downwards from there).
 * @property autoPlace reframe each photo (or move the clock) so its subject overlaps the time.
 */
data class ClockSettings(
    val enabled: Boolean = false,
    val color: Int = COLORS.first(),
    val font: ClockFont = ClockFont.ANTONIO_THIN,
    val width: Float = DEFAULT_WIDTH,
    val stretch: Float = 1.25f,
    val opacity: Float = 0.92f,
    val fade: Float = 0f,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val showDate: Boolean = true,
    val position: Float = DEFAULT_POSITION,
    val autoPlace: Boolean = true,
) {
    companion object {
        /** Clock colour choices (ARGB). */
        val COLORS = listOf(
            0xFFFFFFFF.toInt(), // White
            0xFFD8E6FF.toInt(), // Ice blue
            0xFFFFE7A3.toInt(), // Warm yellow
            0xFFFFD1E0.toInt(), // Pink
            0xFFCDEFD8.toInt(), // Mint
            0xFFC9C4FF.toInt(), // Lavender
            0xFF9AA3B5.toInt(), // Slate
            0xFF1B1B1F.toInt(), // Black
        )
        const val DEFAULT_POSITION = 0.13f
        const val MIN_POSITION = 0.06f
        const val MAX_POSITION = 0.55f

        const val DEFAULT_WIDTH = 0.8f
        const val MIN_WIDTH = 0.35f
        const val MAX_WIDTH = 0.96f
        const val MIN_STRETCH = 0.8f
        const val MAX_STRETCH = 2.6f
        const val MIN_OPACITY = 0.25f

        /** How far a single photo can move its clock up or down, as a share of the screen height. */
        const val MAX_PHOTO_OFFSET = 0.45f

        /** Widths for the earlier Small / Medium / Large sizes, for migrating saved settings. */
        val LEGACY_SIZES = mapOf("SMALL" to 0.5f, "MEDIUM" to 0.65f, "LARGE" to 0.8f)
    }
}
