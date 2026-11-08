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
            textSize = 100f
        }
        // Size the font so the digits are the requested height, but never wider than the screen.
        val digitBounds = Rect()
        timePaint.getTextBounds("0123456789", 0, 10, digitBounds)
        val targetDigitHeight = clock.size.digitHeight * width
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

        val bounds = RectF(timeRect)
        var dateBaseline = 0f
        if (dateText != null) {
            dateBaseline = timeRect.top - width * 0.035f
            val dateWidth = datePaint.measureText(dateText)
            bounds.union(centerX - dateWidth / 2f, dateBaseline + datePaint.ascent(), centerX + dateWidth / 2f, dateBaseline)
        }
        // Room for descenders and the shadow.
        val pad = width * 0.03f
        bounds.inset(-pad, -pad)

        return ClockLayout(timeText, dateText, timePaint, datePaint, centerX, timeBaseline, dateBaseline, timeRect, bounds)
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
    /** Everything the clock draws, in screen pixels. */
    val bounds: RectF,
) {
    fun draw(canvas: Canvas) {
        if (dateText != null) canvas.drawText(dateText, centerX, dateBaseline, datePaint)
        canvas.drawText(timeText, centerX, timeBaseline, timePaint)
    }
}

/** Rules for when a photo's subject may be drawn in front of the clock. */
object DepthRules {

    /** Above this share of the time digits hidden by the subject, depth is turned off. */
    private const val MAX_COVERED = 0.5f

    /**
     * Whether the subject may cover the clock. Like iOS, the effect is skipped when it would
     * hide too much of the time.
     *
     * @param contentRect where the photo is drawn, in screen pixels (may extend past the screen).
     * @param subjectAt subject strength (0..1) at a point of the photo, in 0..1 coordinates.
     */
    fun allowsDepth(timeRect: RectF, contentRect: RectF, subjectAt: (u: Float, v: Float) -> Float): Boolean {
        if (timeRect.isEmpty || contentRect.isEmpty) return false
        val columns = 24
        val rows = 8
        var covered = 0f
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val x = timeRect.left + timeRect.width() * (column + 0.5f) / columns
                val y = timeRect.top + timeRect.height() * (row + 0.5f) / rows
                val u = (x - contentRect.left) / contentRect.width()
                val v = (y - contentRect.top) / contentRect.height()
                if (u in 0f..1f && v in 0f..1f) covered += subjectAt(u, v)
            }
        }
        return covered / (columns * rows) <= MAX_COVERED
    }
}
