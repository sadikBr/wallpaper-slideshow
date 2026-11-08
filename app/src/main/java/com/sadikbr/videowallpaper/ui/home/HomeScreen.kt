package com.sadikbr.videowallpaper.ui.home

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sadikbr.videowallpaper.R
import com.sadikbr.videowallpaper.data.BuiltInWallpapers
import com.sadikbr.videowallpaper.data.DepthMasks
import com.sadikbr.videowallpaper.data.MediaInfoLoader
import com.sadikbr.videowallpaper.data.MediaKind
import com.sadikbr.videowallpaper.data.TimeFormat
import com.sadikbr.videowallpaper.data.WallpaperSettings
import com.sadikbr.videowallpaper.data.WidgetSnapshot
import com.sadikbr.videowallpaper.ui.components.DepthPreview
import com.sadikbr.videowallpaper.ui.components.GalleryRow
import com.sadikbr.videowallpaper.ui.components.rememberAlphaMask
import com.sadikbr.videowallpaper.ui.components.rememberMediaInfo
import com.sadikbr.videowallpaper.ui.theme.AppIcons
import com.sadikbr.videowallpaper.ui.theme.Brand
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

/** Everything the home screen shows. */
data class HomeState(
    val settings: WallpaperSettings,
    val wallpaperActive: Boolean,
    val depthStates: Map<String, DepthMasks.State>,
    val depthProgress: Map<String, Float>,
    val widgetSnapshot: WidgetSnapshot,
    val systemUses24Hour: Boolean,
    /** Offer the guide to hide the phone's own lock screen clock. */
    val showLockClockBanner: Boolean = false,
)

/** What the home screen can do. */
data class HomeActions(
    val onAdd: () -> Unit,
    val onEdit: (uri: String?) -> Unit,
    val onRemove: (uri: String) -> Unit,
    val onMove: (from: Int, to: Int) -> Unit,
    val onApply: () -> Unit,
    val onClockEverywhere: (Boolean) -> Unit,
    val onOpenSlideshowSettings: () -> Unit,
    val builtInUri: (BuiltInWallpapers.Wallpaper) -> String,
    val onToggleBuiltIn: (BuiltInWallpapers.Wallpaper) -> Unit,
    val onOpenLockClockGuide: () -> Unit = {},
    val onOpenHomeScreenSettings: () -> Unit = {},
)

private val HomeBackground = Color(0xFF0B0920)
private val CardSurface = Color.White.copy(alpha = 0.06f)
private val CardBorder = Color.White.copy(alpha = 0.08f)
private val Muted = Color.White.copy(alpha = 0.6f)

/**
 * The home screen: your wallpapers as a swipeable carousel of live previews (each with its
 * own clock and widgets), the main action, slideshow settings at a glance, and the gallery.
 */
@Composable
fun HomeScreen(state: HomeState, actions: HomeActions, loader: MediaInfoLoader) {
    val media = state.settings.media
    // One page per wallpaper, plus the "add" card at the end.
    val pager = rememberPagerState(pageCount = { media.size + 1 })
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(HomeBackground),
    ) {
        GlowBackground()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Header(count = media.size, active = state.wallpaperActive, onAdd = actions.onAdd)

            if (media.isEmpty()) {
                EmptyState(onAdd = actions.onAdd)
            } else {
                Carousel(pager, state, loader, onAdd = actions.onAdd, onEdit = actions.onEdit)
                PageDots(pager)
                val current = media.getOrNull(pager.currentPage)
                ItemActions(
                    enabled = current != null,
                    canMoveLeft = pager.currentPage in 1..media.lastIndex,
                    canMoveRight = pager.currentPage < media.lastIndex,
                    onEdit = { actions.onEdit(current) },
                    onMoveLeft = {
                        val page = pager.currentPage
                        actions.onMove(page, page - 1)
                        scope.launch { pager.animateScrollToPage(page - 1) }
                    },
                    onMoveRight = {
                        val page = pager.currentPage
                        actions.onMove(page, page + 1)
                        scope.launch { pager.animateScrollToPage(page + 1) }
                    },
                    onRemove = { current?.let(actions.onRemove) },
                )
                ApplyButton(active = state.wallpaperActive, onClick = actions.onApply)
                if (state.showLockClockBanner) LockClockBanner(onClick = actions.onOpenLockClockGuide)
            }

            SectionTitle("Slideshow", "Tap a tile to change it")
            SlideshowTiles(state, actions)

            SectionTitle("Explore", "Wallpapers made for the depth clock")
            Box(Modifier.padding(horizontal = 18.dp)) {
                GalleryRow(
                    included = media.toSet(),
                    uriOf = actions.builtInUri,
                    onToggle = actions.onToggleBuiltIn,
                )
            }

            Text(
                if (isSamsung) {
                    "Tip: your phone's own lock screen clock can be made invisible: touch and hold the lock " +
                        "screen, tap the clock, and set its colour to fully transparent."
                } else {
                    "Tip: on the lock screen, your phone's own clock is drawn on top. If your phone lets you, " +
                        "make it small or hide it for the cleanest look."
                },
                color = Color.White.copy(alpha = 0.45f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            )
        }
    }
}

/** Slowly drifting brand-coloured glows behind the top of the screen. */
@Composable
private fun GlowBackground() {
    val transition = rememberInfiniteTransition(label = "glow")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "glow phase",
    )
    Canvas(Modifier.fillMaxWidth().height(620.dp)) {
        fun glow(color: Color, x: Float, y: Float, radius: Float) {
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = 0.38f), Color.Transparent), Offset(x, y), radius),
                radius = radius,
                center = Offset(x, y),
            )
        }
        val w = size.width
        val h = size.height
        glow(Brand.Indigo, w * (0.2f + 0.08f * sin(t)), h * (0.25f + 0.05f * cos(t)), w * 0.75f)
        glow(Brand.Violet, w * (0.85f + 0.06f * cos(t * 2)), h * (0.4f + 0.06f * sin(t)), w * 0.7f)
        glow(Brand.Coral, w * (0.5f + 0.1f * sin(t + 2f)), h * (0.75f + 0.04f * cos(t * 2)), w * 0.55f)
    }
}

@Composable
private fun Header(count: Int, active: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brand.gradient),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.45f, scaleY = 1.45f),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Wallpaper Slideshow", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) {
                    Box(Modifier.size(8.dp).background(Color(0xFF5EE29A), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("Live", color = Color(0xFF5EE29A), style = MaterialTheme.typography.labelMedium)
                    Text(" · ", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    when (count) {
                        0 -> "No wallpapers yet"
                        1 -> "1 wallpaper"
                        else -> "$count wallpapers"
                    },
                    color = Muted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        BouncyBox(
            onClick = onAdd,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brand.gradient)
                .semantics { contentDescription = "Add photos and videos" },
        ) {
            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Swipeable live previews of the wallpapers; the centre one is full size. */
@Composable
private fun Carousel(
    pager: PagerState,
    state: HomeState,
    loader: MediaInfoLoader,
    onAdd: () -> Unit,
    onEdit: (String?) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val pageWidth = (maxWidth * 0.62f).coerceAtMost(300.dp)
        val sidePadding = (maxWidth - pageWidth) / 2
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = sidePadding),
            pageSpacing = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    val scale = 1f - 0.12f * offset
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.45f * offset
                },
            ) {
                val uri = state.settings.media.getOrNull(page)
                if (uri == null) {
                    AddCard(width = pageWidth, onClick = onAdd)
                } else {
                    WallpaperCard(uri, page, state, loader, width = pageWidth, onClick = { onEdit(uri) })
                }
            }
        }
    }
}

@Composable
private fun WallpaperCard(
    uri: String,
    index: Int,
    state: HomeState,
    loader: MediaInfoLoader,
    width: Dp,
    onClick: () -> Unit,
) {
    val info by rememberMediaInfo(uri, loader)
    var large by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) { large = loader.loadLarge(uri, maxSide = 900) }
    val look = state.settings.lookFor(uri)
    val isPhoto = info?.kind == MediaKind.IMAGE
    val depthState = state.depthStates[uri]
    val mask by rememberAlphaMask(uri, isPhoto && depthState == DepthMasks.State.READY && look.clock.enabled)
    val use24Hour = when (look.clock.timeFormat) {
        TimeFormat.SYSTEM -> state.systemUses24Hour
        TimeFormat.H12 -> false
        TimeFormat.H24 -> true
    }

    Box(
        Modifier
            .width(width)
            .aspectRatio(9f / 19.5f)
            .clip(RoundedCornerShape(30.dp))
            .background(Brand.Ink)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(30.dp))
            .clickable(onClickLabel = "Edit this wallpaper", onClick = onClick),
    ) {
        DepthPreview(
            photo = large ?: info?.thumbnail,
            mask = mask,
            clock = look.clock,
            use24Hour = use24Hour,
            manualOffset = if (isPhoto) state.settings.clockOffsets[uri] else null,
            manualFraming = if (isPhoto) state.settings.photoFramings[uri] else null,
            scaleMode = state.settings.scaleMode,
            widgets = look.widgets,
            widgetSnapshot = state.widgetSnapshot,
            modifier = Modifier.fillMaxSize(),
        )
        // Position number, and what kind of item it is.
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                info?.name ?: "Wallpaper ${index + 1}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Badge(
                icon = if (isPhoto) AppIcons.Image else Icons.Default.PlayArrow,
                text = when {
                    depthState == DepthMasks.State.PROCESSING ->
                        state.depthProgress[uri]?.let { "${(it * 100).toInt()}%" } ?: "…"
                    isPhoto -> "Photo"
                    else -> info?.durationMs?.let { "%d:%02d".format(it / 60_000, it / 1000 % 60) } ?: "Video"
                },
            )
            if (depthState == DepthMasks.State.READY && look.clock.enabled) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(26.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .semantics { contentDescription = "Depth effect" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Layers, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, text: String) {
    Row(
        Modifier
            .background(Color.White.copy(alpha = 0.2f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AddCard(width: Dp, onClick: () -> Unit) {
    BouncyBox(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .aspectRatio(9f / 19.5f)
            .clip(RoundedCornerShape(30.dp))
            .background(CardSurface)
            .border(2.dp, Brush.linearGradient(listOf(Brand.Indigo, Brand.Violet, Brand.Coral)), RoundedCornerShape(30.dp)),
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).background(Brand.gradient, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Add photos\n& videos", color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PageDots(pager: PagerState) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pager.pageCount) { page ->
            val selected = page == pager.currentPage
            val width by animateFloatAsState(if (selected) 18f else 6f, label = "dot")
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width.dp, height = 6.dp)
                    .background(if (selected) Color.White else Color.White.copy(alpha = 0.3f), CircleShape),
            )
        }
    }
}

/** Edit / move / remove the wallpaper in the middle of the carousel. */
@Composable
private fun ItemActions(
    enabled: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onEdit: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, label = "actions")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BouncyBox(
            onClick = { if (enabled) onEdit() },
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(CircleShape)
                .background(Color.White),
        ) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, null, tint = Brand.Indigo, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Edit look", color = Brand.Indigo, style = MaterialTheme.typography.titleSmall)
            }
        }
        RoundAction(Icons.Default.KeyboardArrowLeft, "Play earlier", enabled && canMoveLeft, onMoveLeft)
        RoundAction(Icons.Default.KeyboardArrowRight, "Play later", enabled && canMoveRight, onMoveRight)
        RoundAction(Icons.Default.Delete, "Remove", enabled, onRemove)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    BouncyBox(
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(CardSurface)
            .border(1.dp, CardBorder, CircleShape)
            .semantics { contentDescription = label },
    ) {
        Icon(
            icon, null,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun ApplyButton(active: Boolean, onClick: () -> Unit) {
    BouncyBox(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brand.gradient),
    ) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (active) Icons.Default.CheckCircle else AppIcons.Wallpaper,
                null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (active) "Live on your phone · Preview" else "Set as wallpaper",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp)) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(hint, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

/** The slideshow settings at a glance; tapping opens the full settings. */
@Composable
private fun SlideshowTiles(state: HomeState, actions: HomeActions) {
    val s = state.settings
    val clockOn = s.allLooks.any { it.clock.enabled }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoTile(AppIcons.Shuffle, "Order", if (s.shuffle) "Shuffled" else "In order", Brand.Indigo, Modifier.weight(1f), actions.onOpenSlideshowSettings)
            InfoTile(
                AppIcons.Timer, "Timing",
                "Photos ${s.imageSeconds}s · " + if (s.clipSeconds == 0) "videos full" else "videos ${s.clipSeconds}s",
                Brand.Violet, Modifier.weight(1f), actions.onOpenSlideshowSettings,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoTile(
                AppIcons.Animation, "Transition",
                if (s.transitionMs > 0) "${s.transition.label} · ${"%.1f".format(s.transitionMs / 1000f)}s" else s.transition.label,
                Brand.Coral, Modifier.weight(1f), actions.onOpenSlideshowSettings,
            )
            InfoTile(
                AppIcons.VolumeUp, "Sound",
                (if (s.muted) "Off" else "On") + " · ${"%.2g".format(s.speed)}×",
                Color(0xFF3FB6C9), Modifier.weight(1f), actions.onOpenSlideshowSettings,
            )
        }
        // Clock master switch.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                .clickable(role = Role.Switch) { actions.onClockEverywhere(!clockOn) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileIcon(AppIcons.Schedule, Color(0xFF5EE29A))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Depth clock & widgets", color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (clockOn) "Subjects stand in front of the time" else "Show a big clock on your wallpapers",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = clockOn,
                onCheckedChange = actions.onClockEverywhere,
                colors = SwitchDefaults.colors(checkedTrackColor = Brand.Violet),
            )
        }
        // How the home screen differs from the lock screen.
        BouncyBox(
            onClick = actions.onOpenHomeScreenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(22.dp)),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TileIcon(Icons.Filled.Home, Color(0xFFFFC94D))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Home screen look", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    Text(s.home.summary(), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Filled.KeyboardArrowRight, null, tint = Muted)
            }
        }
    }
}

@Composable
private fun InfoTile(
    icon: ImageVector,
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    BouncyBox(
        onClick = onClick,
        modifier = modifier
            .height(104.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(CardSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(22.dp)),
    ) {
        Column(Modifier.padding(14.dp)) {
            TileIcon(icon, accent)
            Spacer(Modifier.weight(1f))
            Text(title, color = Muted, style = MaterialTheme.typography.labelMedium)
            Text(value, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TileIcon(icon: ImageVector, accent: Color) {
    Box(
        Modifier
            .size(36.dp)
            .background(accent.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
    }
}

/** A friendly start when there are no wallpapers yet. */
@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Three fanned phone shapes, like the app icon.
        Box(Modifier.height(300.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            listOf(-12f to 0.35f, 10f to 0.6f, 0f to 1f).forEachIndexed { i, (rotation, alpha) ->
                Box(
                    Modifier
                        .width(140.dp)
                        .aspectRatio(9f / 19.5f)
                        .graphicsLayer {
                            rotationZ = rotation
                            translationX = (i - 1) * 28f
                            this.alpha = alpha
                        }
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brand.gradient)
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp)),
                )
            }
            Text(
                "9:41",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Light,
            )
        }
        Text(
            "Make your first slideshow",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Add photos and videos, or pick a wallpaper from Explore below. Each one gets its own clock and widgets.",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        BouncyBox(
            onClick = onAdd,
            modifier = Modifier
                .height(56.dp)
                .clip(CircleShape)
                .background(Brand.gradient)
                .padding(horizontal = 28.dp),
        ) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Add photos & videos", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A clickable box that shrinks a little while pressed. */
@Composable
private fun BouncyBox(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    // The scale goes first, so the caller's background and shape shrink along with the content.
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(modifier)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        content = content,
    )
}
