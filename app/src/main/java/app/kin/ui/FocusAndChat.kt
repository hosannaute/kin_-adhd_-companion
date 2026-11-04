package app.kin.ui

import app.kin.data.resolved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kin.data.UserPreferences
import app.kin.domain.Evolution
import app.kin.features.KinViewModel
import app.kin.features.PipFocusState
import app.kin.features.PipAvailability

@Composable
fun FocusScreen(vm: KinViewModel, pref: UserPreferences, timer: PipFocusState, enterPip: () -> Unit, pipSettings: () -> Unit) {
    val activeBreak by vm.activeBreak.collectAsStateWithLifecycle()
    val latest by vm.latestFocus.collectAsStateWithLifecycle()
    val finished by vm.finishedFocus.collectAsStateWithLifecycle()
    val completedBreak by vm.completedBreakRequest.collectAsStateWithLifecycle()
    var breakTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showBreakSetup by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(completedBreak?.id) { completedBreak?.let {
        breakTaskId = it.taskId; showBreakSetup = true; vm.clearCompletedBreakRequest()
    } }
    val breakValue = activeBreak
    if (breakValue != null && breakValue.phase in listOf("RUNNING", "PAUSED", "DUE")) {
        ActiveBreakScreen(vm, breakValue, { vm.chooseBreakFocus(breakValue) })
        return
    }
    val session = timer.session?.takeUnless { it.ended }
    val choosingFocus by vm.focusSetupRequested.collectAsStateWithLifecycle()
    LaunchedEffect(session?.id) { if (session != null) vm.focusSetupRequested.value = false }
    if (session == null && !choosingFocus) {
        LazyColumn(Modifier.fillMaxSize().testTag("focus_screen"), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { PageTitle("A little focus", "Rest first, or start one small task.") }
            finished?.let { value -> item { FocusCompletionCard(value,
                { vm.dismissFinished(value.id) }, { vm.requestCompletedBreak(value.id) }) } }
            item { SoftCard {
                Text("Take a scrolling break", style = MaterialTheme.typography.titleLarge)
                Text("Choose a task to return to. Your timer stays on screen until the break ends.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { breakTaskId = latest?.taskId; showBreakSetup = true }, modifier = Modifier.fillMaxWidth()) { Text("Plan a scrolling break") }
            } }
            item { SoftCard {
                Text("Focus now", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("One task. One timer. Choose your pace and begin.")
                OutlinedButton(onClick = { vm.focusSetupRequested.value = true }, modifier = Modifier.fillMaxWidth()) { Text("Choose focus duration") }
            } }
        }
        if (showBreakSetup) BreakSetupDialog(vm, breakTaskId, { showBreakSetup = false })
        return
    }
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val companion by vm.companion.collectAsStateWithLifecycle()
    val count by vm.sessionCount.collectAsStateWithLifecycle()
    val recent by vm.timerOptions.collectAsStateWithLifecycle()
    val options = recent ?: return
    var minutes by rememberSaveable { mutableStateOf(options.focusMinutes.toString()) }
    var taskId by rememberSaveable { mutableStateOf(tasks.firstOrNull { it.id == options.taskId && !it.resolved }?.id) }
    var taskPicked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tasks) { if (!taskPicked && taskId == null) taskId = tasks.firstOrNull { it.id == options.taskId && !it.resolved }?.id }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        LazyColumn(Modifier.fillMaxSize().testTag("focus_screen"), contentPadding = PaddingValues(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { Text("A little focus", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium) }
            item { Text(session?.taskTitle ?: "You don't have to do everything. Just be here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (session == null) {
                item { SoftCard {
                    Text("Choose your focus duration", fontWeight = FontWeight.Bold)
                    MinutesPicker(minutes, { minutes = it }, listOf(5,10,15,25,45), "Focus minutes")
                    ChoiceList("Focus on", tasks.firstOrNull { it.id == taskId }?.title ?: "Just a little time for me",
                        listOf("0" to "Just a little time for me") + tasks.filter { !it.resolved }.map { it.id.toString() to it.title }) { taskPicked = true; taskId = it.toLong().takeIf { key -> key > 0 } }

                } }
            }
            if (session == null) item { TextButton(onClick = { vm.focusSetupRequested.value = false }) { Text("Back to break or focus") } }
            item { CompanionArt(Evolution.at(companion?.xp ?: 0), pref.reducedMotion, Modifier.size(100.dp), CreatureMood.SLEEPING) }
            item {
                val millis = if (session != null) timer.remainingMillis else (minutes.toIntOrNull() ?: 0) * 60_000L
                val seconds = (millis + 999) / 1000
                Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                    Surface(Modifier.fillMaxSize().padding(10.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surface) { }
                    CircularProgressIndicator(progress = { if (session != null) (millis.toFloat() / session!!.durationMillis).coerceIn(0f,1f) else 1f },
                        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer, strokeWidth = 7.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%02d:%02d".format(seconds / 60, seconds % 60), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"))
                        Text(if (session?.running == false) "Resting for a moment" else "One moment at a time", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                val current = session
                if (current == null) Button(enabled = minutes.toIntOrNull() in 1..180, onClick = { vm.action {
                    vm.app.timerPreferences.focusChoice(minutes.toInt(), taskId)
                    vm.repository.startFocus(minutes.toInt(), tasks.firstOrNull { it.id == taskId })
                } }) {
                    Icon(Icons.Outlined.PlayArrow, null); Text(" Start focus")
                } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.action { if (current.running) vm.repository.pauseFocus(current.id) else vm.repository.resumeFocus(current.id) } }) {
                        Text(if (current.running) "Pause" else "Resume")
                    }
                    FilledTonalButton(onClick = { vm.action { vm.repository.stopFocus(current.id) } }) { Text("Finish early") }
                }
            }
            if (session != null) item { SoftCard {
                Text("Picture-in-Picture", fontWeight = FontWeight.Bold)
                when (timer.availability) {
                    PipAvailability.AVAILABLE -> {
                        Text("Keep your companion and timer beside other apps. Tap the small window for controls or to return to KIN.")
                        OutlinedButton(onClick = enterPip) {
                            Icon(Icons.Outlined.PictureInPictureAlt, null); Text(" Open Picture-in-Picture")
                        }
                        Text("Closing the window keeps your session running. Finish early ends it without XP.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    PipAvailability.DISABLED -> {
                        Text("Picture-in-Picture is disabled for KIN in Android settings. Your timer still works here.")
                        OutlinedButton(onClick = pipSettings) { Text("Allow Picture-in-Picture") }
                    }
                    PipAvailability.UNSUPPORTED -> Text("This device doesn't support Picture-in-Picture. Your timer still works here.")
                }
            } }
            item { Text("$count completed sessions · No pressure to keep a streak", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            item { Text("Finishing early saves the interruption without XP. Completion notifications may be delayed by Android battery controls.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (showBreakSetup) BreakSetupDialog(vm, breakTaskId, { showBreakSetup = false })
}

@Composable
fun ChatScreen(vm: KinViewModel, pref: UserPreferences) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val companion by vm.companion.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var clear by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompanionArt(Evolution.at(companion?.xp ?: 0), true, Modifier.size(60.dp))
            Column(Modifier.weight(1f)) {
                Text(pref.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Offline companion · guided responses", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { clear = true }, enabled = messages.isNotEmpty()) { Icon(Icons.Outlined.DeleteOutline, "Delete conversation") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SoftCard { Text("A small space to breathe", fontWeight = FontWeight.Bold)
                Text("Tell me what's making it hard to start. I use offline templates, your tasks, and goals to offer gentle next steps. I'm not an AI or a therapist.") } }
            items(messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                    Surface(Modifier.widthIn(max = 300.dp), shape = RoundedCornerShape(20.dp), color =
                        if (message.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(16.dp)) {
                            Text(if (message.fromUser) "You" else pref.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp)); Text(message.text)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Overwhelmed", "Exhausted", "Distracted").forEach { feeling ->
                SuggestionChip(onClick = { vm.send("I'm ${feeling.lowercase()}") }, label = { Text(feeling) })
            }
        }
        Row(Modifier.fillMaxWidth().imePadding().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it.take(2000) }, modifier = Modifier.weight(1f), label = { Text("What's on your mind?") }, maxLines = 4)
            IconButton(onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank()) { Icon(Icons.AutoMirrored.Outlined.Send, "Send message") }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear this conversation?") },
        text = { Text("All messages are stored only on this device. This deletes them permanently.") },
        confirmButton = { TextButton(onClick = { vm.action { vm.repository.dao.clearMessages() }; clear = false }) { Text("Delete messages") } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("Keep") } })
}
