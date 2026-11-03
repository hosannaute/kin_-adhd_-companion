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
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    LazyColumn(Modifier.fillMaxSize().testTag("home_screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d")), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Hello, dreamer", style = MaterialTheme.typography.headlineMedium)
            }
            FilledTonalIconButton(onClick = openSettings) { Icon(Icons.Outlined.Settings, "Settings") }
        } }
        item { Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, top = 14.dp, bottom = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("One small step.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("A little progress is enough for today.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("${pref.name} · ${stage.label} · ${companion?.xp ?: 0} XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                CompanionArt(stage, pref.reducedMotion, Modifier.size(80.dp), CreatureMood.WAVING)
            }
        } }
        item { SoftCard {
            Text("Your next small step", style = MaterialTheme.typography.titleMedium)
            if (cycle != null || focus != null) {
                HomeTimerSummary(vm, cycle, focus)
                Button(onClick = openFocus, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Open active timer") }
            } else {
                Text("Rest first, or begin one task.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { breakSetup = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Outlined.Coffee, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Take a scrolling break")
                }
                OutlinedButton(onClick = { vm.focusSetupRequested.value = true; openFocus() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.Outlined.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Focus now")
                }
            }
            if (!windowAllowed) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                Text("Allow the companion window to keep your timer on screen.", Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            if (!windowAllowed) TextButton(onClick = { permissionSetup = true }) {
                Icon(if (windowAllowed) Icons.Outlined.CheckCircle else Icons.Outlined.Info, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp)); Text("Check companion permissions")
            }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Today's adventures", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = openTasks) { Text("See all") }
        } }
        item { SoftCard {
            if (today.isEmpty()) {
                Text("Make room for one small thing.", style = MaterialTheme.typography.titleMedium)
                Text("Add a task when you're ready.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            today.forEachIndexed { index, task ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TaskRow(task, { vm.action { vm.repository.completeTask(task.id) } }, { editing = task },
                    { if (vm.focus.value == null) vm.action { vm.repository.startFocus(task.minutes, task) }; openFocus() })
            }
            TextButton(onClick = openTasks) { Icon(Icons.Outlined.Add, null); Text(" Add an adventure") }
        } }
        item { FilledTonalButton(onClick = { help = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("I can't start")
        } }
        item { TextButton(onClick = openFocus, modifier = Modifier.fillMaxWidth()) { Text("A little focus time") } }
    }
    editing?.let { task -> TaskEditor(task, goals, { editing = null }, { vm.action { vm.repository.saveTask(it); editing = null } }) }
    if (help) CantStartDialog(vm, { help = false }, openFocus)
    if (breakSetup) BreakSetupDialog(vm, onDismiss = { breakSetup = false }, onStarted = { breakSetup = false; openFocus() })
}

@Composable
private fun HomeTimerSummary(vm: KinViewModel, rest: BreakSession?, focus: FocusSession?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pending = rest?.takeIf { it.phase in listOf("RUNNING", "PAUSED", "DUE") }
    var left by remember { mutableLongStateOf(0) }
    LaunchedEffect(pending,focus,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                left = if (pending != null) vm.app.breaks.remaining(pending) else focus?.let(vm.repository::remaining) ?: 0
                delay(500)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (pending != null) "Scrolling break" else "Focus session", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(pending?.taskTitle ?: focus?.taskTitle.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        }
        Text(app.kin.services.BreakNotifications.format(left), style = MaterialTheme.typography.headlineSmall)
    }
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
        if (tasks.size > 3 || query.isNotEmpty()) item { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Find a task or category") },
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
