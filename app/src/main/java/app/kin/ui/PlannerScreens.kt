package app.kin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.kin.data.*
import app.kin.domain.Evolution
import app.kin.features.KinViewModel
import app.kin.services.BreakTimerService
import app.kin.services.ScrollDetectorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.*

private fun Task.today(): Boolean = Instant.ofEpochMilli(scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate() <= LocalDate.now()

@Composable
fun HomeScreen(vm: KinViewModel, pref: UserPreferences, openTasks: () -> Unit, openFocus: () -> Unit, openSettings: () -> Unit) {
    var permissionSetup by remember { mutableStateOf(false) }
    if (permissionSetup) { PermissionSetupScreen(vm) { permissionSetup = false }; return }
    val context = LocalContext.current
    val windowAllowed by rememberWindowPermission()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val companion by vm.companion.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val xp = companion?.xp ?: 0
    val stage = Evolution.at(xp)
    val nextStage = Evolution.entries.getOrNull(stage.ordinal + 1)
    var help by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var breakSetup by remember { mutableStateOf(false) }
    val cycle by vm.activeBreak.collectAsStateWithLifecycle()
    val focus by vm.focus.collectAsStateWithLifecycle()
    val finished by vm.finishedFocus.collectAsStateWithLifecycle()
    val floating by BreakTimerService.status.collectAsStateWithLifecycle()
    val scrollStatus by ScrollDetectorService.status.collectAsStateWithLifecycle()
    val scrollEnabled = scrollStatus.connected || ScrollDetectorService.isEnabled(context)
    val allToday = tasks.filter { it.today() }
    val doneTodayCount = allToday.count { it.completed }
    val today = allToday.filter { !it.resolved }.take(3)
    val palette = KinPalette.current

    LazyColumn(
        Modifier.fillMaxSize().testTag("home_screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d")).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Hello, dreamer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                FilledTonalIconButton(onClick = openSettings) { Icon(Icons.Outlined.Settings, "Settings") }
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, palette.cardBorder.copy(alpha = 0.7f))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(palette.heroStart, palette.heroEnd)))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            XpBadge("$xp XP")
                            Text("One small step.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                "${pref.name} · ${stage.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        CompanionArt(stage, pref.reducedMotion, Modifier.size(84.dp), CreatureMood.WAVING)
                    }
                    if (nextStage != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            val progress = ((xp - stage.threshold).toFloat() / (nextStage.threshold - stage.threshold)).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                            )
                            Text(
                                "${nextStage.threshold - xp} XP to ${nextStage.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
        finished?.let { value ->
            item {
                FocusCompletionCard(
                    session = value,
                    onStop = { vm.dismissFinished(value.id) },
                    onBreak = { vm.requestCompletedBreak(value.id); openFocus() },
                    stage = stage,
                    reducedMotion = pref.reducedMotion
                )
            }
        }
        item {
            SoftCard {
                Text("Your next small step", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (cycle != null || focus != null) {
                    HomeTimerSummary(vm, cycle, focus)
                    Button(onClick = openFocus, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Open active timer") }
                } else {
                    Button(onClick = { breakSetup = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Outlined.Coffee, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Take a scrolling break")
                    }
                    OutlinedButton(onClick = { vm.focusSetupRequested.value = true; openFocus() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Outlined.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Focus now")
                    }
                }
            }
        }
        item {
            SoftCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.PictureInPictureAlt, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Floating timer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                !windowAllowed -> "Window permission needed"
                                cycle?.phase == "DUE" -> "Your return-to-task reminder is ready"
                                focus != null -> "Appears when you leave KIN"
                                cycle != null && floating.visible -> "At the screen edge · tap to expand"
                                cycle != null -> floating.message
                                else -> "Stays visible during breaks & focus"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            when {
                                !windowAllowed -> permissionSetup = true
                                cycle?.phase == "DUE" || focus != null -> openFocus()
                                cycle != null -> BreakTimerService.reveal(vm.app)
                                else -> breakSetup = true
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            when {
                                !windowAllowed -> "Set up"
                                cycle?.phase == "DUE" -> "Open"
                                focus != null -> "Open"
                                cycle != null -> "Show"
                                else -> "Start"
                            }
                        )
                    }
                }
                HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.6f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = if (scrollEnabled) MaterialTheme.colorScheme.primaryContainer else palette.warningContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.TouchApp,
                                null,
                                Modifier.size(20.dp),
                                tint = if (scrollEnabled) MaterialTheme.colorScheme.primary else palette.warningOnContainer
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Auto scroll detection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (scrollEnabled) scrollStatus.message else "Tap to enable automatic scroll check-ins",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = openSettings) { Text(if (scrollEnabled) "Manage" else "Enable") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Today's adventures", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (allToday.isNotEmpty()) {
                        Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                "$doneTodayCount/${allToday.size}",
                                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                TextButton(onClick = openTasks) { Text("See all") }
            }
        }
        item {
            SoftCard {
                if (today.isEmpty()) {
                    Text("Make room for one small thing.", style = MaterialTheme.typography.titleMedium)
                    Text("Add a task when you're ready.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                today.forEachIndexed { index, task ->
                    if (index > 0) HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.6f))
                    TaskRow(
                        task,
                        { vm.action { vm.repository.completeTask(task.id) } },
                        { editing = task },
                        {
                            if (vm.focus.value == null) vm.action {
                                vm.repository.startFocus(task.minutes, task)
                                BreakTimerService.start(vm.app)
                            }
                            openFocus()
                        },
                        compact = true
                    )
                }
                TextButton(onClick = openTasks) { Icon(Icons.Outlined.Add, null); Text(" Add an adventure") }
            }
        }
        item {
            FilledTonalButton(onClick = { help = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("I can't start")
            }
        }
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
    LaunchedEffect(pending, focus, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                left = if (pending != null) vm.app.breaks.remaining(pending) else focus?.let(vm.repository::remaining) ?: 0
                delay(500)
            }
        }
    }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (pending != null) "SCROLLING BREAK" else "FOCUS SESSION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(pending?.taskTitle ?: focus?.taskTitle.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(app.kin.services.BreakNotifications.format(left), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
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
        optionsId?.let { id ->
            vm.action {
                val task = vm.repository.dao.task(id)
                if (optionsId == id) {
                    if (task == null) { optionsId = null; error("This adventure was deleted.") }
                    options = task
                }
            }
        }
    }
    val filtered = tasks.filter { task ->
        (when (tab) { 0 -> !task.resolved && task.today(); 1 -> !task.resolved && !task.today(); else -> task.resolved }) &&
            (task.title.contains(query, true) || task.category.contains(query, true))
    }
    val shown = if (tab == 0 && !showAll && query.isEmpty()) filtered.take(3) else filtered
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            PageTitle("Your adventures", "One step is a perfectly good start.") {
                FilledIconButton(onClick = { editing = null; showEditor = true }) { Icon(Icons.Outlined.Add, "Create task") }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Today", "Later", "Completed").forEachIndexed { i, label ->
                    SegmentedButton(tab == i, { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                }
            }
        }
        if (tasks.size > 3 || query.isNotEmpty()) item {
            OutlinedTextField(
                query,
                { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find a task or category") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                shape = RoundedCornerShape(18.dp),
                singleLine = true
            )
        }
        if (shown.isEmpty()) item {
            SoftCard {
                Text(if (tab == 2) "Your completed adventures will live here." else "Room for something small.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Add an adventure when you're ready.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(shown, key = { it.id }) { task ->
            SoftCard {
                TaskRow(
                    task,
                    { vm.action { vm.repository.completeTask(task.id) } },
                    { editing = task; showEditor = true },
                    {
                        if (vm.focus.value == null) vm.action {
                            vm.repository.startFocus(task.minutes, task)
                            BreakTimerService.start(vm.app)
                        }
                        openFocus()
                    }
                )
                if (task.description.isNotBlank()) Text(task.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TaskSteps(vm, task, steps.filter { it.taskId == task.id })
                if (!task.resolved) TextButton(onClick = { optionsId = task.id }) { Text("Options for ${task.title}") }
                if (task.snoozeCount >= 3 && !task.resolved) Text("Starting can be hard. Want to make this smaller?", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (tab == 0 && filtered.size > 3 && query.isEmpty()) item {
            TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Focus on three" else "Show all ${filtered.size} adventures") }
        }
        item { Button(onClick = { helpTaskId = null; help = true }, Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Feeling stuck? I can't start") } }
    }
    if (showEditor) TaskEditor(
        editing,
        goals,
        { showEditor = false },
        { vm.action { vm.repository.saveTask(it); showEditor = false } },
        onDelete = editing?.let { task -> { deleting = task; showEditor = false } },
        onSnooze = editing?.let { task -> { vm.action { vm.repository.snooze(task.id); showEditor = false } } }
    )
    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this adventure?") },
            text = { Text("${task.title} will be removed. Your earned XP stays with you.") },
            confirmButton = { TextButton(onClick = { vm.action { vm.repository.deleteTask(task.id) }; deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep it") } }
        )
    }
    options?.takeIf { optionsId == it.id }?.let { snapshot ->
        val task = tasks.firstOrNull { it.id == snapshot.id } ?: snapshot
        TaskOptions(
            vm,
            task,
            { optionsId = null },
            { optionsId = null; editing = task; showEditor = true },
            { optionsId = null; helpTaskId = task.id; help = true },
            openFocus
        )
    }
    if (help) CantStartDialog(vm, { help = false }, openFocus, helpTaskId)
}
