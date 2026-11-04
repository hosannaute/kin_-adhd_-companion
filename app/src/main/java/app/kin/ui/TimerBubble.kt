package app.kin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kin.domain.Evolution
import app.kin.services.BreakNotifications

@Composable
fun TimerBubble(focus: Boolean, paused: Boolean, due: Boolean, remaining: Long, stage: Evolution,
                onTap: () -> Unit, onDrag: (Float, Float) -> Unit, onDrop: () -> Unit, widthDp: Float = 112f, heightDp: Float = 64f) {
    Surface(Modifier.width(widthDp.dp).height(heightDp.dp).semantics {
        contentDescription = if (focus) "Open focus timer" else "Open break timer"
        stateDescription = if (due) "Break over" else "${BreakNotifications.format(remaining)}${if (paused) ", paused" else ""}"
    }.clickable(onClick = onTap).pointerInput(Unit) {
        detectDragGestures(onDragEnd = onDrop, onDragCancel = onDrop) { change, amount -> change.consume(); onDrag(amount.x, amount.y) }
    }, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (due) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
        Row(Modifier.clearAndSetSemantics { }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CompanionArt(stage, true, Modifier.size(38.dp), if (focus) CreatureMood.SLEEPING else CreatureMood.WAVING)
            Column {
                Text(if (due) "RETURN" else if (paused) "PAUSED" else if (focus) "FOCUS" else "BREAK",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (due) "00:00" else BreakNotifications.format(remaining), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TimerControls(focus: Boolean, paused: Boolean, title: String, remaining: Long, maxHeight: Float,
                  onCollapse: () -> Unit, onPause: () -> Unit, onStart: () -> Unit, onStop: () -> Unit) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight.dp).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (focus) "Focus timer" else "Scrolling break", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton(onClick = onCollapse) { Text("Collapse") }
            }
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(BreakNotifications.format(remaining), style = MaterialTheme.typography.displaySmall)
            if (paused) Text(if (focus) "Focus paused" else "Break paused")
            Button(onClick = onPause, modifier = Modifier.fillMaxWidth()) { Text(if (paused) "Resume" else "Pause") }
            if (!focus) OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start task") }
            TextButton(onClick = onStop) { Text(if (focus) "Finish early" else "End break") }
            Text("Tap outside to return to the icon.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
