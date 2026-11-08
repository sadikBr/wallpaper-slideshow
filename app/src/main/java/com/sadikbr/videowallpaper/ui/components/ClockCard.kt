package com.sadikbr.videowallpaper.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sadikbr.videowallpaper.data.ClockSettings
import com.sadikbr.videowallpaper.data.TimeFormat
import com.sadikbr.videowallpaper.ui.theme.AppIcons

/**
 * Settings for the wallpaper's own clock and the depth effect.
 *
 * @param depthSummary e.g. "4 of 6 photos ready", or null when there's nothing to report.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClockCard(
    clock: ClockSettings,
    depthSummary: String?,
    onUpdate: ((ClockSettings) -> ClockSettings) -> Unit,
    onCustomize: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            SettingRow(
                icon = AppIcons.Schedule,
                title = "Depth clock",
                subtitle = if (clock.enabled) {
                    depthSummary ?: "Subjects in your photos stand in front of the time"
                } else {
                    "Show a big clock that photo subjects can stand in front of"
                },
            ) {
                Switch(checked = clock.enabled, onCheckedChange = { on -> onUpdate { it.copy(enabled = on) } })
            }
            if (!clock.enabled) return@Column

            ItemDivider()
            Button(
                onClick = onCustomize,
                modifier = Modifier
                    .padding(start = 72.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                    .fillMaxWidth(),
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Customize clock & photos")
            }
            Text(
                "Drag, pinch and stretch the clock right on your wallpaper; fonts, colours, glass and clock options are all in there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 4.dp),
            )
            Row(
                Modifier
                    .padding(start = 72.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Your phone's own lock-screen clock is still shown on top. To avoid two clocks, " +
                        "hide or shrink it (Samsung: Good Lock › LockStar).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
