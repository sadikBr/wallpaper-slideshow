package com.sadikbr.videowallpaper.data

/** Fonts for the signature line (bundled, SIL Open Font License). */
enum class SignatureFont(val label: String, val sample: String, val asset: String) {
    SCRIPT("Script", "Aa", "fonts/great_vibes.ttf"),
    RUQAA("Ruqaa", "خط", "fonts/aref_ruqaa.ttf"),
    KUFI("Kufi", "خط", "fonts/reem_kufi.ttf"),
    ;

    companion object {
        /** The font used when none is chosen: Arabic calligraphy for Arabic text, script otherwise. */
        fun automatic(text: String) = if (text.any(::isArabic)) RUQAA else SCRIPT

        private fun isArabic(c: Char) = c in '\u0600'..'\u06FF' || c in '\u0750'..'\u077F' ||
            c in '\u08A0'..'\u08FF' || c in '\uFB50'..'\uFDFF' || c in '\uFE70'..'\uFEFF'
    }
}

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
 * @property signatureFont its font, or null to pick one from the text (Arabic or Latin).
 * @property signatureSize its size, relative to the normal size (0.5–2).
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
    val signatureFont: SignatureFont? = null,
    val signatureSize: Float = 1f,
    val style: WidgetStyle = WidgetStyle.GLASS,
    val position: Float? = null,
    val fahrenheit: Boolean? = null,
) {
    val anyEnabled get() = weather || airQuality || battery || calendar || signature.isNotBlank()

    /** The signature's font, chosen or automatic. */
    val resolvedSignatureFont get() = signatureFont ?: SignatureFont.automatic(signature)

    companion object {
        const val MIN_SIGNATURE_SIZE = 0.5f
        const val MAX_SIGNATURE_SIZE = 2f
        const val MIN_POSITION = 0.08f
        const val MAX_POSITION = 0.85f
    }
}
