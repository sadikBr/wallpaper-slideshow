package com.sadikbr.videowallpaper.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.sadikbr.videowallpaper.data.BuiltInWallpapers
import com.sadikbr.videowallpaper.ui.theme.AppIcons
import com.sadikbr.videowallpaper.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The built-in wallpapers, as a strip of phone-shaped cards. Tap to add or remove one. */
@Composable
fun GalleryRow(
    included: Set<String>,
    uriOf: (BuiltInWallpapers.Wallpaper) -> String,
    onToggle: (BuiltInWallpapers.Wallpaper) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(BuiltInWallpapers.all, key = { it.id }) { wallpaper ->
            GalleryCard(
                wallpaper = wallpaper,
                selected = uriOf(wallpaper) in included,
                onClick = { onToggle(wallpaper) },
            )
        }
    }
}

@Composable
private fun GalleryCard(wallpaper: BuiltInWallpapers.Wallpaper, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    var preview by remember(wallpaper.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(wallpaper.id) {
        preview = withContext(Dispatchers.IO) { BuiltInWallpapers.loadPreview(context, wallpaper) }
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .size(width = 112.dp, height = 236.dp)
            .clip(shape)
            .background(Brand.Ink)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics {
                contentDescription = wallpaper.name
                stateDescription = if (selected) "In your slideshow" else "Not in your slideshow"
            },
    ) {
        preview?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(wallpaper.name, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.weight(1f))
                if (wallpaper.isVideo) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Animated", tint = Color.White, modifier = Modifier.size(16.dp))
                } else if (wallpaper.hasDepth) {
                    Spacer(Modifier.width(4.dp))
                    Icon(AppIcons.Layers, contentDescription = "Depth effect", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(26.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (selected) Icons.Default.Check else Icons.Default.Add,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
