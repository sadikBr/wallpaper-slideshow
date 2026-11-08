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
import com.sadikbr.videowallpaper.clock.DepthRules
import com.sadikbr.videowallpaper.data.ClockSettings
import com.sadikbr.videowallpaper.data.DepthMasks
import com.sadikbr.videowallpaper.data.ScaleMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * A small picture of what the wallpaper shows: the photo, the clock and (when allowed) the
 * photo's subject in front of the clock. Uses the same layout rules as the wallpaper.
 *
 * @param mask alpha-only cut-out of the photo's subject.
 */
@Composable
fun DepthPreview(
    photo: Bitmap?,
    mask: Bitmap?,
    clock: ClockSettings,
    use24Hour: Boolean,
    clockOffset: Float,
    scaleMode: ScaleMode,
    modifier: Modifier = Modifier,
) {
    val photoImage = remember(photo) { photo?.asImageBitmap() }
    val maskImage = remember(mask) { mask?.asImageBitmap() }
    val minute by rememberCurrentMinute()

    Canvas(modifier) {
        minute // Redraw when the minute changes.
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas
        clipRect {
            val target = photo?.let { fitRect(it.width, it.height, width, height, scaleMode == ScaleMode.CROP) }
            val dstOffset = target?.let { IntOffset(it.left.roundToInt(), it.top.roundToInt()) }
            val dstSize = target?.let { IntSize(it.width().roundToInt(), it.height().roundToInt()) }
            if (photoImage != null && dstOffset != null && dstSize != null) {
                drawImage(photoImage, dstOffset = dstOffset, dstSize = dstSize)
            }
            if (!clock.enabled) return@clipRect

            val layout = ClockPainter.layout(clock, use24Hour, width.toInt(), height.toInt(), clockOffset)
            drawIntoCanvas { layout.draw(it.nativeCanvas) }

            if (photoImage == null || maskImage == null || mask == null || target == null) return@clipRect
            val depth = DepthRules.allowsDepth(layout.timeRect, target) { u, v ->
                val x = (u * (mask.width - 1)).roundToInt()
                val y = (v * (mask.height - 1)).roundToInt()
                android.graphics.Color.alpha(mask.getPixel(x, y)) / 255f
            }
            if (!depth) return@clipRect
            // The subject on top: the photo, kept only where the cut-out is opaque.
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(0f, 0f, width, height), Paint())
                drawImage(photoImage, dstOffset = dstOffset!!, dstSize = dstSize!!)
                drawImage(
                    maskImage,
                    srcSize = IntSize(maskImage.width, maskImage.height),
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    blendMode = BlendMode.DstIn,
                )
                canvas.restore()
            }
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

/** Where a [srcWidth]×[srcHeight] image goes in the box: cropped to fill, or fitted inside. */
private fun fitRect(srcWidth: Int, srcHeight: Int, boxWidth: Float, boxHeight: Float, crop: Boolean): RectF {
    val scaleX = boxWidth / srcWidth
    val scaleY = boxHeight / srcHeight
    val scale = if (crop) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
    val width = srcWidth * scale
    val height = srcHeight * scale
    val left = (boxWidth - width) / 2f
    val top = (boxHeight - height) / 2f
    return RectF(left, top, left + width, top + height)
}
