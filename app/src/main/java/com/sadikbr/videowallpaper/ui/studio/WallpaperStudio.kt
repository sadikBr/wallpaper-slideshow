package com.sadikbr.videowallpaper.ui.studio

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sadikbr.videowallpaper.clock.ClockFonts
import com.sadikbr.videowallpaper.data.ClockFont
import com.sadikbr.videowallpaper.data.ClockSettings
import com.sadikbr.videowallpaper.data.DepthMasks
import com.sadikbr.videowallpaper.data.MediaInfoLoader
import com.sadikbr.videowallpaper.data.MediaKind
import com.sadikbr.videowallpaper.data.PhotoFraming
import com.sadikbr.videowallpaper.data.ScaleMode
import com.sadikbr.videowallpaper.data.TimeFormat
import com.sadikbr.videowallpaper.data.WallpaperSettings
import com.sadikbr.videowallpaper.ui.components.DepthPreview
import com.sadikbr.videowallpaper.ui.components.PreviewGeometry
import com.sadikbr.videowallpaper.ui.components.fitRect
import com.sadikbr.videowallpaper.ui.components.rememberAlphaMask
import com.sadikbr.videowallpaper.ui.components.rememberMediaInfo
import com.sadikbr.videowallpaper.ui.theme.Brand
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.Typeface as ComposeTypeface

/** What the editor changed, applied when the user taps Done. */
data class StudioResult(
    val clock: ClockSettings,
    /** Per-photo clock positions; null values go back to automatic. */
    val clockOffsets: Map<String, Float?>,
    /** Per-photo framings; null values go back to automatic. */
    val framings: Map<String, PhotoFraming?>,
)

private enum class DragTarget { NONE, CLOCK, STRETCH, PHOTO }

/**
 * Full-screen wallpaper editor: the live wallpaper preview, edited directly with gestures.
 *
 * - Clock: drag to move, pinch to resize, pull the handle under it to stretch the digits,
 *   double-tap to put it back.
 * - Photo: pinch and drag to frame it, double-tap to go back to automatic framing.
 * - Bottom tray: fonts, colours, glass effects and clock options.
 */
@Composable
fun WallpaperStudio(
    settings: WallpaperSettings,
    startUri: String?,
    loader: MediaInfoLoader,
    depthStates: Map<String, DepthMasks.State>,
    systemUses24Hour: Boolean,
    onDone: (StudioResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var clock by remember { mutableStateOf(settings.clock.copy(enabled = true)) }
    val offsetEdits = remember { mutableStateMapOf<String, Float?>() }
    val framingEdits = remember { mutableStateMapOf<String, PhotoFraming?>() }
    var selectedUri by remember { mutableStateOf(startUri ?: settings.media.firstOrNull()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0920))
                .statusBarsPadding(),
        ) {
            // Top bar.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cancel", tint = Color.White) }
                Text(
                    "Edit wallpaper",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = {
                    onDone(StudioResult(clock, offsetEdits.toMap(), framingEdits.toMap()))
                }) { Icon(Icons.Default.Check, "Done", tint = Color.White) }
            }

            // Which item is being edited.
            if (settings.media.size > 1) {
                ItemStrip(
                    media = settings.media,
                    selected = selectedUri,
                    loader = loader,
                    onSelect = { selectedUri = it },
                )
            }

            val uri = selectedUri
            val info by rememberMediaInfo(uri, loader)
            val isPhoto = info?.kind == MediaKind.IMAGE
            var large by remember(uri) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(uri) { large = uri?.let { loader.loadLarge(it) } }
            val photo = large ?: info?.thumbnail
            val mask by rememberAlphaMask(uri, uri != null && depthStates[uri] == DepthMasks.State.READY && isPhoto)

            val savedOffset = uri?.let { if (it in offsetEdits) offsetEdits[it] else settings.clockOffsets[it] }
            val savedFraming = uri?.let { if (it in framingEdits) framingEdits[it] else settings.photoFramings[it] }
            val use24Hour = when (clock.timeFormat) {
                TimeFormat.SYSTEM -> systemUses24Hour
                TimeFormat.H12 -> false
                TimeFormat.H24 -> true
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Editor(
                    photo = photo,
                    mask = mask,
                    clock = clock,
                    use24Hour = use24Hour,
                    scaleMode = settings.scaleMode,
                    isPhoto = isPhoto,
                    manualOffset = if (isPhoto) savedOffset else null,
                    manualFraming = if (isPhoto) savedFraming else null,
                    onClockChange = { clock = it },
                    onOffsetChange = { offset -> uri?.let { offsetEdits[it] = offset } },
                    onFramingChange = { framing -> uri?.let { framingEdits[it] = framing } },
                )
            }

            Tray(
                clock = clock,
                photo = info?.thumbnail,
                onClockChange = { clock = it },
                canResetPhoto = isPhoto && (savedOffset != null || savedFraming != null),
                onResetPhoto = {
                    uri?.let {
                        offsetEdits[it] = null
                        framingEdits[it] = null
                    }
                },
            )
        }
    }
}

/** The live preview with direct-manipulation gestures. */
@Composable
private fun Editor(
    photo: Bitmap?,
    mask: Bitmap?,
    clock: ClockSettings,
    use24Hour: Boolean,
    scaleMode: ScaleMode,
    isPhoto: Boolean,
    manualOffset: Float?,
    manualFraming: PhotoFraming?,
    onClockChange: (ClockSettings) -> Unit,
    onOffsetChange: (Float?) -> Unit,
    onFramingChange: (PhotoFraming?) -> Unit,
) {
    val density = LocalDensity.current
    var geometry by remember { mutableStateOf<PreviewGeometry?>(null) }
    var active by remember { mutableStateOf(DragTarget.NONE) }

    // Latest values for the gesture loop, which outlives individual recompositions.
    val currentClock by rememberUpdatedState(clock)
    val currentOffset by rememberUpdatedState(manualOffset)
    val currentFraming by rememberUpdatedState(manualFraming)
    val currentGeometry by rememberUpdatedState(geometry)
    val currentIsPhoto by rememberUpdatedState(isPhoto)
    val currentPhoto by rememberUpdatedState(photo)

    BoxWithConstraints(
        Modifier
            .aspectRatio(9f / 19.5f)
            .clip(RoundedCornerShape(28.dp))
            .background(Brand.Ink)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(28.dp)),
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        DepthPreview(
            photo = photo,
            mask = mask,
            clock = clock,
            use24Hour = use24Hour,
            manualOffset = manualOffset,
            manualFraming = manualFraming,
            scaleMode = scaleMode,
            onGeometry = { geometry = it },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { point ->
                        val g = currentGeometry
                        if (g != null && g.time.expandedContains(point, 40f)) {
                            // Clock back to its normal place.
                            if (currentIsPhoto) onOffsetChange(null) else onClockChange(currentClock.copy(position = ClockSettings.DEFAULT_POSITION))
                        } else if (currentIsPhoto) {
                            onFramingChange(null)
                        }
                    })
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val g = currentGeometry ?: return@awaitEachGesture
                        // Everything is computed from the values at the start of the gesture plus the
                        // total movement since, so no movement is lost between recompositions.
                        val startClock = currentClock
                        val startOffset = currentOffset ?: 0f
                        val startFraming = currentFraming ?: PhotoFraming(1f, 0f, 0f)
                        val startDigits = g.time.height().coerceAtLeast(1f)
                        val photoAtStart = currentPhoto
                        val handle = stretchHandleCenter(g)
                        active = when {
                            (down.position - handle).getDistance() < 64f -> DragTarget.STRETCH
                            g.time.expandedContains(down.position, 32f) -> DragTarget.CLOCK
                            currentIsPhoto && photoAtStart != null -> DragTarget.PHOTO
                            else -> DragTarget.NONE
                        }
                        var totalPan = Offset.Zero
                        var totalZoom = 1f
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            if (pan == Offset.Zero && zoom == 1f) continue
                            totalPan += pan
                            totalZoom *= zoom
                            when (active) {
                                DragTarget.CLOCK -> {
                                    val dy = totalPan.y / heightPx
                                    var updated = startClock.copy(
                                        width = (startClock.width * totalZoom)
                                            .coerceIn(ClockSettings.MIN_WIDTH, ClockSettings.MAX_WIDTH),
                                    )
                                    if (currentIsPhoto) {
                                        onOffsetChange(
                                            (startOffset + dy).coerceIn(
                                                ClockSettings.MIN_POSITION - startClock.position,
                                                ClockSettings.MAX_POSITION - startClock.position,
                                            ),
                                        )
                                    } else {
                                        updated = updated.copy(
                                            position = (startClock.position + dy)
                                                .coerceIn(ClockSettings.MIN_POSITION, ClockSettings.MAX_POSITION),
                                        )
                                    }
                                    onClockChange(updated)
                                }
                                DragTarget.STRETCH -> {
                                    val stretch = (startClock.stretch * (startDigits + totalPan.y) / startDigits)
                                        .coerceIn(ClockSettings.MIN_STRETCH, ClockSettings.MAX_STRETCH)
                                    onClockChange(startClock.copy(stretch = stretch))
                                }
                                DragTarget.PHOTO -> {
                                    val p = photoAtStart ?: break
                                    val zoomed = (startFraming.zoom * totalZoom).coerceIn(1f, PhotoFraming.MAX_ZOOM)
                                    // Keep the photo covering the screen.
                                    val fitted = fitRect(p.width, p.height, widthPx, heightPx, scaleMode == ScaleMode.CROP)
                                    val maxX = ((fitted.width() * zoomed - widthPx) / 2f / widthPx).coerceAtLeast(0f)
                                    val maxY = ((fitted.height() * zoomed - heightPx) / 2f / heightPx).coerceAtLeast(0f)
                                    onFramingChange(
                                        PhotoFraming(
                                            zoom = zoomed,
                                            shift = (startFraming.shift + totalPan.y / heightPx).coerceIn(-maxY, maxY),
                                            shiftX = (startFraming.shiftX + totalPan.x / widthPx).coerceIn(-maxX, maxX),
                                        ),
                                    )
                                }
                                DragTarget.NONE -> Unit
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (true)
                        active = DragTarget.NONE
                    }
                },
        )

        // Selection outline and stretch handle on top of the clock.
        val g = geometry
        if (g != null && clock.enabled && !g.time.isEmpty) {
            Canvas(Modifier.fillMaxSize()) {
                val outline = if (active == DragTarget.CLOCK || active == DragTarget.STRETCH) 1f else 0.45f
                drawRoundRect(
                    color = Color.White.copy(alpha = outline),
                    topLeft = Offset(g.time.left - 16f, g.time.top - 16f),
                    size = androidx.compose.ui.geometry.Size(g.time.width() + 32f, g.time.height() + 32f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f),
                    style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))),
                )
            }
            val handle = stretchHandleCenter(g)
            with(density) {
                Box(
                    Modifier
                        .offset { IntOffset((handle.x - 28.dp.toPx()).roundToInt(), (handle.y - 10.dp.toPx()).roundToInt()) }
                        .size(width = 56.dp, height = 20.dp)
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .semantics { contentDescription = "Stretch handle: drag to make the digits taller" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 24.dp, height = 3.dp).background(Brand.Indigo, RoundedCornerShape(2.dp)))
                }
            }
        }

        // Gesture hints.
        Text(
            when (active) {
                DragTarget.CLOCK -> "Drag to move · pinch to resize"
                DragTarget.STRETCH -> "Pull to stretch the digits"
                DragTarget.PHOTO -> "Pinch and drag to frame the photo"
                DragTarget.NONE -> if (isPhoto) "Drag or pinch the clock or the photo · double-tap to reset" else "Drag or pinch the clock"
            },
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun stretchHandleCenter(g: PreviewGeometry) = Offset(g.time.centerX(), g.time.bottom + 30f)

private fun RectF.expandedContains(point: Offset, margin: Float) =
    point.x in (left - margin)..(right + margin) && point.y in (top - margin)..(bottom + margin)

/** Thumbnails of the slideshow items, to pick which one to edit. */
@Composable
private fun ItemStrip(media: List<String>, selected: String?, loader: MediaInfoLoader, onSelect: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        items(media, key = { it }) { uri ->
            val info by rememberMediaInfo(uri, loader)
            val isSelected = uri == selected
            Box(
                Modifier
                    .size(width = 40.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .border(if (isSelected) 2.dp else 0.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(uri) }
                    .semantics { contentDescription = info?.name ?: "Item" },
            ) {
                info?.thumbnail?.let {
                    Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

private enum class TrayTab(val label: String) { FONT("Font"), COLOR("Color"), GLASS("Glass"), CLOCK("Clock") }

/** Bottom tray with visual pickers. */
@Composable
private fun Tray(
    clock: ClockSettings,
    photo: Bitmap?,
    onClockChange: (ClockSettings) -> Unit,
    canResetPhoto: Boolean,
    onResetPhoto: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF15122E), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .navigationBarsPadding(),
    ) {
        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab]), color = Brand.Coral)
            },
            divider = {},
        ) {
            TrayTab.entries.forEachIndexed { index, item ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(item.label) })
            }
        }
        // Fixed height so the preview doesn't jump between tabs; scrolls if a tab needs more room
        // (e.g. with large system font sizes), so every control stays reachable.
        Column(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 8.dp),
        ) {
            when (TrayTab.entries[tab]) {
                TrayTab.FONT -> FontPicker(clock, onClockChange)
                TrayTab.COLOR -> ColorPicker(clock, photo, onClockChange)
                TrayTab.GLASS -> GlassPicker(clock, onClockChange)
                TrayTab.CLOCK -> ClockOptions(clock, onClockChange, canResetPhoto, onResetPhoto)
            }
        }
    }
}

@Composable
private fun FontPicker(clock: ClockSettings, onClockChange: (ClockSettings) -> Unit) {
    val context = LocalContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(ClockFont.entries) { font ->
            val selected = clock.font == font
            val family = remember(font) { FontFamily(ComposeTypeface(ClockFonts.typeface(context, font))) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
                    .border(if (selected) 2.dp else 0.dp, if (selected) Brand.Coral else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable { onClockChange(clock.copy(font = font)) }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    "12",
                    fontFamily = family,
                    fontSize = 44.sp,
                    color = Color.White,
                    modifier = Modifier.graphicsLayer(scaleX = font.widthScale, scaleY = font.heightScale),
                )
                Spacer(Modifier.height(6.dp))
                Text(font.label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ColorPicker(clock: ClockSettings, photo: Bitmap?, onClockChange: (ClockSettings) -> Unit) {
    val matched = remember(photo) { photo?.let(::pastelFrom) }
    val colors = listOfNotNull(matched) + ClockSettings.COLORS
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
        items(colors) { color ->
            val selected = clock.color == color
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(52.dp)
                        .border(if (selected) 3.dp else 1.dp, if (selected) Brand.Coral else Color.White.copy(alpha = 0.25f), CircleShape)
                        .padding(5.dp)
                        .background(Color(color), CircleShape)
                        .clickable { onClockChange(clock.copy(color = color)) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check, null,
                            tint = if (androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5) Color.Black else Color.White,
                        )
                    }
                }
                if (color == matched) {
                    Text("Photo", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** A soft, light tint taken from the top of the photo (where the clock usually is). */
private fun pastelFrom(photo: Bitmap): Int {
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0
    val stepX = (photo.width / 24).coerceAtLeast(1)
    val stepY = (photo.height / 40).coerceAtLeast(1)
    for (y in 0 until photo.height * 4 / 10 step stepY) {
        for (x in 0 until photo.width step stepX) {
            val c = photo.getPixel(x, y)
            r += android.graphics.Color.red(c)
            g += android.graphics.Color.green(c)
            b += android.graphics.Color.blue(c)
            n++
        }
    }
    if (n == 0) return android.graphics.Color.WHITE
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
    hsv[1] = (hsv[1] * 0.45f).coerceAtMost(0.35f)
    hsv[2] = 1f
    return android.graphics.Color.HSVToColor(hsv)
}

private data class GlassPreset(val label: String, val opacity: Float, val fade: Float)

private val GLASS_PRESETS = listOf(
    GlassPreset("Solid", 1f, 0f),
    GlassPreset("Glass", 0.6f, 0f),
    GlassPreset("Frost", 0.85f, 0.65f),
    GlassPreset("Ghost", 0.35f, 0f),
    GlassPreset("Mist", 0.55f, 0.85f),
)

@Composable
private fun GlassPicker(clock: ClockSettings, onClockChange: (ClockSettings) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(GLASS_PRESETS) { preset ->
                val selected = abs(clock.opacity - preset.opacity) < 0.02f && abs(clock.fade - preset.fade) < 0.02f
                FilterChip(
                    selected = selected,
                    onClick = { onClockChange(clock.copy(opacity = preset.opacity, fade = preset.fade)) },
                    label = { Text(preset.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = Color.White,
                        selectedContainerColor = Brand.Coral,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
        TraySlider("Opacity", clock.opacity, ClockSettings.MIN_OPACITY..1f) { onClockChange(clock.copy(opacity = it)) }
        TraySlider("Fade", clock.fade, 0f..1f) { onClockChange(clock.copy(fade = it)) }
    }
}

@Composable
private fun TraySlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Brand.Coral),
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClockOptions(
    clock: ClockSettings,
    onClockChange: (ClockSettings) -> Unit,
    canResetPhoto: Boolean,
    onResetPhoto: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeFormat.entries.forEach { format ->
                FilterChip(
                    selected = clock.timeFormat == format,
                    onClick = { onClockChange(clock.copy(timeFormat = format)) },
                    label = { Text(format.label) },
                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedContainerColor = Brand.Coral, selectedLabelColor = Color.White),
                )
            }
        }
        OptionSwitch("Show date", clock.showDate) { onClockChange(clock.copy(showDate = it)) }
        OptionSwitch("Auto depth framing", clock.autoPlace) { onClockChange(clock.copy(autoPlace = it)) }
        if (canResetPhoto) {
            OutlinedButton(onClick = onResetPhoto, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset this photo's position and framing", color = Color.White)
            }
        }
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    // Full-width row, switch at the end; the whole row toggles.
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
