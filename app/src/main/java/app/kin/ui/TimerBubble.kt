package app.kin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kin.domain.Evolution
import app.kin.domain.Rewards
import app.kin.services.BreakNotifications

@Composable
fun TimerBubble(
    focus: Boolean,
    paused: Boolean,
    due: Boolean,
    remaining: Long,
    stage: Evolution,
    onTap: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDrop: () -> Unit,
    widthDp: Float = 116f,
    heightDp: Float = 64f
) {
    Surface(
        Modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .semantics {
                contentDescription = if (focus) "Open focus timer" else "Open break timer"
                stateDescription = if (due) "Break over" else "${BreakNotifications.format(remaining)}${if (paused) ", paused" else ""}"
            }
            .clickable(onClick = onTap)
            .pointerInput(Unit) {
                detectDragGestures(onDragEnd = onDrop, onDragCancel = onDrop) { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = BorderStroke(
            1.5.dp,
            when {
                due -> MaterialTheme.colorScheme.error
                focus -> MaterialTheme.colorScheme.primary
                else -> KinPalette.current.warning
            }
        )
    ) {
        Row(
            Modifier.clearAndSetSemantics { }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompanionArt(stage, true, Modifier.size(38.dp), if (focus) CreatureMood.SLEEPING else CreatureMood.WAVING)
            Column {
                Text(
                    if (due) "RETURN" else if (paused) "PAUSED" else if (focus) "FOCUS" else "BREAK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (due) "00:00" else BreakNotifications.format(remaining),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = if (due) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TimerControls(
    focus: Boolean,
    paused: Boolean,
    title: String,
    remaining: Long,
    maxHeight: Float,
    focusDurationMillis: Long = 0L,
    onCollapse: () -> Unit,
    onPause: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, KinPalette.current.cardBorder)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight.dp)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (focus) "Focus timer" else "Scrolling break", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onCollapse) { Text("Collapse") }
            }
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(BreakNotifications.format(remaining), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                if (focus && focusDurationMillis > 0L) {
                    XpBadge("+${Rewards.focusForMillis(focusDurationMillis)} XP")
                }
            }
            if (paused) Text(if (focus) "Focus paused" else "Break paused", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onPause, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
                Icon(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (paused) "Resume" else "Pause")
            }
            if (!focus) OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
                Text("Start task")
            }
            TextButton(onClick = onStop, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (focus) "Finish early" else "End break", color = MaterialTheme.colorScheme.error)
            }
            Text("Tap outside to return to the icon.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
