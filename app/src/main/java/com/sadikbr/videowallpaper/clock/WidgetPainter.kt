package com.sadikbr.videowallpaper.clock

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import com.sadikbr.videowallpaper.data.CalendarEvent
import com.sadikbr.videowallpaper.data.WidgetData
import com.sadikbr.videowallpaper.data.WidgetSettings
import com.sadikbr.videowallpaper.data.WidgetSnapshot
import com.sadikbr.videowallpaper.data.WidgetStyle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lays out and draws the widgets: a row of compact items (weather, air quality, battery, next
 * event), optionally on a frosted glass card, and an optional handwritten signature line.
 * Shared by the wallpaper renderer and the in-app previews.
 */
object WidgetPainter {

    private interface Item {
        val width: Float
        fun draw(canvas: Canvas, x: Float, centerY: Float)
    }

    /**
     * @param clockBottom where the clock's time ends (pixels), used when the widgets sit under
     *   the clock; null if there is no clock.
     * @param placeholders show sample values for enabled widgets that have no data yet (for
     *   the editor, so they can be placed).
     * @return null when there is nothing to show.
     */
    fun layout(
        widgets: WidgetSettings,
        snapshot: WidgetSnapshot,
        use24Hour: Boolean,
        color: Int,
        signatureTypeface: Typeface,
        width: Int,
        height: Int,
        clockBottom: Float?,
        placeholders: Boolean = false,
    ): WidgetLayout? {
        val unit = width / 100f
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = unit * 3.9f
        }
        val smallPaint = TextPaint(textPaint).apply {
            alpha = 200
            textSize = unit * 3.1f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        if (widgets.style == WidgetStyle.PLAIN && Color.luminance(color) > 0.5f) {
            val radius = unit * 0.8f
            textPaint.setShadowLayer(radius, 0f, radius * 0.3f, 0x55000000)
            smallPaint.setShadowLayer(radius, 0f, radius * 0.3f, 0x55000000)
            iconPaint.setShadowLayer(radius, 0f, radius * 0.3f, 0x55000000)
        }
        val iconSize = unit * 5f
        val gap = unit * 1.6f

        val items = mutableListOf<Item>()
        val fahrenheit = WidgetData.usesFahrenheit(widgets)
        fun temp(c: Double) = (if (fahrenheit) c * 9 / 5 + 32 else c).roundToInt().toString() + "°"

        val data = if (placeholders) {
            snapshot.copy(
                weather = snapshot.weather ?: com.sadikbr.videowallpaper.data.Weather(21.0, 1, true, 24.0, 15.0),
                airQuality = snapshot.airQuality ?: 42,
                batteryPercent = snapshot.batteryPercent ?: 80,
                nextEvent = snapshot.nextEvent ?: CalendarEvent("Next event", System.currentTimeMillis() + 3_600_000, false),
            )
        } else {
            snapshot
        }
        if (widgets.weather) {
            data.weather?.let { w ->
                items += iconText(
                    icon = { canvas, rect -> WidgetIcons.weather(canvas, rect, w.code, w.isDay, iconPaint) },
                    text = temp(w.temperature),
                    detail = "${temp(w.high)} / ${temp(w.low)}",
                    textPaint, smallPaint, iconSize, gap,
                )
            }
        }
        if (widgets.airQuality) {
            data.airQuality?.let { aqi ->
                items += iconText(
                    icon = { canvas, rect -> WidgetIcons.airQuality(canvas, rect, aqi, iconPaint) },
                    text = "AQI $aqi",
                    detail = null,
                    textPaint, smallPaint, iconSize, gap,
                )
            }
        }
        if (widgets.battery) {
            data.batteryPercent?.let { percent ->
                items += iconText(
                    icon = { canvas, rect -> WidgetIcons.battery(canvas, rect, percent, data.charging, iconPaint) },
                    text = "$percent%",
                    detail = null,
                    textPaint, smallPaint, iconSize, gap,
                )
            }
        }
        if (widgets.calendar) {
            data.nextEvent?.let { event ->
                val title = TextUtils.ellipsize(event.title, textPaint, width * 0.36f, TextUtils.TruncateAt.END).toString()
                items += iconText(
                    icon = { canvas, rect -> WidgetIcons.calendar(canvas, rect, iconPaint) },
                    text = title,
                    detail = eventTime(event, use24Hour),
                    textPaint, smallPaint, iconSize, gap,
                )
            }
        }

        val signature = widgets.signature.trim()
        if (items.isEmpty() && signature.isEmpty()) return null

        // Rows of items, wrapping when they don't fit.
        val maxRowWidth = width * 0.86f
        val spacing = unit * 5f
        val rows = mutableListOf<MutableList<Item>>()
        for (item in items) {
            val row = rows.lastOrNull()
            val rowWidth = row?.let { r -> r.sumOf { it.width.toDouble() }.toFloat() + spacing * (r.size - 1) } ?: 0f
            if (row == null || rowWidth + spacing + item.width > maxRowWidth) rows += mutableListOf(item) else row += item
        }
        val rowHeight = unit * 8f
        val padding = unit * 3.5f
        val cardWidth = (rows.maxOfOrNull { r -> r.sumOf { it.width.toDouble() }.toFloat() + spacing * (r.size - 1) } ?: 0f) +
            padding * 2
        val cardHeight = if (rows.isEmpty()) 0f else rows.size * rowHeight + padding

        val signaturePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            typeface = signatureTypeface
            textSize = unit * 9f * widgets.signatureSize.coerceIn(WidgetSettings.MIN_SIGNATURE_SIZE, WidgetSettings.MAX_SIGNATURE_SIZE)
            textAlign = Paint.Align.CENTER
            if (Color.luminance(color) > 0.5f) setShadowLayer(unit * 0.8f, 0f, unit * 0.3f, 0x55000000)
        }
        val signatureText = if (signature.isEmpty()) {
            null
        } else {
            TextUtils.ellipsize(signature, signaturePaint, width * 0.9f, TextUtils.TruncateAt.END).toString()
        }
        // Room for the line, including tall Arabic letters and descenders.
        val signatureHeight = if (signatureText != null) (signaturePaint.descent() - signaturePaint.ascent()) * 1.15f else 0f

        val top = widgets.position?.let { it * height }
            ?: ((clockBottom ?: (height * 0.3f)) + unit * 6f)
        val centerX = width / 2f
        val cardRect = RectF(centerX - cardWidth / 2f, top, centerX + cardWidth / 2f, top + cardHeight)
        val bounds = RectF(
            minOf(cardRect.left, if (signatureText != null) centerX - signaturePaint.measureText(signatureText) / 2f else cardRect.left),
            top,
            maxOf(cardRect.right, if (signatureText != null) centerX + signaturePaint.measureText(signatureText) / 2f else cardRect.right),
            top + cardHeight + signatureHeight,
        ).apply { inset(-unit * 2f, -unit * 2f) }

        return WidgetLayout(bounds, top) { canvas ->
            if (rows.isNotEmpty()) {
                if (widgets.style == WidgetStyle.GLASS) {
                    val radius = unit * 5f
                    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(46, 255, 255, 255) }
                    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = unit * 0.25f
                        this.color = Color.argb(70, 255, 255, 255)
                    }
                    canvas.drawRoundRect(cardRect, radius, radius, fill)
                    canvas.drawRoundRect(cardRect, radius, radius, stroke)
                }
                rows.forEachIndexed { index, row ->
                    val rowWidth = row.sumOf { it.width.toDouble() }.toFloat() + spacing * (row.size - 1)
                    var x = centerX - rowWidth / 2f
                    val y = cardRect.top + padding / 2f + rowHeight * (index + 0.5f)
                    for (item in row) {
                        item.draw(canvas, x, y)
                        x += item.width + spacing
                    }
                }
            }
            if (signatureText != null) {
                val baseline = cardRect.bottom + (signatureHeight - (signaturePaint.descent() - signaturePaint.ascent())) / 2f - signaturePaint.ascent()
                canvas.drawText(signatureText, centerX, baseline, signaturePaint)
            }
        }
    }

    private fun iconText(
        icon: (Canvas, RectF) -> Unit,
        text: String,
        detail: String?,
        textPaint: TextPaint,
        smallPaint: TextPaint,
        iconSize: Float,
        gap: Float,
    ): Item {
        val textWidth = textPaint.measureText(text)
        val detailWidth = detail?.let { smallPaint.measureText(it) + gap * 0.8f } ?: 0f
        return object : Item {
            override val width = iconSize + gap + textWidth + detailWidth
            override fun draw(canvas: Canvas, x: Float, centerY: Float) {
                icon(canvas, RectF(x, centerY - iconSize / 2f, x + iconSize, centerY + iconSize / 2f))
                val textX = x + iconSize + gap
                val baseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2f
                canvas.drawText(text, textX, baseline, textPaint)
                if (detail != null) canvas.drawText(detail, textX + textWidth + gap * 0.8f, baseline, smallPaint)
            }
        }
    }

    private fun eventTime(event: CalendarEvent, use24Hour: Boolean): String {
        val locale = Locale.getDefault()
        val today = Calendar.getInstance()
        val day = Calendar.getInstance().apply { timeInMillis = event.begin }
        val isToday = today.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR) &&
            today.get(Calendar.YEAR) == day.get(Calendar.YEAR)
        val dayLabel = if (isToday) "" else SimpleDateFormat("EEE ", locale).format(Date(event.begin))
        if (event.allDay) return if (isToday) "Today" else dayLabel.trim()
        val time = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, if (use24Hour) "Hm" else "hm"), locale)
            .format(Date(event.begin))
        return dayLabel + time
    }
}

class WidgetLayout internal constructor(
    /** Everything the widgets draw, in screen pixels. */
    val bounds: RectF,
    /** Where the widgets start (the value [WidgetSettings.position] describes), in pixels. */
    val top: Float,
    private val drawer: (Canvas) -> Unit,
) {
    fun draw(canvas: Canvas) = drawer(canvas)
}

/** Small vector icons for the widgets, drawn with the given paint's colour. */
internal object WidgetIcons {

    fun weather(canvas: Canvas, r: RectF, code: Int, isDay: Boolean, paint: Paint) {
        when (code) {
            0 -> if (isDay) sun(canvas, r, paint) else moon(canvas, r, paint)
            1, 2 -> {
                // A little sun or moon peeking behind a cloud.
                val small = RectF(r.left + r.width() * 0.35f, r.top, r.right, r.top + r.height() * 0.65f)
                if (isDay) sun(canvas, small, paint) else moon(canvas, small, paint)
                cloud(canvas, RectF(r.left, r.top + r.height() * 0.3f, r.right - r.width() * 0.1f, r.bottom), paint)
            }
            3 -> cloud(canvas, r, paint)
            45, 48 -> fog(canvas, r, paint)
            in 71..77, 85, 86 -> {
                cloud(canvas, RectF(r.left, r.top, r.right, r.top + r.height() * 0.68f), paint)
                for (i in 0..2) canvas.drawCircle(r.left + r.width() * (0.25f + 0.25f * i), r.bottom - r.height() * 0.12f, r.width() * 0.07f, paint)
            }
            in 95..99 -> {
                cloud(canvas, RectF(r.left, r.top, r.right, r.top + r.height() * 0.68f), paint)
                val bolt = Path().apply {
                    moveTo(r.left + r.width() * 0.55f, r.top + r.height() * 0.6f)
                    lineTo(r.left + r.width() * 0.38f, r.top + r.height() * 0.85f)
                    lineTo(r.left + r.width() * 0.52f, r.top + r.height() * 0.85f)
                    lineTo(r.left + r.width() * 0.42f, r.bottom)
                    lineTo(r.left + r.width() * 0.68f, r.top + r.height() * 0.75f)
                    lineTo(r.left + r.width() * 0.54f, r.top + r.height() * 0.75f)
                    close()
                }
                canvas.drawPath(bolt, paint)
            }
            else -> if (code in 51..67 || code in 80..82) rain(canvas, r, paint) else cloud(canvas, r, paint)
        }
    }

    private fun sun(canvas: Canvas, r: RectF, paint: Paint) {
        val cx = r.centerX()
        val cy = r.centerY()
        val radius = minOf(r.width(), r.height()) * 0.22f
        canvas.drawCircle(cx, cy, radius, paint)
        val stroke = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.32f
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0 until 8) {
            val angle = Math.PI / 4 * i
            val inner = radius * 1.45f
            val outer = radius * 2.05f
            canvas.drawLine(
                cx + (cos(angle) * inner).toFloat(), cy + (sin(angle) * inner).toFloat(),
                cx + (cos(angle) * outer).toFloat(), cy + (sin(angle) * outer).toFloat(), stroke,
            )
        }
    }

    private fun moon(canvas: Canvas, r: RectF, paint: Paint) {
        val radius = minOf(r.width(), r.height()) * 0.38f
        val moon = Path().apply {
            addCircle(r.centerX(), r.centerY(), radius, Path.Direction.CW)
            val bite = Path().apply { addCircle(r.centerX() + radius * 0.55f, r.centerY() - radius * 0.35f, radius * 0.85f, Path.Direction.CW) }
            op(bite, Path.Op.DIFFERENCE)
        }
        canvas.drawPath(moon, paint)
    }

    private fun cloud(canvas: Canvas, r: RectF, paint: Paint) {
        val w = r.width()
        val h = r.height()
        val path = Path().apply {
            addCircle(r.left + w * 0.33f, r.top + h * 0.58f, h * 0.22f, Path.Direction.CW)
            addCircle(r.left + w * 0.55f, r.top + h * 0.45f, h * 0.3f, Path.Direction.CW)
            addCircle(r.left + w * 0.75f, r.top + h * 0.6f, h * 0.2f, Path.Direction.CW)
            addRoundRect(RectF(r.left + w * 0.12f, r.top + h * 0.55f, r.right - w * 0.06f, r.top + h * 0.8f), h * 0.12f, h * 0.12f, Path.Direction.CW)
        }
        canvas.drawPath(path, paint)
    }

    private fun rain(canvas: Canvas, r: RectF, paint: Paint) {
        cloud(canvas, RectF(r.left, r.top, r.right, r.top + r.height() * 0.68f), paint)
        val stroke = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = r.width() * 0.07f
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0..2) {
            val x = r.left + r.width() * (0.3f + 0.22f * i)
            canvas.drawLine(x, r.top + r.height() * 0.74f, x - r.width() * 0.06f, r.bottom - r.height() * 0.04f, stroke)
        }
    }

    private fun fog(canvas: Canvas, r: RectF, paint: Paint) {
        val stroke = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = r.height() * 0.09f
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0..2) {
            val y = r.top + r.height() * (0.3f + 0.2f * i)
            canvas.drawLine(r.left + r.width() * (0.12f + 0.06f * i), y, r.right - r.width() * (0.12f + 0.04f * (2 - i)), y, stroke)
        }
    }

    fun battery(canvas: Canvas, r: RectF, percent: Int, charging: Boolean, paint: Paint) {
        val body = RectF(r.left + r.width() * 0.05f, r.top + r.height() * 0.28f, r.right - r.width() * 0.15f, r.bottom - r.height() * 0.28f)
        val stroke = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = r.height() * 0.07f
        }
        val radius = body.height() * 0.22f
        canvas.drawRoundRect(body, radius, radius, stroke)
        canvas.drawRoundRect(
            RectF(body.right + r.width() * 0.03f, r.centerY() - body.height() * 0.2f, body.right + r.width() * 0.09f, r.centerY() + body.height() * 0.2f),
            radius * 0.5f, radius * 0.5f, paint,
        )
        val inset = stroke.strokeWidth * 1.2f
        val fillWidth = (body.width() - inset * 2) * (percent.coerceIn(0, 100) / 100f)
        val fill = Paint(paint).apply { if (percent <= 15 && !charging) color = Color.rgb(255, 99, 88) }
        canvas.drawRoundRect(RectF(body.left + inset, body.top + inset, body.left + inset + fillWidth, body.bottom - inset), radius * 0.5f, radius * 0.5f, fill)
        if (charging) {
            val bolt = Path().apply {
                moveTo(body.centerX() + body.width() * 0.08f, body.top - body.height() * 0.15f)
                lineTo(body.centerX() - body.width() * 0.16f, body.centerY() + body.height() * 0.08f)
                lineTo(body.centerX() - body.width() * 0.01f, body.centerY() + body.height() * 0.08f)
                lineTo(body.centerX() - body.width() * 0.08f, body.bottom + body.height() * 0.15f)
                lineTo(body.centerX() + body.width() * 0.16f, body.centerY() - body.height() * 0.08f)
                lineTo(body.centerX() + body.width() * 0.01f, body.centerY() - body.height() * 0.08f)
                close()
            }
            val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 0, 0, 0) }
            canvas.drawPath(bolt, cut)
        }
    }

    fun calendar(canvas: Canvas, r: RectF, paint: Paint) {
        val body = RectF(r.left + r.width() * 0.12f, r.top + r.height() * 0.16f, r.right - r.width() * 0.12f, r.bottom - r.height() * 0.08f)
        val stroke = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = r.height() * 0.07f
        }
        val radius = body.width() * 0.16f
        canvas.drawRoundRect(body, radius, radius, stroke)
        canvas.drawRect(RectF(body.left, body.top, body.right, body.top + body.height() * 0.28f), paint)
        val dot = r.width() * 0.06f
        for (row in 0..1) for (column in 0..2) {
            canvas.drawCircle(
                body.left + body.width() * (0.27f + 0.23f * column),
                body.top + body.height() * (0.55f + 0.24f * row),
                dot, paint,
            )
        }
    }

    fun airQuality(canvas: Canvas, r: RectF, aqi: Int, paint: Paint) {
        // A dot in the US AQI colour for the level, with a ring in the widget colour.
        val level = when {
            aqi <= 50 -> Color.rgb(94, 214, 120)
            aqi <= 100 -> Color.rgb(250, 214, 92)
            aqi <= 150 -> Color.rgb(255, 160, 80)
            aqi <= 200 -> Color.rgb(255, 99, 88)
            aqi <= 300 -> Color.rgb(170, 100, 220)
            else -> Color.rgb(150, 60, 90)
        }
        val radius = minOf(r.width(), r.height()) * 0.36f
        val ring = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.22f
        }
        canvas.drawCircle(r.centerX(), r.centerY(), radius, ring)
        canvas.drawCircle(r.centerX(), r.centerY(), radius * 0.62f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = level })
    }
}
