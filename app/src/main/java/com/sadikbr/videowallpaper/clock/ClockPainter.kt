package com.sadikbr.videowallpaper.clock

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.LightingColorFilter
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.floor
import kotlin.math.roundToInt
import android.graphics.Shader
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import com.sadikbr.videowallpaper.data.ClockFont
import com.sadikbr.videowallpaper.data.ClockSettings
import com.sadikbr.videowallpaper.data.SignatureFont
import com.sadikbr.videowallpaper.data.WidgetSettings
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
     * @param typeface the clock font, from [ClockFonts].
     * @param offset per-photo adjustment of the vertical position (share of [height]).
     */
    fun layout(
        clock: ClockSettings,
        use24Hour: Boolean,
        typeface: Typeface,
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

        val font = clock.font
        val stretch = (clock.stretch * font.heightScale).coerceAtLeast(0.1f)
        val opacity = clock.opacity.coerceIn(0f, 1f)
        val hasShadow = Color.luminance(clock.color) > 0.5f && opacity > 0.8f
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = clock.color
            alpha = (opacity * 255).toInt()
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
            letterSpacing = -0.02f
            textScaleX = font.widthScale
            textSize = 100f
        }
        // Size the font by width, like iOS: "00:00" spans the chosen share of the screen, whatever
        // the font. Stretch then makes the digits taller.
        val reference = timePaint.measureText("00:00").coerceAtLeast(1f)
        timePaint.textSize = 100f * clock.width * width / reference
        val digitBounds = Rect()
        timePaint.getTextBounds("0123456789", 0, 10, digitBounds)
        val digitHeight = digitBounds.height().toFloat()
        val stretchedHeight = digitHeight * stretch

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = clock.color
            alpha = (maxOf(opacity, 0.85f) * 255).toInt()
            this.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            textSize = width * 0.045f
        }
        if (hasShadow) {
            // A faint shadow keeps light text readable on bright photos.
            val radius = width * 0.012f
            timePaint.setShadowLayer(radius, 0f, radius * 0.3f, 0x33000000)
        }
        if (Color.luminance(clock.color) > 0.5f) {
            val radius = width * 0.01f
            datePaint.setShadowLayer(radius, 0f, radius * 0.3f, 0x33000000)
        }
        if (clock.fade > 0f) {
            // Fade towards the bottom of the digits (in unstretched text coordinates).
            val top = Color.argb((opacity * 255).toInt(), Color.red(clock.color), Color.green(clock.color), Color.blue(clock.color))
            val bottom = Color.argb((opacity * (1f - clock.fade) * 255).toInt(), Color.red(clock.color), Color.green(clock.color), Color.blue(clock.color))
            timePaint.shader = null // set per draw, as it depends on the baseline
            timePaint.color = top
            timePaint.alpha = 255
            timePaint.setShadowLayer(0f, 0f, 0f, 0)
            return finishLayout(
                timeText, dateText, timePaint, datePaint, width, height, clock, offset,
                digitHeight, stretchedHeight, stretch, fade = top to bottom,
            )
        }
        return finishLayout(
            timeText, dateText, timePaint, datePaint, width, height, clock, offset,
            digitHeight, stretchedHeight, stretch, fade = null,
        )
    }

    private fun finishLayout(
        timeText: String,
        dateText: String?,
        timePaint: Paint,
        datePaint: Paint,
        width: Int,
        height: Int,
        clock: ClockSettings,
        offset: Float,
        digitHeight: Float,
        stretchedHeight: Float,
        stretch: Float,
        fade: Pair<Int, Int>?,
    ): ClockLayout {
        val position = (clock.position + offset).coerceIn(0.05f, 0.95f)
        val centerX = width / 2f
        val timeBaseline = position * height + stretchedHeight
        val timeWidth = timePaint.measureText(timeText)
        val timeRect = RectF(
            centerX - timeWidth / 2f,
            timeBaseline - stretchedHeight,
            centerX + timeWidth / 2f,
            timeBaseline,
        )
        if (fade != null) {
            timePaint.shader = LinearGradient(
                0f, timeBaseline - digitHeight, 0f, timeBaseline,
                fade.first, fade.second, Shader.TileMode.CLAMP,
            )
        }

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
            timeText, dateText, timePaint, datePaint, centerX, timeBaseline, dateBaseline, stretch,
            timeRect, timeBounds, dateBounds,
            waterline = clock.waterline?.coerceIn(ClockSettings.MIN_WATERLINE, ClockSettings.MAX_WATERLINE),
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
    private val stretch: Float,
    /** Where the time digits are, in screen pixels. */
    val timeRect: RectF,
    /** Everything the time draws (with shadow), in screen pixels. */
    val timeBounds: RectF,
    /** Everything the date draws, in screen pixels; null when the date is hidden. */
    val dateBounds: RectF?,
    private val waterline: Float? = null,
) {
    /** The digits seen through water: tinted light blue, more see-through. */
    private val underwaterPaint by lazy {
        Paint(timePaint).apply {
            alpha = (alpha * 0.55f).toInt()
            colorFilter = LightingColorFilter(0xFFB8E2FF.toInt(), 0x00081420)
        }
    }

    /**
     * The big time, stretched upwards from its baseline. With depth it goes behind the subject.
     * With a [waterline], the part under the water ripples sideways, sinks a little and fades,
     * and the surface shimmers where it crosses the digits.
     */
    fun drawTime(canvas: Canvas) {
        val line = waterline
        if (line == null) {
            drawDigits(canvas, timePaint)
            return
        }
        val height = timeRect.height()
        val waterY = timeRect.top + height * line

        // Above the water: as normal.
        canvas.save()
        canvas.clipRect(timeBounds.left, timeBounds.top - height, timeBounds.right, floor(waterY))
        drawDigits(canvas, timePaint)
        canvas.restore()

        // Under the water: thin bands, each pushed sideways by a ripple (refraction), the whole
        // lower part sunk a little.
        val amplitude = timeRect.width() * 0.0045f
        val wavelength = height * 0.3f
        val sink = height * 0.02f
        // Whole-pixel bands that meet exactly, so no seams show between them.
        val band = (height / 240f).roundToInt().coerceAtLeast(1)
        var y = floor(waterY)
        val bottom = timeBounds.bottom + sink
        while (y < bottom) {
            val depth = ((y - waterY) / height).coerceIn(0f, 1f)
            val phase = (y - waterY) / wavelength * 2f * PI.toFloat()
            val dx = amplitude * (0.5f + depth) * (sin(phase) + 0.35f * sin(phase * 2.3f + 1f))
            canvas.save()
            canvas.clipRect(timeBounds.left, y, timeBounds.right, y + band)
            canvas.translate(dx, sink)
            drawDigits(canvas, underwaterPaint)
            canvas.restore()
            y += band
        }

        // The surface: a soft glow and a thin bright wavy line across the digits.
        val left = timeRect.left - timeRect.width() * 0.04f
        val right = timeRect.right + timeRect.width() * 0.04f
        val surface = Path()
        val steps = 48
        for (i in 0..steps) {
            val x = left + (right - left) * i / steps
            val wy = waterY + height * 0.012f * sin(i / steps.toFloat() * 7f * PI.toFloat())
            if (i == 0) surface.moveTo(x, wy) else surface.lineTo(x, wy)
        }
        val fadeEnds = LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT),
            floatArrayOf(0f, 0.15f, 0.85f, 1f), Shader.TileMode.CLAMP,
        )
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = height * 0.06f
            strokeCap = Paint.Cap.ROUND
            shader = fadeEnds
            alpha = 40
        }
        canvas.drawPath(surface, glow)
        val shine = Paint(glow).apply {
            strokeWidth = (height * 0.008f).coerceAtLeast(1.5f)
            alpha = 150
        }
        canvas.drawPath(surface, shine)
    }

    private fun drawDigits(canvas: Canvas, paint: Paint) {
        canvas.save()
        canvas.scale(1f, stretch, centerX, timeBaseline)
        canvas.drawText(timeText, centerX, timeBaseline, paint)
        canvas.restore()
    }

    /** The date line. It always stays in front, so it can be read. */
    fun drawDate(canvas: Canvas) {
        if (dateText != null) canvas.drawText(dateText, centerX, dateBaseline, datePaint)
    }
}

/** Loads the clock typefaces once per process. */
object ClockFonts {
    private val cache = HashMap<ClockFont, Typeface>()
    private val signatures = HashMap<SignatureFont, Typeface>()

    /** The font for the widgets' signature line (chosen, or picked from the text). */
    @Synchronized
    fun signature(context: Context, widgets: WidgetSettings): Typeface {
        val font = widgets.resolvedSignatureFont
        return signatures.getOrPut(font) {
            runCatching { Typeface.createFromAsset(context.applicationContext.assets, font.asset) }
                .getOrDefault(Typeface.SERIF)
        }
    }

    @Synchronized
    fun typeface(context: Context, font: ClockFont): Typeface = cache.getOrPut(font) {
        val asset = font.asset
        when {
            asset != null -> runCatching { Typeface.createFromAsset(context.applicationContext.assets, asset) }
                .getOrDefault(Typeface.DEFAULT)
            else -> Typeface.create(font.family, Typeface.NORMAL)
        }
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
 * @param manualFraming the photo's own zoom and position (pixels), if the user set one.
 * @param subjectAt the photo's subject cut-out, or null if there is none.
 */
fun arrangeDepth(
    clock: ClockSettings,
    use24Hour: Boolean,
    typeface: Typeface,
    width: Int,
    height: Int,
    photo: RectF,
    cropped: Boolean,
    manualOffset: Float?,
    manualFraming: Framing? = null,
    subjectAt: ((u: Float, v: Float) -> Float)?,
): DepthArrangement {
    if (manualFraming != null) return DepthArrangement(manualFraming, manualOffset ?: 0f)
    if (subjectAt == null || !clock.autoPlace) return DepthArrangement(Framing.NONE, manualOffset ?: 0f)
    val clockOffset = manualOffset ?: 0f
    val base = ClockPainter.layout(clock, use24Hour, typeface, width, height, clockOffset)
    if (cropped) {
        DepthPlacement.autoFraming(base.timeRect.toBox(), photo.toBox(), height.toFloat(), subjectAt = subjectAt)
            ?.let { return DepthArrangement(it, clockOffset) }
    }
    if (manualOffset != null) return DepthArrangement(Framing.NONE, manualOffset)
    val shift = DepthPlacement.autoShift(
        time = base.timeRect.toBox(),
        content = photo.toBox(),
        // Positions are the top of the time; the search works with its centre.
        minCenterY = ClockSettings.MIN_POSITION * height + base.timeRect.height() / 2f,
        maxCenterY = ClockSettings.MAX_POSITION * height + base.timeRect.height() / 2f,
        subjectAt = subjectAt,
    )
    return DepthArrangement(Framing.NONE, (shift ?: 0f) / height)
}
