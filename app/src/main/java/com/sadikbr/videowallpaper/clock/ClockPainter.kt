package com.sadikbr.videowallpaper.clock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.ceil
import android.graphics.LinearGradient
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
import androidx.core.graphics.ColorUtils
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
        if (clock.waterline != null) {
            // Room for the underwater digits' shadow, sink and ripple (see renderUnderwater).
            val extra = stretchedHeight * 0.13f + timeRect.width() * 0.006f
            timeBounds.left -= extra
            timeBounds.right += extra
            timeBounds.bottom += extra
            // Fully under water: the shadow also spreads above the digits.
            if (clock.waterline <= 0f) timeBounds.top -= extra
        }
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
            waterGap = clock.waterGap.coerceIn(0f, ClockSettings.MAX_WATER_GAP),
            color = clock.color,
            opacity = clock.opacity.coerceIn(0f, 1f),
            // Everything the underwater picture depends on (the time, the look and the geometry).
            // (Not the position: the picture is placed relative to the time, so moving it is free.)
            waterKey = listOf(timeText, clock.copy(position = 0f), width, height),
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
    private val waterGap: Float = 0f,
    private val waterKey: Any? = null,
    private val color: Int = Color.WHITE,
    private val opacity: Float = 1f,
) {
    /**
     * The big time, stretched upwards from its baseline. With depth it goes behind the subject.
     * With a [waterline], the part below it looks seen through water (see [UnderwaterDigits]).
     */
    fun drawTime(canvas: Canvas) {
        val line = waterline
        if (line == null) {
            drawDigits(canvas, timePaint)
            return
        }
        val waterY = floor(timeRect.top + timeRect.height() * line)

        // Above the water: as normal.
        canvas.save()
        canvas.clipRect(timeBounds.left, timeBounds.top - timeRect.height(), timeBounds.right, waterY)
        drawDigits(canvas, timePaint)
        canvas.restore()

        // Under the water: a picture made once (per minute and look) and reused for every frame.
        val underwater = UnderwaterDigits.get(waterKey ?: this) { renderUnderwater(waterY, line <= 0f) } ?: return
        canvas.drawBitmap(underwater.bitmap, timeRect.left + underwater.left, waterY + underwater.top, null)
    }

    /**
     * The digits as seen under water, made to stay readable over any water, bright or dark.
     * Glass under water is seen by its edges more than its fill, so they are drawn in three
     * layers: a soft dark shadow (stands out on bright water), a see-through fill in the clock's
     * colour tinted by the water, and a thin bright outline (stands out on dark water). They are
     * softly blurred, sunk a little (so they don't line up with the part above the surface, like
     * refraction), ripple sideways, and fade a little with depth.
     */
    private fun renderUnderwater(waterY: Float, fullyUnder: Boolean): UnderwaterDigits.Picture? {
        val height = timeRect.height()
        val amplitude = timeRect.width() * 0.0045f
        val sink = height * 0.025f
        val shadowBlur = height * 0.035f
        val pad = ceil(amplitude + shadowBlur * 2f).toInt() + 2
        val left = floor(timeBounds.left) - pad
        val width = ceil(timeBounds.right - timeBounds.left).toInt() + 2 * pad
        // With no surface crossing the digits, the picture starts above them, so the shadow
        // isn't cut off; otherwise it starts at the surface.
        val startY = if (fullyUnder) floor(timeBounds.top) else waterY
        val rows = ceil(timeBounds.bottom + sink + shadowBlur * 2f - startY).toInt()
        if (width <= 0 || rows <= 0) return null

        // Readable even for the faintest glass looks.
        val strength = 0.75f + 0.25f * opacity
        val shadow = Paint(timePaint).apply {
            shader = null
            clearShadowLayer()
            colorFilter = null
            color = 0xFF04182C.toInt()
            alpha = (0.5f * strength * 255).toInt()
            maskFilter = BlurMaskFilter(shadowBlur, BlurMaskFilter.Blur.NORMAL)
        }
        val fill = Paint(timePaint).apply {
            shader = null
            clearShadowLayer()
            colorFilter = null
            color = ColorUtils.blendARGB(this@ClockLayout.color, 0xFF8FD3FF.toInt(), 0.35f)
            alpha = (0.55f * strength * 255).toInt()
            maskFilter = BlurMaskFilter((height * 0.004f).coerceAtLeast(0.75f), BlurMaskFilter.Blur.NORMAL)
        }
        val outline = Paint(timePaint).apply {
            shader = null
            clearShadowLayer()
            colorFilter = null
            style = Paint.Style.STROKE
            // The digits are stretched vertically, which thickens the outline's horizontal parts.
            strokeWidth = (height * 0.012f / stretch).coerceAtLeast(1.2f)
            color = ColorUtils.blendARGB(this@ClockLayout.color, Color.WHITE, 0.6f)
            alpha = (0.85f * strength * 255).toInt()
        }

        val flat = Bitmap.createBitmap(width, rows, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            save()
            translate(-left, -startY + sink)
            save()
            translate(0f, height * 0.015f)
            drawDigits(this, shadow)
            restore()
            drawDigits(this, fill)
            drawDigits(this, outline)
            restore()
            // Hidden between the waterline and where the underwater view begins (the gap), then
            // fading a little with depth.
            // (Fully under water with no gap, nothing is hidden, not even the shadow above.)
            val gap = if (fullyUnder && waterGap <= 0f) 0f else (waterY - startY) + height * waterGap
            if (gap > 0f) drawRect(0f, 0f, width.toFloat(), gap, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
            drawRect(
                0f, gap, width.toFloat(), rows.toFloat(),
                Paint().apply {
                    shader = LinearGradient(
                        0f, gap, 0f, rows.toFloat(),
                        intArrayOf(0xFF000000.toInt(), 0xE0000000.toInt(), 0x80000000.toInt()),
                        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
                    )
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                },
            )
        }

        // Ripples: each row pushed sideways a little, more so deeper down.
        val rippled = Bitmap.createBitmap(width, rows, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(rippled)
        val rowPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        val wavelength = height * 0.22f
        val src = Rect()
        val dst = RectF()
        val step = 2
        var y = 0
        while (y < rows) {
            val depth = (y / height).coerceIn(0f, 1f)
            val phase = y / wavelength * 2f * PI.toFloat()
            val dx = amplitude * (0.4f + depth) * (sin(phase) + 0.4f * sin(phase * 2.7f + 1.3f))
            val bottom = minOf(y + step, rows)
            src.set(0, y, width, bottom)
            dst.set(dx, y.toFloat(), dx + width, bottom.toFloat())
            canvas.drawBitmap(flat, src, dst, rowPaint)
            y = bottom
        }
        flat.recycle()
        return UnderwaterDigits.Picture(rippled, left - timeRect.left, startY - waterY)
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

/** The latest underwater clock pictures, shared by the wallpaper and the in-app previews. */
private object UnderwaterDigits {
    /** [left] is relative to the left of the time, [top] to the water surface. */
    class Picture(val bitmap: Bitmap, val left: Float, val top: Float)

    private const val SIZE = 4
    private val cache = LinkedHashMap<Any, Picture?>()

    @Synchronized
    fun get(key: Any, create: () -> Picture?): Picture? {
        cache.remove(key)?.let { cache[key] = it; return it }
        val picture = create()
        cache[key] = picture
        // Older pictures may still be on screen in a preview being drawn, so they are not
        // recycled; the garbage collector frees them.
        while (cache.size > SIZE) cache.remove(cache.keys.first())
        return picture
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
