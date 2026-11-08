package com.sadikbr.videowallpaper.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sadikbr.videowallpaper.data.DepthMasks
import com.sadikbr.videowallpaper.data.HomeEffect
import com.sadikbr.videowallpaper.data.HomeScreenSettings
import com.sadikbr.videowallpaper.data.MediaInfoLoader
import com.sadikbr.videowallpaper.data.MediaKind
import com.sadikbr.videowallpaper.data.TimeFormat
import com.sadikbr.videowallpaper.data.WallpaperSettings
import com.sadikbr.videowallpaper.data.WidgetSnapshot
import com.sadikbr.videowallpaper.ui.components.DepthPreview
import com.sadikbr.videowallpaper.ui.components.rememberAlphaMask
import com.sadikbr.videowallpaper.ui.components.rememberMediaInfo
import com.sadikbr.videowallpaper.ui.theme.Brand

/** A short description of the home screen look, for the tile on the home page. */
fun HomeScreenSettings.summary(): String {
    if (sameAsLock) return "Same as lock screen"
    val parts = buildList {
        when {
            !showClock && !showWidgets -> add("No clock or widgets")
            !showClock -> add("No clock")
            !showWidgets -> add("No widgets")
        }
        when (effect) {
            HomeEffect.NONE -> Unit
            HomeEffect.DIM -> add("dimmed")
            HomeEffect.BLUR -> add("blurred")
        }
    }
    return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
}

/**
 * How the wallpaper changes once the phone is unlocked, so the home screen and lock screen are
 * easy to tell apart: the clock and widgets can fade away, and the picture can be dimmed or blurred.
 * Shows both screens side by side with the item [uri].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenSheet(
    settings: WallpaperSettings,
    uri: String?,
    loader: MediaInfoLoader,
    depthStates: Map<String, DepthMasks.State>,
    widgetSnapshot: WidgetSnapshot,
    systemUses24Hour: Boolean,
    onChange: (HomeScreenSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF15122C),
    ) {
        HomeScreenContent(settings, uri, loader, depthStates, widgetSnapshot, systemUses24Hour, onChange)
    }
}

@Composable
fun HomeScreenContent(
    settings: WallpaperSettings,
    uri: String?,
    loader: MediaInfoLoader,
    depthStates: Map<String, DepthMasks.State>,
    widgetSnapshot: WidgetSnapshot,
    systemUses24Hour: Boolean,
    onChange: (HomeScreenSettings) -> Unit,
) {
    val home = settings.home
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Text(
            "Home screen",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Text(
            "Make your home screen look different from your lock screen, so you always know where you are. " +
                "It changes smoothly as you unlock.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            ScreenPreview(Icons.Filled.Lock, "Lock screen", settings, uri, loader, depthStates, widgetSnapshot, systemUses24Hour, null, Modifier.weight(1f))
            ScreenPreview(Icons.Filled.Home, "Home screen", settings, uri, loader, depthStates, widgetSnapshot, systemUses24Hour, home, Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))
        SwitchRow("Clock on home screen", "Otherwise it fades away when you unlock", home.showClock) {
            onChange(home.copy(showClock = it))
        }
        SwitchRow("Widgets on home screen", "Weather, battery, calendar and signature", home.showWidgets) {
            onChange(home.copy(showWidgets = it))
        }
        Spacer(Modifier.height(10.dp))
        Text("Picture behind your apps", style = MaterialTheme.typography.labelLarge, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f)),
        ) {
            HomeEffect.entries.forEach { effect ->
                val selected = effect == home.effect
                Box(
                    Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (selected) Brand.Coral else Color.Transparent)
                        .clickable(role = Role.RadioButton) { onChange(home.copy(effect = effect)) }
                        .semantics { this.selected = selected }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(effect.label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Brand.Violet),
        )
    }
}

/** A small phone showing the lock screen look ([home] null) or the home screen look. */
@Composable
private fun ScreenPreview(
    icon: ImageVector,
    label: String,
    settings: WallpaperSettings,
    uri: String?,
    loader: MediaInfoLoader,
    depthStates: Map<String, DepthMasks.State>,
    widgetSnapshot: WidgetSnapshot,
    systemUses24Hour: Boolean,
    home: HomeScreenSettings?,
    modifier: Modifier = Modifier,
) {
    val info by rememberMediaInfo(uri, loader)
    var large by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) { if (uri != null) large = loader.loadLarge(uri, maxSide = 700) }
    val look = settings.lookFor(uri)
    val showClock = home?.showClock ?: true
    val showWidgets = home?.showWidgets ?: true
    val isPhoto = info?.kind == MediaKind.IMAGE
    val mask by rememberAlphaMask(
        uri,
        isPhoto && uri != null && depthStates[uri] == DepthMasks.State.READY && look.clock.enabled && showClock,
    )
    val use24Hour = when (look.clock.timeFormat) {
        TimeFormat.SYSTEM -> systemUses24Hour
        TimeFormat.H12 -> false
        TimeFormat.H24 -> true
    }
    val effect = home?.effect ?: HomeEffect.NONE

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 19.5f)
                .clip(RoundedCornerShape(22.dp))
                .background(Brand.Ink)
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp)),
        ) {
            DepthPreview(
                photo = large ?: info?.thumbnail,
                mask = if (effect == HomeEffect.BLUR) null else mask,
                clock = if (showClock) look.clock else look.clock.copy(enabled = false),
                use24Hour = use24Hour,
                manualOffset = if (isPhoto) settings.clockOffsets[uri] else null,
                manualFraming = if (isPhoto) settings.photoFramings[uri] else null,
                scaleMode = settings.scaleMode,
                widgets = look.widgets.takeIf { showWidgets },
                widgetSnapshot = widgetSnapshot,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (effect == HomeEffect.BLUR) Modifier.blur(10.dp) else Modifier),
            )
            val dim = when (effect) {
                HomeEffect.NONE -> 0f
                HomeEffect.DIM -> 0.45f
                HomeEffect.BLUR -> 0.15f
            }
            if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
            if (home != null) AppIconGrid(Modifier.align(Alignment.BottomCenter))
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Placeholder app icons, so the home screen preview reads as one. */
@Composable
private fun AppIconGrid(modifier: Modifier = Modifier) {
    val colors = listOf(Brand.Coral, Color(0xFF5EE29A), Brand.Indigo, Color(0xFF3FB6C9), Brand.Violet, Color(0xFFFFC94D), Color(0xFFE25E8A), Color(0xFF6E8BFF))
    Column(
        modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until 4) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors[row * 4 + col].copy(alpha = 0.9f)),
                    )
                }
            }
        }
    }
}
