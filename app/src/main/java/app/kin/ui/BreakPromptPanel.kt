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
            Text(if (snoozed) "Five more minutes" else "Your break is over", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            CompanionArt(stage, pref.reducedMotion, Modifier.fillMaxWidth().height(180.dp), CreatureMood.WAVING)
            Text(value.taskTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(BreakPolicy.message(value.taskTitle, value.goalTitle, value.snoozeCount))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("Start task") }
            if (!snoozed) OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) { Text("Snooze 5 min") }
            TextButton(onClick = onStop) { Text("Stop break") }
        }
    }
}
