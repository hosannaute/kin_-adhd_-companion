package app.kin.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kin.data.*
import app.kin.domain.*
import app.kin.features.KinViewModel
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun PageTitle(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun TaskRow(task: Task, complete: () -> Unit, edit: () -> Unit, start: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = task.completed, onCheckedChange = { if (!task.resolved) complete() }, enabled = !task.resolved)
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text("${task.minutes} min · ${task.category}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Instant.ofEpochMilli(task.scheduledAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d · h:mm a")),
                style = MaterialTheme.typography.labelSmall)
            if (task.skipped) Text("Skipped · no XP", style = MaterialTheme.typography.labelSmall)
            if (task.repeatRule != RepeatRule.NONE.name) Text(
                "${RepeatRule.valueOf(task.repeatRule).label} · ${task.repeatZone}", style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = edit) { Icon(Icons.Outlined.Edit, "Edit ${task.title}") }
        if (!task.resolved) IconButton(onClick = start) { Icon(Icons.Outlined.PlayArrow, "Focus on ${task.title}") }
    }
}

@Composable
fun TaskEditor(task: Task?, goals: List<PersonalGoal>, onDismiss: () -> Unit, onSave: (Task) -> Unit,
               onDelete: (() -> Unit)? = null, onSnooze: (() -> Unit)? = null) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var category by remember { mutableStateOf(task?.category ?: "Personal") }
    var minutes by remember { mutableStateOf((task?.minutes ?: 15).toString()) }
    val zone = remember { task?.repeatZone?.takeIf { it.isNotBlank() }?.let(ZoneId::of) ?: ZoneId.systemDefault() }
    val scheduled = remember { Instant.ofEpochMilli(task?.scheduledAt ?: System.currentTimeMillis()).atZone(zone) }
    var date by remember { mutableStateOf(scheduled.toLocalDate()) }
    var time by remember { mutableStateOf(scheduled.toLocalTime().withSecond(0).withNano(0)) }
    var scheduleChanged by remember { mutableStateOf(task == null) }
    var repeat by remember { mutableStateOf(RepeatRule.valueOf(task?.repeatRule ?: "NONE")) }
    var priority by remember { mutableIntStateOf(task?.priority ?: 1) }
    var reminder by remember { mutableStateOf(task?.reminder ?: false) }
    var goalId by remember { mutableStateOf(task?.goalId) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (task == null) "A new adventure" else "Your adventure") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Task name") }, singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text("Notes") }, minLines = 2)
            OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
            OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Estimated minutes (1–1440)") }, singleLine = true)
            OutlinedButton(onClick = {
                DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day); scheduleChanged = true },
                    date.year, date.monthValue - 1, date.dayOfMonth).show()
            }) { Icon(Icons.Outlined.CalendarMonth, null); Text(" Date: ${date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}") }
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, hour, minute -> time = LocalTime.of(hour, minute); scheduleChanged = true },
                    time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
            }) { Icon(Icons.Outlined.Schedule, null); Text(" Time: ${time.format(DateTimeFormatter.ofPattern("h:mm a"))}") }
            Text("Schedule time zone: ${zone.id}", style = MaterialTheme.typography.bodySmall)
            Text("Repeat")
            RepeatRule.entries.forEach { rule -> FilterChip(repeat == rule, { repeat = rule }, label = { Text(rule.label) }) }
            if (repeat != RepeatRule.NONE) Text("Completing or skipping creates the next future date. Missed dates won't pile up. Repeats keep this time zone.",
                style = MaterialTheme.typography.bodySmall)
            Text("Priority")
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { listOf("Gentle", "Normal", "High").forEachIndexed { i, label ->
                FilterChip(priority == i, { priority = i }, label = { Text(label) })
            } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(reminder, { reminder = it }); Text("Gentle reminder")
            }
            if (goals.isNotEmpty()) {
                Text("Personal goal")
                FilterChip(goalId == null, { goalId = null }, label = { Text("No goal") })
                goals.forEach { goal -> FilterChip(goalId == goal.id, { goalId = goal.id }, label = { Text(goal.title) }) }
            }
            if (task != null) {
                Text(when { task.skipped -> "Skipped · no XP"; task.completed -> "Completed · XP already awarded"; else -> "Snoozed ${task.snoozeCount} times" })
                if (!task.resolved && onSnooze != null) TextButton(onClick = onSnooze) { Text("Snooze for 10 minutes") }
                onDelete?.let { TextButton(onClick = it) { Text("Delete task", color = MaterialTheme.colorScheme.error) } }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = {
            val estimate = minutes.toIntOrNull()
            val instant = if (!scheduleChanged && task != null) task.scheduledAt else date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            when {
                title.isBlank() -> error = "Please give this adventure a name."
                estimate == null || estimate !in 1..1440 -> error = "Enter a duration from 1 to 1440 minutes."
                else -> onSave((task ?: Task(title = title)).copy(title = title, description = description,
                    category = category.ifBlank { "Personal" }, minutes = estimate, scheduledAt = instant,
                    priority = priority, reminder = reminder, goalId = goalId, repeatRule = repeat.name,
                    repeatZone = zone.id, repeatTime = if (scheduleChanged || task?.repeatRule != repeat.name) time.toString() else task?.repeatTime.orEmpty()))
            }
        }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun CantStartDialog(vm: KinViewModel, onDismiss: () -> Unit, onFocus: () -> Unit, taskId: Long? = null) {
    var selected by remember { mutableStateOf<Feeling?>(null) }
    val tasks by vm.tasks.collectAsState()
    val task = if (taskId == null) tasks.firstOrNull { !it.resolved } else tasks.firstOrNull { it.id == taskId && !it.resolved }
    val conversation = remember { CompanionConversation() }
    var steps by remember(task?.id) { mutableStateOf(conversation.steps(task?.title ?: "your task").joinToString("\n")) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("What's happening?") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("You don't have to push through everything.")
            Feeling.entries.forEach { feeling ->
                FilterChip(selected == feeling, { selected = feeling }, label = { Text(feeling.label) })
            }
            selected?.let {
                Text(conversation.support(it, vm.context()))
                if (task != null && it in listOf(Feeling.OVERWHELMED, Feeling.UNSURE)) {
                    OutlinedTextField(steps, { value -> steps = value }, label = { Text("Edit your small steps") }, minLines = 3)
                    Button(onClick = { vm.action { vm.repository.breakDown(task, steps.lines()); onDismiss() } }) { Text("Save steps to task") }
                }
                Button(onClick = {
                    vm.action {
                        if (vm.repository.dao.currentFocus() == null) vm.repository.startFocus(if (it == Feeling.TIRED) 5 else 2, task)
                        onDismiss(); onFocus()
                    }
                }) { Text(if (it == Feeling.TIRED) "Try five gentle minutes" else "Try two minutes") }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Take a breath & close") } })
}
