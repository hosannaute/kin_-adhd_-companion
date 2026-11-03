package app.kin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
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
                onTap: () -> Unit, onDrag: (Float, Float) -> Unit, onDrop: () -> Unit) {
    Surface(Modifier.size(68.dp).semantics {
        contentDescription = if (focus) "Open focus timer" else "Open break timer"
        stateDescription = if (due) "Break over" else "${BreakNotifications.format(remaining)}${if (paused) ", paused" else ""}"
    }.clickable(onClick = onTap).pointerInput(Unit) {
        detectDragGestures(onDragEnd = onDrop, onDragCancel = onDrop) { change, amount -> change.consume(); onDrag(amount.x, amount.y) }
    }, shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, if (due) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
        Box(Modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            CompanionArt(stage, true, Modifier.size(56.dp), if (focus) CreatureMood.SLEEPING else CreatureMood.WAVING)
            Text(if (due) "!" else if (paused) "Ⅱ" else if (focus) "Focus" else "Break",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TimerControls(focus: Boolean, paused: Boolean, title: String, remaining: Long, maxHeight: Float,
                  onCollapse: () -> Unit, onPause: () -> Unit, onStart: () -> Unit, onStop: () -> Unit) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), shadowElevation = 6.dp) {
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
