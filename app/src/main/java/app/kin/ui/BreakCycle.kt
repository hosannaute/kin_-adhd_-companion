package app.kin.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import app.kin.data.*
import app.kin.domain.*
import app.kin.features.KinViewModel
import app.kin.services.*
import kotlinx.coroutines.*

@Composable
fun BreakPermissions() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notifications by remember { mutableStateOf(Notifications.allowed(context)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            overlay = Settings.canDrawOverlays(context); notifications = Notifications.allowed(context)
        } }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    Text(if (overlay) "Companion window is allowed." else "Allow the companion window to see a large reminder over other apps.", style = MaterialTheme.typography.bodySmall)
    if (!overlay) OutlinedButton(onClick = {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
    }) { Text("Allow companion window") }
    Text(if (notifications) "Notifications are available as a fallback." else "Notifications are off. Without either permission, return to KIN to see the reminder.",
        style = MaterialTheme.typography.bodySmall)
    if (!notifications) OutlinedButton(onClick = {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }) { Text("Allow break notifications") }
}

@Composable
fun BreakSetupDialog(vm: KinViewModel, initialTaskId: Long? = null, onDismiss: () -> Unit, onStarted: () -> Unit = onDismiss, initialMinutes: Int? = null) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val recent by vm.timerOptions.collectAsStateWithLifecycle()
    val options = recent ?: return
    val initial = tasks.firstOrNull { it.id == initialTaskId && !it.resolved }
        ?: tasks.firstOrNull { it.id == options.taskId && !it.resolved } ?: tasks.firstOrNull { !it.resolved }
    var taskId by rememberSaveable { mutableStateOf(initial?.id) }
    var goalId by rememberSaveable { mutableStateOf(if (options.taskId == initial?.id) options.goalId.takeIf { id -> goals.any { it.id == id && !it.achieved } }
        else initial?.goalId?.takeIf { id -> goals.any { it.id == id && !it.achieved } }) }
    var minutes by rememberSaveable { mutableStateOf((initialMinutes ?: options.breakMinutes).toString()) }
    var taskPicked by rememberSaveable { mutableStateOf(false) }
    var goalPicked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tasks,goals) {
        if (!taskPicked && taskId == null) taskId = initial?.id
        if (!goalPicked) goalId = if (options.taskId == taskId) options.goalId.takeIf { id -> goals.any { it.id == id && !it.achieved } }
            else tasks.firstOrNull { it.id == taskId }?.goalId?.takeIf { id -> goals.any { it.id == id && !it.achieved } }
    }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Plan a scrolling break") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Choose the task you'll return to. When the break ends, your companion will help you start.")
            MinutesPicker(minutes, { minutes = it }, if (initialMinutes == 1) listOf(1,5,10,15,20) else listOf(5,10,15,20), "Break minutes")
            if (tasks.none { !it.resolved }) Text("Create a pending task in Tasks first.")
            ChoiceList("Return to task", tasks.firstOrNull { it.id == taskId }?.title ?: "Choose a task",
                tasks.filter { !it.resolved }.map { it.id.toString() to it.title }) { id ->
                taskPicked = true; goalPicked = false; taskId = id.toLong(); goalId = tasks.firstOrNull { it.id == taskId }?.goalId?.takeIf { key -> goals.any { it.id == key && !it.achieved } }
            }
            ChoiceList("Personal goal", goals.firstOrNull { it.id == goalId }?.title ?: "Use just my task",
                listOf("0" to "Use just my task") + goals.filter { !it.achieved }.map { it.id.toString() to it.title }) { goalPicked = true; goalId = it.toLong().takeIf { id -> id > 0 } }
            BreakPermissions()
            Text("A small timer stays at the screen edge, even inside KIN. Tap it for controls. At zero, a large reminder appears, including during quiet hours. Notification sounds stay quiet.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !saving && taskId != null && minutes.toIntOrNull() in 1..180, onClick = {
        saving = true
        vm.action {
            try {
                vm.app.timerPreferences.breakChoice(minutes.toInt(), taskId!!, goalId)
                vm.app.breaks.begin(taskId!!, goalId, minutes.toInt())
                BreakTimerService.start(vm.app)
                onStarted()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message
            } finally { saving = false }
        }
    }) { Text("Begin break") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun ActiveBreakScreen(vm: KinViewModel, value: BreakSession, onStart: () -> Unit) {
    val pref by vm.preferences.collectAsStateWithLifecycle()
    val options = pref ?: return
    var left by remember(value.id) { mutableLongStateOf(vm.app.breaks.remaining(value)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val status by BreakTimerService.status.collectAsStateWithLifecycle()
    val companion by vm.companion.collectAsStateWithLifecycle()
    LaunchedEffect(value, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                left = vm.app.breaks.remaining(value)
                vm.app.breaks.refresh(value.id)
                delay(500)
            }
        }
    }
    val windowAllowed by rememberWindowPermission()
    Column(Modifier.fillMaxSize().testTag("break_screen").verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageTitle("Your scrolling break", "Rest now. Return with one small step.")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(
            MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceVariant))).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CompanionArt(Evolution.at(companion?.xp ?: 0), true, Modifier.size(80.dp), CreatureMood.WAVING)
            Text(if (value.phase == "DUE") "Time to return" else BreakNotifications.format(left), style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(when(value.phase) { "PAUSED" -> "Break paused"; "DUE" -> "Choose your focus time when you're ready."; else -> "Your timer stays on screen while you scroll." },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        SoftCard {
            Text("RETURN TO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(value.taskTitle, style = MaterialTheme.typography.titleLarge)
            if (value.goalTitle.isNotBlank()) Text(value.goalTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (value.snoozeCount > 0) Text(BreakPolicy.message(value.taskTitle, value.goalTitle, value.snoozeCount, options.reminderStyle))
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Start task") }
            when (value.phase) {
                "RUNNING" -> OutlinedButton(onClick = { vm.action { vm.app.breaks.pause(value.id, value.revision) } }, modifier = Modifier.fillMaxWidth()) { Text("Pause break") }
                "PAUSED" -> OutlinedButton(onClick = { vm.action { vm.app.breaks.resume(value.id, value.revision) } }, modifier = Modifier.fillMaxWidth()) { Text("Resume break") }
                "DUE" -> if (AlertPolicy.canSnooze(value.snoozeCount,options.snoozeLimit)) OutlinedButton(onClick = { vm.action { if (vm.app.breaks.snooze(value.id, value.revision))
                    vm.app.breaks.dao.breakSession(value.id)?.let { BreakNotifications.snoozed(vm.app, it) } } }, modifier = Modifier.fillMaxWidth()) { Text("Snooze ${options.snoozeMinutes} min") }
            }
            TextButton(onClick = { vm.action { vm.app.breaks.stop(value.id, value.revision) } }) { Text("Stop break") }
        }
        if (!status.running) {
            Text("The timer is saved. Restart its floating control to keep it nearby.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { BreakTimerService.start(vm.app) }) { Text("Restart timer") }
        } else Text("Drag the floating timer to either edge. Tap it for controls.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!windowAllowed) BreakPermissions()
    }

}

@Composable
fun BreakCycleHost(vm: KinViewModel, pref: UserPreferences, entry: BreakEntry?, requestId: Int, openFocus: () -> Unit) {
    val active by vm.activeBreak.collectAsStateWithLifecycle()
    val companion by vm.companion.collectAsStateWithLifecycle()
    val internalRequest by vm.breakFocusRequest.collectAsStateWithLifecycle()
    var handled by rememberSaveable { mutableIntStateOf(-1) }
    var pickerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickerRevision by rememberSaveable { mutableIntStateOf(-1) }
    var dismissedDue by rememberSaveable { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val promptOwner = remember { "break-dialog:${java.util.UUID.randomUUID()}" }
    LaunchedEffect(internalRequest) {
        internalRequest?.let {
            pickerId = it.id; pickerRevision = it.revision; dismissedDue = "${it.id}:${it.revision}"
            vm.clearBreakFocusRequest()
        }
    }
    LaunchedEffect(entry, requestId) {
        if (entry != null && handled != requestId) {
            handled = requestId
            vm.action {
                val value = vm.app.breaks.refresh(entry.id)
                require(value != null && value.revision == entry.revision && value.phase in listOf("RUNNING", "PAUSED", "DUE")) {
                    "This break changed or ended. Use its current Start task button."
                }
                pickerId = value.id; pickerRevision = value.revision
                dismissedDue = "${value.id}:${value.revision}"
            }
        }
    }
    val value = active
    if (value != null && value.phase == "DUE" && pickerId == null && dismissedDue != "${value.id}:${value.revision}") {
        DisposableEffect(value.id, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) BreakPromptVisibility.acquire(promptOwner)
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_DESTROY) BreakPromptVisibility.release(promptOwner)
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) BreakPromptVisibility.acquire(promptOwner)
            onDispose { lifecycle.removeObserver(observer); BreakPromptVisibility.release(promptOwner) }
        }
        androidx.compose.ui.window.Dialog(onDismissRequest = { dismissedDue = "${value.id}:${value.revision}" }) {
            BreakPromptPanel(value, pref, Evolution.at(companion?.xp ?: 0),
                onStart = { dismissedDue = "${value.id}:${value.revision}"; pickerId = value.id; pickerRevision = value.revision },
                onSnooze = { vm.action { if (vm.app.breaks.snooze(value.id, value.revision))
                    vm.app.breaks.dao.breakSession(value.id)?.let { BreakNotifications.snoozed(vm.app, it) } } },
                onStop = { vm.action { vm.app.breaks.stop(value.id, value.revision) } })
        }
    }
    if (pickerId != null) {
        if (value != null && value.id == pickerId && value.phase in listOf("RUNNING", "PAUSED", "DUE"))
            FocusDurationDialog(vm, value, pickerRevision, { pickerId = null }, { pickerId = null; openFocus() })
        else if (value != null) {
            LaunchedEffect(value) { pickerId = null }
        }
    }
}

@Composable
fun FocusDurationDialog(vm: KinViewModel, value: BreakSession, revision: Int, onDismiss: () -> Unit, onStarted: () -> Unit) {
    val recent by vm.timerOptions.collectAsStateWithLifecycle()
    val options = recent ?: return
    var minutes by rememberSaveable(value.id) { mutableStateOf(options.focusMinutes.toString()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("How long do you want to focus?") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(value.taskTitle)
            MinutesPicker(minutes, { minutes = it }, listOf(5,10,15,25,45), "Focus minutes")
            Text("Your break ends when you start this focus session. Finishing early awards no XP.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !saving && minutes.toIntOrNull() in 1..180, onClick = {
        saving = true
        vm.action {
            try { vm.app.timerPreferences.focusChoice(minutes.toInt(), value.taskId); vm.app.breaks.startFocus(value.id, revision, minutes.toInt()); onStarted()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message
            } finally { saving = false }
        }
    }) { Text("Begin focus") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
