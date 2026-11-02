package app.kin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kin.data.*
import app.kin.domain.Evolution
import app.kin.features.KinViewModel
import java.time.*

private fun Task.today(): Boolean = Instant.ofEpochMilli(scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate() <= LocalDate.now()

@Composable
fun HomeScreen(vm: KinViewModel, pref: UserPreferences, openTasks: () -> Unit, openFocus: () -> Unit, openSettings: () -> Unit) {
    var permissionSetup by remember { mutableStateOf(false) }
    if (permissionSetup) { PermissionSetupScreen(vm) { permissionSetup = false }; return }
    val windowAllowed by rememberWindowPermission()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val companion by vm.companion.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val stage = Evolution.at(companion?.xp ?: 0)
    var help by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var breakSetup by remember { mutableStateOf(false) }
    val cycle by vm.activeBreak.collectAsStateWithLifecycle()
    val focus by vm.focus.collectAsStateWithLifecycle()
    val today = tasks.filter { !it.resolved && it.today() }.take(3)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))) {
                WorldLandscape(modifier = Modifier.fillMaxSize(), unlocked = stage.ordinal)
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Small steps. A little magic.", style = MaterialTheme.typography.labelLarge, color = Ink)
                            Text("Hello, dreamer", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Ink)
                            Text("${pref.name} · ${stage.label} · ${companion?.xp ?: 0} XP", color = Ink)
                        }
                        FilledTonalIconButton(onClick = openSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                    }
                    Spacer(Modifier.height(20.dp))
                    Surface(Modifier.align(Alignment.End).widthIn(max = 210.dp), shape = RoundedCornerShape(20.dp), color = Cream.copy(alpha=.95f)) {
                        Text("We can start small.\nReady for today's adventure?", Modifier.padding(16.dp), color = Ink)
                    }
                }
                CompanionArt(stage, pref.reducedMotion, Modifier.align(Alignment.BottomStart).padding(start = 20.dp).size(150.dp), CreatureMood.WAVING)
            }
        }
        item { Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SoftCard {
                Text("Your next small step", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (cycle != null || focus != null) {
                    Text(if (cycle?.phase in listOf("RUNNING", "PAUSED", "DUE")) "Your break timer is active. Open it to see the countdown or return to your task." else "Your focus session is active.")
                    Button(onClick = openFocus, modifier = Modifier.fillMaxWidth()) { Text("Open active timer") }
                } else {
                    Text("Rest first, or start one task. You choose.")
                    Button(onClick = { breakSetup = true }, modifier = Modifier.fillMaxWidth()) { Text("Take a scrolling break") }
                    Text("Choose when to return. Your companion appears over other apps at the end.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.focusSetupRequested.value = true; openFocus() }, modifier = Modifier.fillMaxWidth()) { Text("Focus now") }
                }
                Text(if (windowAllowed) "Companion window allowed" else "Floating reminders are off until you allow the companion window.",
                    style = MaterialTheme.typography.bodySmall, color = if (windowAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                TextButton(onClick = { permissionSetup = true }) { Text("Check companion permissions") }
            }
            SoftCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Today's adventures", fontWeight = FontWeight.Bold)
                    Text("${today.size} to explore", style = MaterialTheme.typography.labelMedium)
                }
                if (today.isEmpty()) Text("A clear little clearing. Choose one small thing you'd like to do today.")
                today.forEach { task -> TaskRow(task, { vm.action { vm.repository.completeTask(task.id) } },
                    { editing = task }, { if (vm.focus.value == null) vm.action { vm.repository.startFocus(task.minutes, task) }; openFocus() }) }
                TextButton(onClick = openTasks) { Icon(Icons.Outlined.Add, null); Text(" Add an adventure") }
            }
            Button(onClick = { help = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("I can't start") }
            OutlinedButton(onClick = openFocus, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Timer, null); Text(" A little focus time") }
            Text("Progress is finding your way back.", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall)
        } }
    }
    editing?.let { task -> TaskEditor(task, goals, { editing = null }, { vm.action { vm.repository.saveTask(it); editing = null } }) }
    if (help) CantStartDialog(vm, { help = false }, openFocus)
    if (breakSetup) BreakSetupDialog(vm, onDismiss = { breakSetup = false }, onStarted = { breakSetup = false; openFocus() })
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TasksScreen(vm: KinViewModel, openFocus: () -> Unit, requestedTaskId: Long? = null, requestId: Int = 0) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val steps by vm.subtasks.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var deleting by remember { mutableStateOf<Task?>(null) }
    var help by remember { mutableStateOf(false) }
    var helpTaskId by remember { mutableStateOf<Long?>(null) }
    var optionsId by rememberSaveable { mutableStateOf<Long?>(null) }
    var options by remember { mutableStateOf<Task?>(null) }
    var handledRequest by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(requestedTaskId, requestId) {
        if (requestedTaskId != null && handledRequest != requestId) {
            handledRequest = requestId
            optionsId = requestedTaskId
        }
    }
    LaunchedEffect(optionsId) {
        options = null
        optionsId?.let { id -> vm.action {
            val task = vm.repository.dao.task(id)
            if (optionsId == id) {
                if (task == null) { optionsId = null; error("This adventure was deleted.") }
                options = task
            }
        } }
    }
    val filtered = tasks.filter { task ->
        (when(tab) { 0 -> !task.resolved && task.today(); 1 -> !task.resolved && !task.today(); else -> task.resolved }) &&
            (task.title.contains(query, true) || task.category.contains(query, true))
    }
    val shown = if (tab == 0 && !showAll && query.isEmpty()) filtered.take(3) else filtered
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Your adventures", "One step is a perfectly good start.") {
            FilledTonalIconButton(onClick = { editing = null; showEditor = true }) { Icon(Icons.Outlined.Add, "Create task") }
        } }
        item { SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Today", "Later", "Completed").forEachIndexed { i, label ->
                SegmentedButton(tab == i, { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
            }
        } }
        item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Find a task or category") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true) }
        if (shown.isEmpty()) item { SoftCard { Text(if (tab == 2) "Your completed adventures will live here." else "Room for something small.", fontWeight = FontWeight.Medium)
            Text("Add an adventure when you're ready.") } }
        items(shown, key = { it.id }) { task -> SoftCard {
            TaskRow(task, { vm.action { vm.repository.completeTask(task.id) } }, { editing = task; showEditor = true }, {
                if (vm.focus.value == null) vm.action { vm.repository.startFocus(task.minutes, task) }
                openFocus()
            })
            if (task.description.isNotBlank()) Text(task.description, style = MaterialTheme.typography.bodyMedium)
            TaskSteps(vm, task, steps.filter { it.taskId == task.id })
            if (!task.resolved) TextButton(onClick = { optionsId = task.id }) { Text("Options for ${task.title}") }
            if (task.snoozeCount >= 3 && !task.resolved) Text("Starting can be hard. Want to make this smaller?", color = MaterialTheme.colorScheme.primary)
        } }
        if (tab == 0 && filtered.size > 3 && query.isEmpty()) item {
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Focus on three" else "Show all ${filtered.size} adventures") }
        }
        item { Button(onClick = { helpTaskId = null; help = true }, Modifier.fillMaxWidth()) { Text("Feeling stuck? I can't start") } }
    }
    if (showEditor) TaskEditor(editing, goals, { showEditor = false }, { vm.action { vm.repository.saveTask(it); showEditor = false } },
        onDelete = editing?.let { task -> { deleting = task; showEditor = false } },
        onSnooze = editing?.let { task -> { vm.action { vm.repository.snooze(task.id); showEditor = false } } })
    deleting?.let { task -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this adventure?") },
        text = { Text("${task.title} will be removed. Your earned XP stays with you.") },
        confirmButton = { TextButton(onClick = { vm.action { vm.repository.deleteTask(task.id) }; deleting = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep it") } }) }
    options?.takeIf { optionsId == it.id }?.let { snapshot ->
        val task = tasks.firstOrNull { it.id == snapshot.id } ?: snapshot
        TaskOptions(vm, task, { optionsId = null }, { optionsId = null; editing = task; showEditor = true },
            { optionsId = null; helpTaskId = task.id; help = true }, openFocus)
    }
    if (help) CantStartDialog(vm, { help = false }, openFocus, helpTaskId)
}
