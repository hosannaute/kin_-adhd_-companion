package app.kin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kin.data.*
import app.kin.domain.*

@Composable
fun BreakPromptPanel(value: BreakSession, pref: UserPreferences, stage: Evolution, snoozed: Boolean = false,
                     maxHeight: Float = 560f, onStart: () -> Unit, onSnooze: () -> Unit, onStop: () -> Unit) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight.dp).verticalScroll(rememberScrollState()).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (snoozed) { if (value.remainingMillis / 60_000 == 5L) "Five more minutes" else "${value.remainingMillis / 60_000} more minutes" } else "Your break is over", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            CompanionArt(stage, pref.reducedMotion, Modifier.fillMaxWidth().height(180.dp), CreatureMood.WAVING)
            Text(value.taskTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(BreakPolicy.message(value.taskTitle, value.goalTitle, value.snoozeCount, pref.reminderStyle))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start task") }
            if (!snoozed && AlertPolicy.canSnooze(value.snoozeCount,pref.snoozeLimit)) OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) { Text("Snooze ${pref.snoozeMinutes} min") }
            if (!snoozed && !AlertPolicy.canSnooze(value.snoozeCount,pref.snoozeLimit)) Text("Your snooze limit is reached. Choose a focus duration or end the break.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onStop) { Text("Stop break") }
        }
    }
}

@Composable
fun BreakCountdownPanel(value: BreakSession, left: Long, pref: UserPreferences, stage: Evolution,
                        onOpen: () -> Unit, onPause: () -> Unit) {
    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), shadowElevation = 6.dp) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompanionArt(stage, pref.reducedMotion, Modifier.size(48.dp), CreatureMood.WAVING)
                Column {
                    Text(if (value.phase == "PAUSED") "Break paused" else "Scrolling break", style = MaterialTheme.typography.labelMedium)
                    Text(app.kin.services.BreakNotifications.format(left), style = MaterialTheme.typography.headlineSmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPause) { Text(if (value.phase == "PAUSED") "Resume" else "Pause") }
                TextButton(onClick = onOpen) { Text("Open KIN") }
            }
        }
    }
}
