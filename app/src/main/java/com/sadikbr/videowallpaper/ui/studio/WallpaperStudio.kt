package com.sadikbr.videowallpaper.ui.studio

import android.graphics.Bitmap
import android.graphics.RectF
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.sadikbr.videowallpaper.clock.ClockFonts
import com.sadikbr.videowallpaper.data.ClockFont
import com.sadikbr.videowallpaper.data.ClockSettings
import com.sadikbr.videowallpaper.data.DepthMasks
import com.sadikbr.videowallpaper.data.MediaInfoLoader
import com.sadikbr.videowallpaper.data.MediaKind
import com.sadikbr.videowallpaper.data.PhotoFraming
import com.sadikbr.videowallpaper.data.ScaleMode
import com.sadikbr.videowallpaper.data.TimeFormat
import com.sadikbr.videowallpaper.data.WallpaperLook
import com.sadikbr.videowallpaper.data.WallpaperSettings
import com.sadikbr.videowallpaper.data.SignatureFont
import com.sadikbr.videowallpaper.data.WidgetData
import com.sadikbr.videowallpaper.data.WidgetSettings
import com.sadikbr.videowallpaper.data.WidgetSnapshot
import com.sadikbr.videowallpaper.data.WidgetStyle
import androidx.compose.runtime.collectAsState
import com.sadikbr.videowallpaper.ui.components.DepthPreview
import com.sadikbr.videowallpaper.ui.components.PreviewGeometry
import com.sadikbr.videowallpaper.ui.components.fitRect
import com.sadikbr.videowallpaper.ui.components.rememberAlphaMask
import com.sadikbr.videowallpaper.ui.components.rememberMediaInfo
import com.sadikbr.videowallpaper.ui.theme.AppIcons
import com.sadikbr.videowallpaper.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.Typeface as ComposeTypeface

/** What the editor changed, applied when the user taps Done. */
data class StudioResult(
    /** Per-photo clock positions; null values go back to automatic. */
    val clockOffsets: Map<String, Float?>,
    /** Per-photo framings; null values go back to automatic. */
    val framings: Map<String, PhotoFraming?>,
    /** Looks changed in the editor, per item. */
    val looks: Map<String, WallpaperLook>,
    /** Set when "Apply to all" was used: the new default and look of every item. */
    val lookForAll: WallpaperLook?,
    /** The default look, when edited with an empty slideshow. */
    val defaultLook: WallpaperLook?,
)

private enum class DragTarget { NONE, CLOCK, STRETCH, PHOTO, WIDGETS }

private enum class Tool(val label: String) { FONT("Font"), COLOR("Color"), GLASS("Glass"), WIDGETS("Widgets"), OPTIONS("Options") }

private val StudioBackground = Color(0xFF0B0920)
private val PanelColor = Color(0xFF17132F)
private val TileColor = Color.White.copy(alpha = 0.07f)
private val PANEL_HEIGHT = 128.dp

/**
 * Full-screen wallpaper editor (a page of the app, not a pop-up, so it fits between the
 * status and navigation bars): the live wallpaper preview, edited directly with gestures.
 *
 * - Clock: tap to select; drag to move, pinch to resize, pull the handle under it to stretch
 *   the digits; double-tap to put it back.
 * - Photo: pinch and drag to frame it; double-tap to go back to automatic framing.
 * - Toolbar: fonts, colours, glass effects and clock options.
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
    /** Which tool panel is open at first (0 = Font); for screenshot tests. */
    initialTool: Int = 0,
) {
    BackHandler(onBack = onDismiss)

    val widgetSnapshot by WidgetData.snapshot.collectAsState()
    val offsetEdits = remember { mutableStateMapOf<String, Float?>() }
    val framingEdits = remember { mutableStateMapOf<String, PhotoFraming?>() }
    // Each wallpaper has its own look; edits go to the one being edited.
    val lookEdits = remember { mutableStateMapOf<String, WallpaperLook>() }
    var lookForAll by remember { mutableStateOf<WallpaperLook?>(null) }
    var defaultLook by remember { mutableStateOf<WallpaperLook?>(null) }
    var selectedUri by remember { mutableStateOf(startUri ?: settings.media.firstOrNull()) }
    var tool by remember { mutableStateOf(Tool.entries[initialTool.coerceIn(0, Tool.entries.lastIndex)]) }

    val uri = selectedUri
    val look = when {
        uri == null -> defaultLook ?: settings.defaultLook
        else -> lookEdits[uri] ?: lookForAll ?: settings.lookFor(uri)
    }
    val clock = look.clock
    val widgets = look.widgets
    fun setLook(newLook: WallpaperLook) {
        if (uri == null) defaultLook = newLook else lookEdits[uri] = newLook
    }
    fun setClock(newClock: ClockSettings) = setLook(look.copy(clock = newClock))
    fun setWidgets(newWidgets: WidgetSettings) = setLook(look.copy(widgets = newWidgets))
    fun applyToAll() {
        lookForAll = look
        lookEdits.clear()
        defaultLook = null
    }
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

    Column(
        Modifier
            .fillMaxSize()
            .background(StudioBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        TopBar(
            subtitle = info?.name,
            onClose = onDismiss,
            onDone = {
                onDone(StudioResult(offsetEdits.toMap(), framingEdits.toMap(), lookEdits.toMap(), lookForAll, defaultLook))
            },
        )

        if (settings.media.size > 1) {
            ItemStrip(
                media = settings.media,
                selected = uri,
                loader = loader,
                onSelect = { selectedUri = it },
                appliedToAll = lookForAll == look && lookEdits.isEmpty(),
                onApplyToAll = { applyToAll() },
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Editor(
                photo = photo,
                mask = mask,
                clock = clock,
                use24Hour = use24Hour,
                scaleMode = settings.scaleMode,
                isPhoto = isPhoto,
                manualOffset = if (isPhoto) savedOffset else null,
                manualFraming = if (isPhoto) savedFraming else null,
                onClockChange = { setClock(it) },
                onOffsetChange = { offset -> uri?.let { offsetEdits[it] = offset } },
                onFramingChange = { framing -> uri?.let { framingEdits[it] = framing } },
                widgets = widgets,
                widgetSnapshot = widgetSnapshot,
                onWidgetsChange = { setWidgets(it) },
            )
        }

        // The panel for the chosen tool, then the toolbar.
        Column(
            Modifier
                .fillMaxWidth()
                .background(PanelColor, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        ) {
            AnimatedContent(
                targetState = tool,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "tool panel",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PANEL_HEIGHT)
                    .padding(top = 16.dp),
            ) { current ->
                when (current) {
                    Tool.FONT -> FontPanel(clock) { setClock(it) }
                    Tool.COLOR -> ColorPanel(clock, photo) { setClock(it) }
                    Tool.GLASS -> GlassPanel(clock) { setClock(it) }
                    Tool.WIDGETS -> WidgetsPanel(widgets) { setWidgets(it) }
                    Tool.OPTIONS -> OptionsPanel(
                        clock = clock,
                        onClockChange = { setClock(it) },
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
            Toolbar(selected = tool, onSelect = { tool = it })
        }
    }
}

@Composable
private fun TopBar(subtitle: String?, onClose: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cancel", tint = Color.White) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Edit wallpaper", color = Color.White, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier
                .clip(CircleShape)
                .background(Brand.gradient)
                .clickable(role = Role.Button, onClick = onDone)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Done", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
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
    widgets: WidgetSettings,
    widgetSnapshot: WidgetSnapshot,
    onWidgetsChange: (WidgetSettings) -> Unit,
) {
    var geometry by remember { mutableStateOf<PreviewGeometry?>(null) }
    var active by remember { mutableStateOf(DragTarget.NONE) }
    var clockSelected by remember { mutableStateOf(false) }
    var showIntroHint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(4500)
        showIntroHint = false
    }

    // Latest values for the gesture loops, which outlive individual recompositions.
    val currentClock by rememberUpdatedState(clock)
    val currentOffset by rememberUpdatedState(manualOffset)
    val currentFraming by rememberUpdatedState(manualFraming)
    val currentGeometry by rememberUpdatedState(geometry)
    val currentIsPhoto by rememberUpdatedState(isPhoto)
    val currentPhoto by rememberUpdatedState(photo)
    val currentSelected by rememberUpdatedState(clockSelected)
    val currentWidgets by rememberUpdatedState(widgets)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .weight(1f, fill = false)
                .aspectRatio(9f / 19.5f)
                .clip(RoundedCornerShape(32.dp))
                .background(Brand.Ink)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(32.dp)),
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
                widgets = widgets,
                widgetSnapshot = widgetSnapshot,
                widgetPlaceholders = true,
                onGeometry = { geometry = it },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { point ->
                                clockSelected = currentGeometry?.time?.expandedContains(point, 40f) == true
                            },
                            onDoubleTap = { point ->
                                val g = currentGeometry
                                if (g?.widgets?.expandedContains(point, 16f) == true) {
                                    // Widgets back under the clock.
                                    onWidgetsChange(currentWidgets.copy(position = null))
                                } else if (g != null && g.time.expandedContains(point, 40f)) {
                                    // Clock back to its normal place.
                                    if (currentIsPhoto) {
                                        onOffsetChange(null)
                                    } else {
                                        onClockChange(currentClock.copy(position = ClockSettings.DEFAULT_POSITION))
                                    }
                                } else if (currentIsPhoto) {
                                    onFramingChange(null)
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val g = currentGeometry ?: return@awaitEachGesture
                            // Everything is computed from the values at the start of the gesture plus
                            // the total movement since, so no movement is lost between recompositions.
                            val startClock = currentClock
                            val startOffset = currentOffset ?: 0f
                            val startFraming = currentFraming ?: PhotoFraming(1f, 0f, 0f)
                            val startDigits = g.time.height().coerceAtLeast(1f)
                            val photoAtStart = currentPhoto
                            val handle = stretchHandleCenter(g)
                            val startWidgets = currentWidgets
                            val startWidgetsTop = g.widgetsTop
                            val target = when {
                                currentSelected && (down.position - handle).getDistance() < 72f -> DragTarget.STRETCH
                                g.widgets?.expandedContains(down.position, 16f) == true -> DragTarget.WIDGETS
                                g.time.expandedContains(down.position, 32f) -> DragTarget.CLOCK
                                currentIsPhoto && photoAtStart != null -> DragTarget.PHOTO
                                else -> DragTarget.NONE
                            }
                            var totalPan = Offset.Zero
                            var totalZoom = 1f
                            var started = false
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                if (pan == Offset.Zero && zoom == 1f) continue
                                totalPan += pan
                                totalZoom *= zoom
                                // Ignore tiny movements so a tap stays a tap.
                                if (!started && totalPan.getDistance() < 12f && abs(totalZoom - 1f) < 0.02f) continue
                                if (!started) {
                                    started = true
                                    active = target
                                    if (target == DragTarget.CLOCK || target == DragTarget.STRETCH) clockSelected = true
                                    if (target == DragTarget.PHOTO) clockSelected = false
                                }
                                when (target) {
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
                                    DragTarget.WIDGETS -> {
                                        val top = (startWidgetsTop + totalPan.y) / heightPx
                                        onWidgetsChange(
                                            startWidgets.copy(
                                                position = top.coerceIn(WidgetSettings.MIN_POSITION, WidgetSettings.MAX_POSITION),
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

            // Selection outline and stretch handle, only while the clock is selected.
            val g = geometry
            if (g != null && clockSelected && !g.time.isEmpty) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = if (active == DragTarget.NONE) 0.7f else 1f),
                        topLeft = Offset(g.time.left - 14f, g.time.top - 14f),
                        size = Size(g.time.width() + 28f, g.time.height() + 28f),
                        cornerRadius = CornerRadius(22f),
                        style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))),
                    )
                }
                val handle = stretchHandleCenter(g)
                Box(
                    Modifier
                        .offset { IntOffset((handle.x - 26.dp.toPx()).roundToInt(), (handle.y - 9.dp.toPx()).roundToInt()) }
                        .size(width = 52.dp, height = 18.dp)
                        .background(Color.White, RoundedCornerShape(9.dp))
                        .semantics { contentDescription = "Stretch handle: drag down to make the digits taller" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(width = 20.dp, height = 3.dp).background(Brand.Indigo, RoundedCornerShape(2.dp)))
                }
            }
        }

        // Contextual hint, under the preview so it never covers it.
        val hint = when {
            active == DragTarget.CLOCK -> "Drag to move · pinch to resize"
            active == DragTarget.STRETCH -> "Pull down to stretch the digits"
            active == DragTarget.PHOTO -> "Pinch and drag to frame · double-tap to reset"
            active == DragTarget.WIDGETS -> "Drag the widgets · double-tap to put them under the clock"
            showIntroHint -> "Each wallpaper has its own look · tap the clock to select it"
            clockSelected -> "Drag, pinch, or pull the handle · double-tap to reset"
            else -> null
        }
        // Keep showing the last hint while it fades out.
        val lastHint = remember { arrayOf("") }
        if (hint != null) lastHint[0] = hint
        val hintAlpha by animateFloatAsState(if (hint != null) 1f else 0f, label = "hint")
        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            Text(
                lastHint[0],
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer(alpha = hintAlpha),
            )
        }
    }
}

private fun stretchHandleCenter(g: PreviewGeometry) = Offset(g.time.centerX(), g.time.bottom + 30f)

private fun RectF.expandedContains(point: Offset, margin: Float) =
    point.x in (left - margin)..(right + margin) && point.y in (top - margin)..(bottom + margin)

/** Thumbnails of the slideshow items, to pick which one to edit. */
@Composable
private fun ItemStrip(
    media: List<String>,
    selected: String?,
    loader: MediaInfoLoader,
    onSelect: (String) -> Unit,
    appliedToAll: Boolean,
    onApplyToAll: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        items(media, key = { it }) { uri ->
            val info by rememberMediaInfo(uri, loader)
            val isSelected = uri == selected
            Box(
                Modifier
                    .size(width = 34.dp, height = 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TileColor)
                    .border(2.dp, if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(uri) }
                    .semantics {
                        contentDescription = info?.name ?: "Item"
                        this.selected = isSelected
                    },
            ) {
                info?.thumbnail?.let {
                    Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
        item {
            // Copies the look being edited to every wallpaper (and makes it the default).
            Row(
                Modifier
                    .padding(start = 4.dp)
                    .clip(CircleShape)
                    .background(if (appliedToAll) Brand.Indigo else TileColor)
                    .clickable(role = Role.Button, onClick = onApplyToAll)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Layers, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (appliedToAll) "Same look on all" else "Same look for all",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun Toolbar(selected: Tool, onSelect: (Tool) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tool.entries.forEach { tool ->
            val isSelected = tool == selected
            val icon: ImageVector = when (tool) {
                Tool.FONT -> AppIcons.TextFields
                Tool.COLOR -> AppIcons.Palette
                Tool.GLASS -> AppIcons.Opacity
                Tool.WIDGETS -> AppIcons.Widgets
                Tool.OPTIONS -> AppIcons.Tune
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Tab) { onSelect(tool) }
                    .semantics { this.selected = isSelected }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            ) {
                Box(
                    Modifier
                        .size(width = 48.dp, height = 32.dp)
                        .background(if (isSelected) Brand.Coral else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    tool.label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun FontPanel(clock: ClockSettings, onClockChange: (ClockSettings) -> Unit) {
    val context = LocalContext.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
        items(ClockFont.entries) { font ->
            val selected = clock.font == font
            val family = remember(font) { FontFamily(ComposeTypeface(ClockFonts.typeface(context, font))) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .size(width = 72.dp, height = 96.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.16f) else TileColor)
                    .border(2.dp, if (selected) Brand.Coral else Color.Transparent, RoundedCornerShape(18.dp))
                    .clickable(role = Role.RadioButton) { onClockChange(clock.copy(font = font)) }
                    .semantics { this.selected = selected },
            ) {
                Text(
                    "12",
                    fontFamily = family,
                    fontSize = 40.sp,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer(scaleX = font.widthScale, scaleY = font.heightScale),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    font.label,
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ColorPanel(clock: ClockSettings, photo: Bitmap?, onClockChange: (ClockSettings) -> Unit) {
    val matched = remember(photo) { photo?.let(::pastelFrom) }
    val colors = listOfNotNull(matched) + ClockSettings.COLORS
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
            items(colors) { color ->
                val selected = clock.color == color
                Box(
                    Modifier
                        .size(48.dp)
                        .border(3.dp, if (selected) Brand.Coral else Color.White.copy(alpha = 0.18f), CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .clickable(role = Role.RadioButton) { onClockChange(clock.copy(color = color)) }
                        .semantics { this.selected = selected },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check, null,
                            tint = if (ColorUtils.calculateLuminance(color) > 0.5) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    } else if (color == matched) {
                        Icon(AppIcons.AutoAwesome, "Matched to the photo", tint = Color.Black.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (matched != null) {
            Text(
                "The first colour is picked from your photo",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp),
            )
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
private fun GlassPanel(clock: ClockSettings, onClockChange: (ClockSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // The looks share the width, so they all fit on any phone.
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(TileColor),
        ) {
            GLASS_PRESETS.forEach { preset ->
                val selected = abs(clock.opacity - preset.opacity) < 0.02f && abs(clock.fade - preset.fade) < 0.02f
                Box(
                    Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (selected) Brand.Coral else Color.Transparent)
                        .clickable(role = Role.RadioButton) { onClockChange(clock.copy(opacity = preset.opacity, fade = preset.fade)) }
                        .semantics { this.selected = selected }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(preset.label, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
        val waterline = clock.waterline
        Row(
            Modifier.padding(start = 20.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (waterline != null) {
                // Under water, the looks above set the opacity; the sliders place the water.
                // Slider to the right = more of the clock under water.
                PanelSlider(
                    "Water level", 1f - waterline,
                    (1f - ClockSettings.MAX_WATERLINE)..(1f - ClockSettings.MIN_WATERLINE), Modifier.weight(1f),
                ) { onClockChange(clock.copy(waterline = 1f - it)) }
                // Hides the digits just under the surface, down to the photo's underwater view.
                PanelSlider("Gap", clock.waterGap, 0f..ClockSettings.MAX_WATER_GAP, Modifier.weight(1f)) {
                    onClockChange(clock.copy(waterGap = it))
                }
            } else {
                PanelSlider("Opacity", clock.opacity, ClockSettings.MIN_OPACITY..1f, Modifier.weight(1f)) {
                    onClockChange(clock.copy(opacity = it))
                }
                PanelSlider("Fade", clock.fade, 0f..1f, Modifier.weight(1f)) { onClockChange(clock.copy(fade = it)) }
            }
            WaterButton(on = waterline != null) {
                onClockChange(clock.copy(waterline = if (waterline != null) null else ClockSettings.DEFAULT_WATERLINE))
            }
        }
    }
}

/** Sinks the clock in water, or brings it back up. */
@Composable
private fun WaterButton(on: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch, onClickLabel = if (on) "Take the clock out of the water" else "Put the clock under water", onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (on) Brand.Coral else TileColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Opacity, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text("Water", color = Color.White.copy(alpha = if (on) 1f else 0.7f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PanelSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Brand.Coral,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
        )
    }
}

@Composable
private fun OptionsPanel(
    clock: ClockSettings,
    onClockChange: (ClockSettings) -> Unit,
    canResetPhoto: Boolean,
    onResetPhoto: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Time format as one segmented control.
        Segmented(
            options = TimeFormat.entries.map { it.label },
            selected = clock.timeFormat.ordinal,
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        ) { onClockChange(clock.copy(timeFormat = TimeFormat.entries[it])) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
            item {
                ToggleTile(AppIcons.Schedule, "Clock", clock.enabled, Modifier.width(96.dp)) {
                    onClockChange(clock.copy(enabled = !clock.enabled))
                }
            }
            item {
                ToggleTile(AppIcons.Event, "Date", clock.showDate, Modifier.width(92.dp)) {
                    onClockChange(clock.copy(showDate = !clock.showDate))
                }
            }
            item {
                ToggleTile(AppIcons.AutoAwesome, "Framing", clock.autoPlace, Modifier.width(112.dp)) {
                    onClockChange(clock.copy(autoPlace = !clock.autoPlace))
                }
            }
            item {
                ToggleTile(Icons.Default.Refresh, "Reset photo", false, Modifier.width(130.dp), enabled = canResetPhoto, onClick = onResetPhoto)
            }
        }
    }
}

@Composable
private fun ToggleTile(
    icon: ImageVector,
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.35f
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) Brand.Indigo else TileColor)
            .clickable(enabled = enabled, role = Role.Switch, onClick = onClick)
            .semantics { this.selected = on }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = alpha), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = Color.White.copy(alpha = alpha),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Widgets tool: tap a tile to turn a widget on or off (asking for the permission it needs),
 * edit the signature line, and choose the style and temperature unit.
 */
@Composable
private fun WidgetsPanel(widgets: WidgetSettings, onChange: (WidgetSettings) -> Unit) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(widgets)
    var editingSignature by remember { mutableStateOf(false) }

    // Weather and air quality need the approximate location (read now, while the app is open).
    var pendingLocationWidget by remember { mutableStateOf<((WidgetSettings) -> WidgetSettings)?>(null) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val enable = pendingLocationWidget
        pendingLocationWidget = null
        if (granted && enable != null) {
            val updated = enable(latest)
            onChange(updated)
            WidgetData.updateLocation(context) { WidgetData.refresh(context, updated, force = true) }
        }
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val updated = latest.copy(calendar = true)
            onChange(updated)
            WidgetData.refresh(context, updated, force = true)
        }
    }

    fun toggleLocationWidget(on: Boolean, change: (WidgetSettings) -> WidgetSettings) {
        if (!on) {
            onChange(change(widgets))
        } else if (WidgetData.hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            val updated = change(widgets)
            onChange(updated)
            WidgetData.updateLocation(context) { WidgetData.refresh(context, updated, force = true) }
        } else {
            pendingLocationWidget = change
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
            item {
                ToggleTile(AppIcons.Cloud, "Weather", widgets.weather, Modifier.width(112.dp)) {
                    toggleLocationWidget(!widgets.weather) { it.copy(weather = !widgets.weather) }
                }
            }
            item {
                ToggleTile(AppIcons.Air, "Air quality", widgets.airQuality, Modifier.width(124.dp)) {
                    toggleLocationWidget(!widgets.airQuality) { it.copy(airQuality = !widgets.airQuality) }
                }
            }
            item {
                ToggleTile(AppIcons.Battery, "Battery", widgets.battery, Modifier.width(108.dp)) {
                    onChange(widgets.copy(battery = !widgets.battery))
                }
            }
            item {
                ToggleTile(AppIcons.Event, "Calendar", widgets.calendar, Modifier.width(116.dp)) {
                    when {
                        widgets.calendar -> onChange(widgets.copy(calendar = false))
                        WidgetData.hasPermission(context, Manifest.permission.READ_CALENDAR) -> {
                            val updated = widgets.copy(calendar = true)
                            onChange(updated)
                            WidgetData.refresh(context, updated, force = true)
                        }
                        else -> calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    }
                }
            }
            item {
                ToggleTile(AppIcons.Gesture, "Signature", widgets.signature.isNotBlank(), Modifier.width(120.dp)) {
                    editingSignature = true
                }
            }
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Segmented(
                options = WidgetStyle.entries.map { it.label },
                selected = widgets.style.ordinal,
                modifier = Modifier.weight(1f),
            ) { onChange(widgets.copy(style = WidgetStyle.entries[it])) }
            val unit = when (widgets.fahrenheit) {
                null -> if (WidgetData.usesFahrenheit(widgets)) 1 else 0
                true -> 1
                false -> 0
            }
            Segmented(options = listOf("°C", "°F"), selected = unit, modifier = Modifier.weight(1f)) {
                onChange(widgets.copy(fahrenheit = it == 1))
            }
        }
    }

    if (editingSignature) {
        SignatureDialog(
            widgets = widgets,
            onSave = {
                onChange(it)
                editingSignature = false
            },
            onDismiss = { editingSignature = false },
        )
    }
}

/**
 * Edits the signature line: its text (Arabic or Latin), font and size, with a live preview.
 * Without a chosen font, Arabic text gets the Ruqaa calligraphy font and Latin text the script one.
 */
@Composable
private fun SignatureDialog(widgets: WidgetSettings, onSave: (WidgetSettings) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(widgets.signature) }
    var chosenFont by remember { mutableStateOf(widgets.signatureFont) }
    var size by remember { mutableFloatStateOf(widgets.signatureSize) }
    val draft = widgets.copy(signature = text, signatureFont = chosenFont, signatureSize = size)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signature") },
        text = {
            SignatureEditor(
                draft = draft,
                onTextChange = { if (it.length <= 40) text = it },
                onFontChange = { chosenFont = it },
                onSizeChange = { size = it },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.copy(signature = text.trim())) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** The signature's text field, live preview, font choice and size (the dialog's content). */
@Composable
internal fun SignatureEditor(
    draft: WidgetSettings,
    onTextChange: (String) -> Unit,
    onFontChange: (SignatureFont) -> Unit,
    onSizeChange: (Float) -> Unit,
) {
    val context = LocalContext.current
    val font = draft.resolvedSignatureFont
    val family = remember(font) { FontFamily(ComposeTypeface(ClockFonts.signature(context, draft))) }
    Column {
        Text(
            "A line of your own text, in Arabic or English. Leave it empty for none.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = draft.signature,
            onValueChange = onTextChange,
            singleLine = true,
            placeholder = { Text("e.g. your name") },
            // Arabic text flows right to left as it is typed.
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        // Live preview, on a dark backdrop like the wallpaper.
        Box(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B1838)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                draft.signature.ifBlank { if (font == SignatureFont.SCRIPT) "Your name" else "توقيعك" },
                fontFamily = family,
                fontSize = (26 * draft.signatureSize).sp,
                color = Color.White.copy(alpha = if (draft.signature.isBlank()) 0.4f else 1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Font", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SignatureFont.entries.forEach { option ->
                val optionFamily = remember(option) {
                    FontFamily(ComposeTypeface(ClockFonts.signature(context, WidgetSettings(signatureFont = option))))
                }
                val selected = option == font
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Brand.Coral else TileColor)
                        .clickable(role = Role.RadioButton) { onFontChange(option) }
                        .semantics { this.selected = selected }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(option.sample, fontFamily = optionFamily, fontSize = 22.sp, color = Color.White)
                    Text(option.label, style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Size", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = draft.signatureSize,
            onValueChange = onSizeChange,
            valueRange = WidgetSettings.MIN_SIGNATURE_SIZE..WidgetSettings.MAX_SIGNATURE_SIZE,
            colors = SliderDefaults.colors(thumbColor = Brand.Coral, activeTrackColor = Brand.Coral),
        )
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier
            .clip(CircleShape)
            .background(TileColor),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (isSelected) Brand.Coral else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(index) }
                    .semantics { this.selected = isSelected }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
