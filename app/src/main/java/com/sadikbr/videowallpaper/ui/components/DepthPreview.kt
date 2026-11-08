package com.sadikbr.videowallpaper.ui.components

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.sadikbr.videowallpaper.clock.ClockPainter
import com.sadikbr.videowallpaper.clock.DepthPlacement
import com.sadikbr.videowallpaper.clock.ClockFonts
import com.sadikbr.videowallpaper.clock.DepthArrangement
import com.sadikbr.videowallpaper.clock.Framing
import com.sadikbr.videowallpaper.clock.arrangeDepth
import com.sadikbr.videowallpaper.clock.toRectF
import com.sadikbr.videowallpaper.clock.toBox
import com.sadikbr.videowallpaper.data.ClockSettings
import com.sadikbr.videowallpaper.data.DepthMasks
import com.sadikbr.videowallpaper.data.PhotoFraming
import com.sadikbr.videowallpaper.data.WidgetSettings
import com.sadikbr.videowallpaper.data.WidgetSnapshot
import com.sadikbr.videowallpaper.clock.WidgetLayout
import com.sadikbr.videowallpaper.clock.WidgetPainter
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import com.sadikbr.videowallpaper.data.ScaleMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Where the preview drew things, in its own pixels; used by the editor for touch handling. */
data class PreviewGeometry(
    val time: RectF,
    val date: RectF?,
    val photo: RectF?,
    val size: Size,
    /** Where the widgets are, and the top their position refers to; null if none. */
    val widgets: RectF? = null,
    val widgetsTop: Float = 0f,
)

/**
 * A picture of what the wallpaper shows: the photo, the time, the photo's subject in front of
 * the time (when allowed) and the date. Uses the same arrangement rules and drawing code as
 * the wallpaper, including reframing the photo to bring its subject up to the clock.
 *
 * @param mask alpha-only cut-out of the photo's subject.
 * @param manualOffset the photo's own clock position, or null to arrange it automatically.
 * @param manualFraming the photo's own zoom and position, or null for automatic.
 * @param onGeometry receives where the clock and photo were drawn.
 */
@Composable
fun DepthPreview(
    photo: Bitmap?,
    mask: Bitmap?,
    clock: ClockSettings,
    use24Hour: Boolean,
    manualOffset: Float?,
    scaleMode: ScaleMode,
    modifier: Modifier = Modifier,
    manualFraming: PhotoFraming? = null,
    widgets: WidgetSettings? = null,
    widgetSnapshot: WidgetSnapshot = WidgetSnapshot(),
    widgetPlaceholders: Boolean = false,
    onGeometry: ((PreviewGeometry) -> Unit)? = null,
) {
    val context = LocalContext.current
    val photoImage = remember(photo) { photo?.asImageBitmap() }
    val maskImage = remember(mask) { mask?.asImageBitmap() }
    val typeface = remember(clock.font) { ClockFonts.typeface(context, clock.font) }
    val minute by rememberCurrentMinute()

    Canvas(modifier) {
        minute // Redraw when the minute changes.
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas
        clipRect {
            val fitted = photo?.let { fitRect(it.width, it.height, width, height, scaleMode == ScaleMode.CROP) }
            val subjectAt = mask?.let { m -> { u: Float, v: Float -> alphaAt(m, u, v) } }
            val framing = manualFraming?.let { Framing(it.zoom, it.shift * height, it.shiftX * width) }
            val arrangement = when {
                clock.enabled && fitted != null -> arrangeDepth(
                    clock, use24Hour, typeface, width.toInt(), height.toInt(), fitted,
                    cropped = scaleMode == ScaleMode.CROP,
                    manualOffset = manualOffset,
                    manualFraming = framing,
                    subjectAt = subjectAt,
                )
                framing != null -> DepthArrangement(framing, manualOffset ?: 0f)
                else -> DepthArrangement(Framing.NONE, manualOffset ?: 0f)
            }
            val target = fitted?.toBox()?.framed(arrangement.framing.zoom, arrangement.framing.shiftY, arrangement.framing.shiftX)?.toRectF()
            val dstOffset = target?.let { IntOffset(it.left.roundToInt(), it.top.roundToInt()) }
            val dstSize = target?.let { IntSize(it.width().roundToInt(), it.height().roundToInt()) }
            if (photoImage != null && dstOffset != null && dstSize != null) {
                drawImage(photoImage, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.High)
            }
            fun drawWidgets(clockBottom: Float?): WidgetLayout? {
                if (widgets == null || !widgets.anyEnabled) return null
                val layout = WidgetPainter.layout(
                    widgets, widgetSnapshot, use24Hour,
                    if (clock.enabled) clock.color else android.graphics.Color.WHITE,
                    ClockFonts.signature(context), width.toInt(), height.toInt(), clockBottom, widgetPlaceholders,
                ) ?: return null
                drawIntoCanvas { layout.draw(it.nativeCanvas) }
                return layout
            }
            if (!clock.enabled) {
                val w = drawWidgets(null)
                onGeometry?.invoke(PreviewGeometry(RectF(), null, target, size, w?.bounds, w?.top ?: 0f))
                return@clipRect
            }

            val layout = ClockPainter.layout(clock, use24Hour, typeface, width.toInt(), height.toInt(), arrangement.clockOffset)
            drawIntoCanvas { layout.drawTime(it.nativeCanvas) }

            val depth = photoImage != null && maskImage != null && subjectAt != null && target != null &&
                DepthPlacement.allowsDepth(layout.timeRect.toBox(), target.toBox(), subjectAt)
            if (depth) {
                // The subject in front of the time: the photo, kept only where the cut-out is opaque.
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(0f, 0f, width, height), Paint())
                    drawImage(photoImage!!, dstOffset = dstOffset!!, dstSize = dstSize!!, filterQuality = FilterQuality.High)
                    drawImage(
                        maskImage!!,
                        srcSize = IntSize(maskImage.width, maskImage.height),
                        dstOffset = dstOffset,
                        dstSize = dstSize,
                        blendMode = BlendMode.DstIn,
                    )
                    canvas.restore()
                }
            }
            drawIntoCanvas { layout.drawDate(it.nativeCanvas) }
            val w = drawWidgets(layout.timeRect.bottom)
            onGeometry?.invoke(PreviewGeometry(layout.timeRect, layout.dateBounds, target, size, w?.bounds, w?.top ?: 0f))
        }
    }
}

/** The alpha-only cut-out of [uri], once [ready]; null otherwise. */
@Composable
fun rememberAlphaMask(uri: String?, ready: Boolean): State<Bitmap?> {
    val context = LocalContext.current
    val state = remember(uri, ready) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, ready) {
        if (uri != null && ready) {
            state.value = withContext(Dispatchers.IO) { DepthMasks.loadAlphaMask(context, uri) }
        }
    }
    return state
}

@Composable
private fun rememberCurrentMinute(): State<Long> {
    val minute = remember { mutableLongStateOf(System.currentTimeMillis() / 60_000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000 + 20)
            minute.longValue = System.currentTimeMillis() / 60_000
        }
    }
    return minute
}

internal fun alphaAt(mask: Bitmap, u: Float, v: Float): Float {
    val x = (u * (mask.width - 1)).roundToInt()
    val y = (v * (mask.height - 1)).roundToInt()
    return android.graphics.Color.alpha(mask.getPixel(x, y)) / 255f
}

/** Where a [srcWidth]×[srcHeight] image goes in the box: cropped to fill, or fitted inside. */
internal fun fitRect(srcWidth: Int, srcHeight: Int, boxWidth: Float, boxHeight: Float, crop: Boolean): RectF {
    val scaleX = boxWidth / srcWidth
    val scaleY = boxHeight / srcHeight
    val scale = if (crop) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
    val width = srcWidth * scale
    val height = srcHeight * scale
    val left = (boxWidth - width) / 2f
    val top = (boxHeight - height) / 2f
    return RectF(left, top, left + width, top + height)
}
