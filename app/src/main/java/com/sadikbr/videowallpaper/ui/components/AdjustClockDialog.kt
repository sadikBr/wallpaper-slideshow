package com.sadikbr.videowallpaper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sadikbr.videowallpaper.clock.arrangeDepth
import com.sadikbr.videowallpaper.data.ClockSettings
import com.sadikbr.videowallpaper.data.DepthMasks
import com.sadikbr.videowallpaper.data.MediaInfoLoader
import com.sadikbr.videowallpaper.data.ScaleMode

/**
 * Lets the user move the clock up or down for one photo, with a live preview.
 *
 * @param savedOffset the photo's manual position, or null if it uses the automatic one.
 */
@Composable
fun AdjustClockDialog(
    uri: String,
    loader: MediaInfoLoader,
    clock: ClockSettings,
    use24Hour: Boolean,
    scaleMode: ScaleMode,
    depthState: DepthMasks.State?,
    savedOffset: Float?,
    onSave: (Float?) -> Unit,
    onDismiss: () -> Unit,
) {
    val info by rememberMediaInfo(uri, loader)
    val mask by rememberAlphaMask(uri, depthState == DepthMasks.State.READY)
    /** Null = automatic (or the default position when automatic placement is off). */
    var offset by remember(uri) { mutableStateOf(savedOffset) }

    // Where automatic arrangement puts the clock, so the slider can start from there.
    val photo = info?.thumbnail
    val cutOut = mask
    val automatic = remember(photo, cutOut, clock, use24Hour, scaleMode) {
        if (photo == null) {
            0f
        } else {
            val width = 1080
            val height = 2340
            val photoRect = fitRect(photo.width, photo.height, width.toFloat(), height.toFloat(), scaleMode == ScaleMode.CROP)
            arrangeDepth(
                clock, use24Hour, width, height, photoRect,
                cropped = scaleMode == ScaleMode.CROP,
                manualOffset = null,
                subjectAt = cutOut?.let { m -> { u: Float, v: Float -> alphaAt(m, u, v) } },
            ).clockOffset
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(28.dp)) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Clock position", style = MaterialTheme.typography.titleLarge)
                Text(
                    when (depthState) {
                        DepthMasks.State.READY -> "Move the clock so the subject overlaps it just right."
                        DepthMasks.State.PROCESSING, null -> "Finding the subject in this photo…"
                        DepthMasks.State.NO_SUBJECT -> "No clear subject found, so the clock stays in front."
                        DepthMasks.State.FAILED -> "Couldn't analyse this photo yet; it will be retried."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DepthPreview(
                    photo = info?.thumbnail,
                    mask = mask,
                    clock = clock,
                    use24Hour = use24Hour,
                    manualOffset = offset,
                    scaleMode = scaleMode,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .height(360.dp)
                        .aspectRatio(9f / 19.5f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF101318)),
                )
                Text(
                    if (offset == null && clock.autoPlace) "Placed automatically" else "Placed by you",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = offset ?: automatic,
                    onValueChange = { offset = it },
                    valueRange = -ClockSettings.MAX_PHOTO_OFFSET..ClockSettings.MAX_PHOTO_OFFSET,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("Higher", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("Lower", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { offset = null }, enabled = offset != null) {
                        Text(if (clock.autoPlace) "Automatic" else "Reset")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { onSave(offset); onDismiss() }) { Text("Save") }
                }
            }
        }
    }
}
