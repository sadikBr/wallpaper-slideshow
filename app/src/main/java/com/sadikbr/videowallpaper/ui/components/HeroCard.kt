package com.sadikbr.videowallpaper.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.sadikbr.videowallpaper.data.DepthMasks
import com.sadikbr.videowallpaper.data.MediaKind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sadikbr.videowallpaper.data.ScaleMode
import com.sadikbr.videowallpaper.data.MediaInfoLoader
import com.sadikbr.videowallpaper.data.WallpaperSettings
import com.sadikbr.videowallpaper.ui.theme.AppIcons
import com.sadikbr.videowallpaper.ui.theme.Brand
import androidx.compose.material3.ButtonDefaults

/** Top card: a phone-shaped preview, the wallpaper's status, and the main action. */
@Composable
fun HeroCard(
    settings: WallpaperSettings,
    loader: MediaInfoLoader,
    wallpaperActive: Boolean,
    use24Hour: Boolean,
    depthStates: Map<String, DepthMasks.State>,
    onApply: () -> Unit,
) {
    val hasMedia = settings.media.isNotEmpty()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
        ),
    ) {
        Row(
            Modifier
                .background(Brand.gradient)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val first = settings.media.firstOrNull()
            PhonePreview(
                uri = first,
                loader = loader,
                settings = settings,
                use24Hour = use24Hour,
                maskReady = first != null && depthStates[first] == DepthMasks.State.READY,
                modifier = Modifier.width(96.dp),
            )
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(active = wallpaperActive)
                Text(
                    if (hasMedia) "Your slideshow" else "Get started",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    if (hasMedia) summary(settings, loader) else "Add photos or videos below, then set the slideshow as your wallpaper.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onApply,
                    enabled = hasMedia,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Brand.Indigo,
                        disabledContainerColor = Color.White.copy(alpha = 0.3f),
                        disabledContentColor = Color.White.copy(alpha = 0.7f),
                    ),
                ) {
                    Icon(AppIcons.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (wallpaperActive) "Preview" else "Set as wallpaper")
                }
            }
        }
    }
}

@Composable
private fun PhonePreview(
    uri: String?,
    loader: MediaInfoLoader,
    settings: WallpaperSettings,
    use24Hour: Boolean,
    maskReady: Boolean,
    modifier: Modifier = Modifier,
) {
    val info by rememberMediaInfo(uri, loader)
    val mask by rememberAlphaMask(uri, maskReady && settings.clock.enabled)
    val thumbnail = info?.thumbnail
    Box(
        modifier
            .aspectRatio(9f / 19f)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101318))
            .border(4.dp, Color(0xFF2A2F3A), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null || settings.clock.enabled) {
            // Mirrors the "fit to screen" and clock settings so their effect is visible here.
            DepthPreview(
                photo = thumbnail,
                mask = mask,
                clock = settings.clock,
                use24Hour = use24Hour,
                manualOffset = uri?.let { settings.clockOffsets[it] },
                scaleMode = settings.scaleMode,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .semantics { contentDescription = "Preview of the wallpaper" },
            )
        } else {
            Icon(
                AppIcons.VideoLibrary,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp),
            )
        }
        // Camera punch-hole, for a phone-like look.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .size(6.dp)
                .background(Color.Black, CircleShape)
        )
    }
}

@Composable
private fun StatusPill(active: Boolean) {
    val background = if (active) Color.White else Color.White.copy(alpha = 0.2f)
    val content = if (active) Brand.Indigo else Color.White
    Row(
        Modifier
            .background(background, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            if (active) "Active wallpaper" else "Not set yet",
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

private fun summary(settings: WallpaperSettings, loader: MediaInfoLoader): String {
    // Kinds come from the info already loaded for the grid; unknown items count as videos.
    val photos = settings.media.count { loader.cached(it)?.kind == MediaKind.IMAGE }
    val videos = settings.media.size - photos
    return buildList {
        if (videos > 0) add(if (videos == 1) "1 video" else "$videos videos")
        if (photos > 0) add(if (photos == 1) "1 photo" else "$photos photos")
        if (settings.shuffle) add("shuffled")
        if (!settings.muted && videos > 0) add("with sound")
    }.joinToString(" · ")
}
