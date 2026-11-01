package app.kin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.kin.data.*
import app.kin.features.KinViewModel

@Composable
fun TaskSteps(vm: KinViewModel, task: Task, steps: List<Subtask>) {
    var expanded by rememberSaveable(task.id) { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Subtask?>(null) }
    if (steps.isNotEmpty()) {
        Text("Small steps · ${steps.count { it.completed }}/${steps.size}", style = MaterialTheme.typography.labelLarge)
        (if (expanded) steps else steps.take(3)).forEach { step ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(step.completed, { checked -> vm.action { vm.repository.updateStep(step.id, completed = checked) } },
                    Modifier.semantics { contentDescription = "Complete step ${step.title}" }, enabled = !task.resolved)
                Text(step.title, Modifier.weight(1f))
                if (!task.resolved) IconButton(onClick = { editing = step }) { Icon(Icons.Outlined.Edit, "Edit step ${step.title}") }
            }
        }
        if (steps.size > 3) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show fewer steps" else "Show all ${steps.size} steps") }
    }
    if (!task.resolved) TextButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, null); Text(" Add a small step") }
    if (adding || editing != null) {
        val step = editing
        var title by remember(step?.id) { mutableStateOf(step?.title ?: "") }
        AlertDialog(onDismissRequest = { adding = false; editing = null }, title = { Text(if (step == null) "One small step" else "Edit small step") },
            text = { Column {
                OutlinedTextField(title, { title = it.take(300) }, label = { Text("Step name") }, minLines = 2)
                if (step != null) TextButton(onClick = { vm.action { vm.repository.deleteStep(step.id); editing = null } }) { Text("Delete step") }
            } }, confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { vm.action {
                if (step == null) vm.repository.addSteps(task.id, listOf(title)) else vm.repository.updateStep(step.id, title = title)
                adding = false; editing = null
            } }) { Text("Save step") } }, dismissButton = { TextButton(onClick = { adding = false; editing = null }) { Text("Cancel") } })
    }
}

@Composable
fun TaskOptions(vm: KinViewModel, task: Task, onDismiss: () -> Unit, onEdit: () -> Unit,
                onSmaller: () -> Unit, openFocus: () -> Unit) {
    var skipping by remember(task.id) { mutableStateOf(false) }
    if (skipping) {
        AlertDialog(onDismissRequest = { skipping = false }, title = { Text("Skip this occurrence?") },
            text = { Text(if (task.repeatRule == "NONE") "This adventure will move to Completed as skipped. No XP is awarded."
                else "Only this occurrence is skipped. The next future date will be created. No XP is awarded.") },
            confirmButton = { TextButton(onClick = { vm.action { vm.repository.skipTask(task.id); onDismiss() } }) { Text("Skip occurrence") } },
            dismissButton = { TextButton(onClick = { skipping = false }) { Text("Keep it") } })
    } else AlertDialog(onDismissRequest = onDismiss, title = { Text(task.title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (task.resolved) Text("This adventure is already ${if (task.skipped) "skipped" else "completed"}.") else {
                TextButton(onClick = { vm.reminderStart(task.id); onDismiss(); openFocus() }) { Text("Start focus") }
                TextButton(onClick = { vm.action { vm.repository.snooze(task.id); onDismiss() } }) { Text("Snooze for 10 minutes") }
                TextButton(onClick = onSmaller) { Text("Make it smaller") }
                TextButton(onClick = onEdit) { Text("Reschedule or edit") }
                TextButton(onClick = { skipping = true }) { Text("Skip this occurrence") }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
