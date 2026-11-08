package com.sadikbr.videowallpaper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sadikbr.videowallpaper.data.ScaleMode
import com.sadikbr.videowallpaper.data.TransitionStyle
import com.sadikbr.videowallpaper.data.WallpaperSettings
import com.sadikbr.videowallpaper.ui.theme.AppIcons
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaybackCard(
    settings: WallpaperSettings,
    onShuffleChange: (Boolean) -> Unit,
    onMutedChange: (Boolean) -> Unit,
    onClipSecondsChange: (Int) -> Unit,
    onImageSecondsChange: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onScaleModeChange: (ScaleMode) -> Unit,
    onTransitionChange: (TransitionStyle) -> Unit,
    onTransitionMsChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            SettingRow(
                icon = AppIcons.Shuffle,
                title = "Shuffle",
                subtitle = if (settings.shuffle) "Random order" else "In the order shown above",
            ) {
                Switch(checked = settings.shuffle, onCheckedChange = onShuffleChange)
            }
            ItemDivider()

            SettingRow(
                icon = AppIcons.VolumeUp,
                title = "Sound",
                subtitle = if (settings.muted) "Muted" else "Plays the videos' audio",
            ) {
                Switch(checked = !settings.muted, onCheckedChange = { onMutedChange(!it) })
            }
            ItemDivider()

            val playInFull = settings.clipSeconds == 0
            SettingRow(
                icon = AppIcons.Timer,
                title = "Play each video in full",
                subtitle = if (playInFull) "Next video starts when one ends" else "Next video every ${settings.clipSeconds}s",
            ) {
                Switch(
                    checked = playInFull,
                    onCheckedChange = { full ->
                        onClipSecondsChange(if (full) 0 else WallpaperSettings.DEFAULT_CLIP_SECONDS)
                    },
                )
            }
            if (!playInFull) {
                StepSlider(
                    options = WallpaperSettings.CLIP_SECONDS_OPTIONS,
                    selected = settings.clipSeconds,
                    label = { "${it}s" },
                    onSelected = onClipSecondsChange,
                )
            }
            ItemDivider()

            SettingRow(
                icon = AppIcons.Image,
                title = "Photo duration",
                subtitle = "Each photo stays on screen for ${settings.imageSeconds}s",
            )
            StepSlider(
                options = WallpaperSettings.IMAGE_SECONDS_OPTIONS,
                selected = settings.imageSeconds,
                label = { "${it}s" },
                onSelected = onImageSecondsChange,
            )
            ItemDivider()

            SettingRow(
                icon = AppIcons.Speed,
                title = "Video speed",
                subtitle = "${formatSpeed(settings.speed)}×",
            )
            StepSlider(
                options = WallpaperSettings.SPEED_OPTIONS,
                selected = settings.speed,
                label = { "${formatSpeed(it)}×" },
                onSelected = onSpeedChange,
            )
            ItemDivider()

            SettingRow(
                icon = if (settings.scaleMode == ScaleMode.CROP) AppIcons.Crop else AppIcons.FitScreen,
                title = "Fit to screen",
                subtitle = when (settings.scaleMode) {
                    ScaleMode.CROP -> "Fills the screen; edges may be cut off"
                    ScaleMode.FIT -> "Shows the whole photo or video, with black bars"
                },
            )
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dp, end = 16.dp, bottom = 12.dp)
            ) {
                val options = listOf(ScaleMode.CROP to "Fill screen", ScaleMode.FIT to "Show all")
                options.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = settings.scaleMode == mode,
                        onClick = { onScaleModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) {
                        Text(label)
                    }
                }
            }
            ItemDivider()

            SettingRow(
                icon = AppIcons.Animation,
                title = "Transition",
                subtitle = if (settings.transition == TransitionStyle.NONE) {
                    "Cut straight to the next item"
                } else {
                    "How each item gives way to the next"
                },
            )
            FlowRow(
                modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TransitionStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.transition == style,
                        onClick = { onTransitionChange(style) },
                        label = { Text(style.label) },
                    )
                }
            }
            if (settings.transition != TransitionStyle.NONE) {
                StepSlider(
                    options = WallpaperSettings.TRANSITION_MS_OPTIONS,
                    selected = settings.transitionMs,
                    label = { formatSeconds(it) },
                    onSelected = onTransitionMsChange,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatSeconds(ms: Int): String {
    val seconds = ms / 1000f
    return if (seconds % 1f == 0f) "${seconds.toInt()}s" else "${seconds}s"
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * A slider that snaps to [options]. The value is only saved when the finger is lifted,
 * so dragging doesn't restart the wallpaper on every step.
 */
@Composable
private fun <T> StepSlider(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    var position by remember(selectedIndex) { mutableFloatStateOf(selectedIndex.toFloat()) }
    Column(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 4.dp)) {
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onSelected(options[position.roundToInt()]) },
            valueRange = 0f..options.lastIndex.toFloat(),
            steps = (options.size - 2).coerceAtLeast(0),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(label(options.first()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                label(options[position.roundToInt()]),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(label(options.last()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ItemDivider() {
    HorizontalDivider(
        Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
