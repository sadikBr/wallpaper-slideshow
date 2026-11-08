package com.sadikbr.videowallpaper.ui.home

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadikbr.videowallpaper.ui.theme.Brand

/** Samsung phones get the step-by-step guide; Android has no way for apps to hide that clock. */
val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

private val STEPS = listOf(
    "Lock your phone, wake the screen, then touch and hold an empty spot on the lock screen. Unlock if asked: the lock screen editor opens.",
    "Tap the clock.",
    "Pick a simple single-colour clock style.",
    "Tap the colour, open the custom colour picker and slide the transparency all the way, so the clock becomes invisible.",
    "Tap Done. Now only your depth clock shows.",
)

/** A banner on the home screen offering the guide. */
@Composable
fun LockClockBanner(onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Brand.Coral.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TwoClocksIcon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Two clocks on your lock screen?", color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text(
                "Make Samsung's clock invisible in about a minute",
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White)
    }
}

/** A small picture of a solid clock over a faded one. */
@Composable
private fun TwoClocksIcon() {
    Box(
        Modifier
            .size(44.dp)
            .background(Brand.Coral.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("12", color = Color.White.copy(alpha = 0.35f), fontSize = 22.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
        Text("12", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp, bottom = 6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockClockGuideSheet(onOpenSettings: () -> Unit, onDone: () -> Unit, onLater: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onLater,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF15122C),
    ) {
        LockClockGuideContent(onOpenSettings, onDone, onLater)
    }
}

/** The guide itself: why, the steps, and the buttons. */
@Composable
fun LockClockGuideContent(onOpenSettings: () -> Unit, onDone: () -> Unit, onLater: () -> Unit) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        BeforeAfter()
        Spacer(Modifier.height(18.dp))
        Text(
            "Hide Samsung's lock screen clock",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Android doesn't let apps remove it, but you can make it invisible, so only your depth clock shows. " +
                "You only need to do this once.",
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(18.dp))
        STEPS.forEachIndexed { index, step ->
            Row(Modifier.padding(vertical = 7.dp)) {
                Box(
                    Modifier.size(28.dp).background(Brand.gradient, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(14.dp))
                Text(step, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(CircleShape)
                .background(Brand.gradient)
                .clickable(role = Role.Button, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text("Done, it's hidden", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onOpenSettings) { Text("Open Settings", color = Color.White) }
            TextButton(onClick = onLater) { Text("Later", color = Color.White.copy(alpha = 0.7f)) }
        }
    }
}

/** Before (two clocks) and after (only the depth clock), as two little phones. */
@Composable
private fun BeforeAfter() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
        MiniPhone(label = "Before", systemClock = true)
        Box(Modifier.height(170.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
        }
        MiniPhone(label = "After", systemClock = false)
    }
}

@Composable
private fun MiniPhone(label: String, systemClock: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .height(170.dp)
                .aspectRatio(9f / 19.5f)
                .clip(RoundedCornerShape(18.dp))
                .background(Brand.gradient)
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (systemClock) {
                    Text("12:45", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "12:45",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = if (systemClock) 2.dp else 18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
    }
}
