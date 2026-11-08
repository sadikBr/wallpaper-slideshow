package com.sadikbr.videowallpaper.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sadikbr.videowallpaper.data.VideoInfoLoader

/** One video in the grid: its preview frame, position and length. Tap for actions. */
@Composable
fun VideoTile(
    position: Int,
    uri: String,
    loader: VideoInfoLoader,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info by rememberVideoInfo(uri, loader)
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)

    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { menuOpen = true },
        ) {
            val current = info
            when {
                current == null -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                    strokeWidth = 2.dp,
                )
                !current.available -> Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        "Can't open\n(will be skipped)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                    )
                }
                current.thumbnail != null -> Image(
                    bitmap = current.thumbnail.asImageBitmap(),
                    contentDescription = current.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Bottom shade so the text stays readable on any frame.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))
                    )
                    .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 6.dp),
            ) {
                Text(
                    info?.durationMs?.let(::formatDuration) ?: info?.name.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$position", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }

            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Video options",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(3.dp),
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            info?.name?.let { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            DropdownMenuItem(
                text = { Text("Play earlier") },
                leadingIcon = { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null) },
                enabled = canMoveEarlier,
                onClick = { menuOpen = false; onMoveEarlier() },
            )
            DropdownMenuItem(
                text = { Text("Play later") },
                leadingIcon = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) },
                enabled = canMoveLater,
                onClick = { menuOpen = false; onMoveLater() },
            )
            DropdownMenuItem(
                text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = { menuOpen = false; onRemove() },
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
