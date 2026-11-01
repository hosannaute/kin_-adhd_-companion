package app.kin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kin.data.*
import app.kin.domain.Evolution
import app.kin.features.KinViewModel
import kotlinx.coroutines.flow.first

@Composable
fun Onboarding(vm: KinViewModel, pref: UserPreferences) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(pref.name) }
    var goal by rememberSaveable { mutableStateOf("") }
    var why by rememberSaveable { mutableStateOf("") }
    var task by rememberSaveable { mutableStateOf("") }
    var reminders by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val error by vm.error.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("K I N", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(progress = { (step + 1) / 7f }, modifier = Modifier.fillMaxWidth())
        Text("${step + 1} of 7", style = MaterialTheme.typography.labelMedium)
        CompanionArt(if (step == 0) Evolution.EGG else Evolution.CURIOUS, pref.reducedMotion, Modifier.size(210.dp), CreatureMood.WAVING)
        val titles = listOf("Welcome to your little clearing", "Meet your companion", "A name for your friend",
            "A direction, not a deadline", "One small adventure", "Your pace, your reminders", "A little space for you")
        Text(titles[step], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        when(step) {
            0 -> Text("Progress isn't about never getting distracted. It's about finding your way back. KIN is here to keep you company.")
            1 -> Text("Your companion grows with small steps, not perfect streaks. Your real journey begins with an egg. Nothing is lost when you take a break.")
            2 -> OutlinedTextField(name, { name = it.take(32) }, label = { Text("Companion name") }, singleLine = true)
            3 -> {
                OutlinedTextField(goal, { goal = it }, label = { Text("Who do you want to become?") })
                OutlinedTextField(why, { why = it }, label = { Text("Why does this matter to you?") }, minLines = 2)
                Text("Both are optional. You can change them anytime.")
            }
            4 -> {
                OutlinedTextField(task, { task = it }, label = { Text("One small thing to do today") })
                Text("We'll start with 15 minutes. You can edit every detail in Tasks, or leave this blank for now.")
            }
            5 -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(reminders, { reminders = it }); Text("I would like gentle task reminders")
                }
                Text("Each task has its own reminder switch. Quiet hours start at 22:00 and end at 08:00. No pressure and no escalating alerts.")
            }
            6 -> Text("Everything works offline. Notifications, the floating companion, and app-time reminders are optional. You can enable them and choose permissions in More when you're ready.")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            if (step < 6) step++ else {
                busy = true
                vm.action {
                    try {
                        // Preferences are written last. Existing data is checked to make retries safe.
                        if (goal.isNotBlank()) {
                            val existing = vm.repository.dao.goals().first()
                            if (existing.none { it.title == goal.trim() }) vm.repository.saveGoal(PersonalGoal(title = goal, why = why))
                        }
                        if (task.isNotBlank() && vm.repository.dao.allTasks().none { it.title == task.trim() }) vm.repository.saveTask(Task(title = task))
                        vm.app.preferences.save(pref.copy(onboarded = true, name = name, reminders = reminders))
                    } finally { busy = false }
                }
            }
        }, enabled = !busy && (step != 2 || name.isNotBlank()), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (busy) "Saving your clearing…" else if (step == 6) "Begin my journey" else "Continue")
        }
        if (step > 0) TextButton(onClick = { step-- }, enabled = !busy) { Text("Back") }
    }
}
