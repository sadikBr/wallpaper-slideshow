package com.sadikbr.videowallpaper.clock

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import com.sadikbr.videowallpaper.data.ClockSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lays out and draws the wallpaper's clock: a short date line above large time digits,
 * centred horizontally. Used both by the wallpaper renderer and by the in-app previews,
 * so they look the same.
 */
object ClockPainter {

    /**
     * @param offset per-photo adjustment of the vertical position (share of [height]).
     */
    fun layout(
        clock: ClockSettings,
        use24Hour: Boolean,
        width: Int,
        height: Int,
        offset: Float = 0f,
        now: Date = Date(),
    ): ClockLayout {
        val locale = Locale.getDefault()
        val timeText = SimpleDateFormat(if (use24Hour) "HH:mm" else "h:mm", locale).format(now)
        val dateText = if (clock.showDate) {
            SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(now)
        } else {
            null
        }

        val hasShadow = Color.luminance(clock.color) > 0.5f
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = clock.color
            typeface = Typeface.create(clock.weight.fontFamily, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = -0.02f
            textScaleX = clock.weight.widthScale
            textSize = 100f
        }
        // Size the font so the digits are the requested height, but never wider than the screen.
        val digitBounds = Rect()
        timePaint.getTextBounds("0123456789", 0, 10, digitBounds)
        val targetDigitHeight = clock.size.digitHeight * clock.weight.heightScale * width
        timePaint.textSize = 100f * targetDigitHeight / digitBounds.height().coerceAtLeast(1)
        val maxTextWidth = width * 0.9f
        val measured = timePaint.measureText(timeText)
        if (measured > maxTextWidth) timePaint.textSize *= maxTextWidth / measured
        timePaint.getTextBounds("0123456789", 0, 10, digitBounds)
        val digitHeight = digitBounds.height().toFloat()

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = clock.color
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            textSize = width * 0.045f
        }
        if (hasShadow) {
            // A faint shadow keeps light text readable on bright photos.
            val radius = width * 0.012f
            timePaint.setShadowLayer(radius, 0f, radius * 0.3f, 0x33000000)
            datePaint.setShadowLayer(radius, 0f, radius * 0.3f, 0x33000000)
        }

        val position = (clock.position + offset).coerceIn(0.05f, 0.95f)
        val centerX = width / 2f
        val timeBaseline = position * height + digitHeight / 2f
        val timeWidth = timePaint.measureText(timeText)
        val timeRect = RectF(
            centerX - timeWidth / 2f,
            timeBaseline - digitHeight,
            centerX + timeWidth / 2f,
            timeBaseline,
        )

        // Room for the shadow and anti-aliasing around each part.
        val pad = width * 0.03f
        val timeBounds = RectF(timeRect).apply { inset(-pad, -pad) }
        var dateBaseline = 0f
        var dateBounds: RectF? = null
        if (dateText != null) {
            dateBaseline = timeRect.top - width * 0.035f
            val dateWidth = datePaint.measureText(dateText)
            dateBounds = RectF(
                centerX - dateWidth / 2f,
                dateBaseline + datePaint.ascent(),
                centerX + dateWidth / 2f,
                dateBaseline + datePaint.descent(),
            ).apply { inset(-pad, -pad) }
        }

        return ClockLayout(
            timeText, dateText, timePaint, datePaint, centerX, timeBaseline, dateBaseline,
            timeRect, timeBounds, dateBounds,
        )
    }
}

class ClockLayout internal constructor(
    private val timeText: String,
    private val dateText: String?,
    private val timePaint: Paint,
    private val datePaint: Paint,
    private val centerX: Float,
    private val timeBaseline: Float,
    private val dateBaseline: Float,
    /** Where the time digits are, in screen pixels. */
    val timeRect: RectF,
    /** Everything the time draws (with shadow), in screen pixels. */
    val timeBounds: RectF,
    /** Everything the date draws, in screen pixels; null when the date is hidden. */
    val dateBounds: RectF?,
) {
    /** The big time. With the depth effect it goes behind the subject. */
    fun drawTime(canvas: Canvas) = canvas.drawText(timeText, centerX, timeBaseline, timePaint)

    /** The date line. It always stays in front, so it can be read. */
    fun drawDate(canvas: Canvas) {
        if (dateText != null) canvas.drawText(dateText, centerX, dateBaseline, datePaint)
    }
}

/** Android [RectF] adapters for [DepthPlacement]. */
fun RectF.toBox() = Box(left, top, right, bottom)

fun Box.toRectF() = RectF(left, top, right, bottom)

/**
 * How a photo and the clock are arranged for the depth effect.
 *
 * @property framing how the photo is zoomed and moved.
 * @property clockOffset how far the clock is moved, as a share of the screen height.
 */
data class DepthArrangement(val framing: Framing, val clockOffset: Float) {
    companion object {
        val NONE = DepthArrangement(Framing.NONE, 0f)
    }
}

/**
 * Arranges a photo and the clock so the subject overlaps the time, the way iOS does:
 * first by reframing the photo (the clock stays put); if that can't work, by moving the clock.
 * Used by the wallpaper and the in-app previews alike.
 *
 * @param photo where the photo is drawn without reframing, in screen pixels.
 * @param cropped whether the photo fills the screen (reframing is only done then).
 * @param manualOffset the photo's own clock position, if the user set one.
 * @param subjectAt the photo's subject cut-out, or null if there is none.
 */
fun arrangeDepth(
    clock: ClockSettings,
    use24Hour: Boolean,
    width: Int,
    height: Int,
    photo: RectF,
    cropped: Boolean,
    manualOffset: Float?,
    subjectAt: ((u: Float, v: Float) -> Float)?,
): DepthArrangement {
    if (subjectAt == null || !clock.autoPlace) return DepthArrangement(Framing.NONE, manualOffset ?: 0f)
    val clockOffset = manualOffset ?: 0f
    val base = ClockPainter.layout(clock, use24Hour, width, height, clockOffset)
    if (cropped) {
        DepthPlacement.autoFraming(base.timeRect.toBox(), photo.toBox(), height.toFloat(), subjectAt = subjectAt)
            ?.let { return DepthArrangement(it, clockOffset) }
    }
    if (manualOffset != null) return DepthArrangement(Framing.NONE, manualOffset)
    val shift = DepthPlacement.autoShift(
        time = base.timeRect.toBox(),
        content = photo.toBox(),
        minCenterY = ClockSettings.MIN_POSITION * height,
        maxCenterY = ClockSettings.MAX_POSITION * height,
        subjectAt = subjectAt,
    )
    return DepthArrangement(Framing.NONE, (shift ?: 0f) / height)
}
