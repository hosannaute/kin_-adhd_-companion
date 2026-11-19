package app.kin.ui

import app.kin.data.resolved
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kin.data.UserPreferences
import app.kin.domain.Evolution
import app.kin.domain.Rewards
import app.kin.features.KinViewModel
import app.kin.features.PipAvailability
import app.kin.features.PipFocusState
import app.kin.services.BreakTimerService

@Composable
fun FocusScreen(vm: KinViewModel, pref: UserPreferences, timer: PipFocusState, enterPip: () -> Unit, pipSettings: () -> Unit) {
    val activeBreak by vm.activeBreak.collectAsStateWithLifecycle()
    val latest by vm.latestFocus.collectAsStateWithLifecycle()
    val finished by vm.finishedFocus.collectAsStateWithLifecycle()
    val completedBreak by vm.completedBreakRequest.collectAsStateWithLifecycle()
    val companion by vm.companion.collectAsStateWithLifecycle()
    val stage = Evolution.at(companion?.xp ?: 0)
    var breakTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showBreakSetup by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(completedBreak?.id) {
        completedBreak?.let {
            breakTaskId = it.taskId; showBreakSetup = true; vm.clearCompletedBreakRequest()
        }
    }
    val breakValue = activeBreak
    if (breakValue != null && breakValue.phase in listOf("RUNNING", "PAUSED", "DUE")) {
        ActiveBreakScreen(vm, breakValue, { vm.chooseBreakFocus(breakValue) })
        return
    }
    val session = timer.session?.takeUnless { it.ended }
    val choosingFocus by vm.focusSetupRequested.collectAsStateWithLifecycle()
    LaunchedEffect(session?.id) { if (session != null) vm.focusSetupRequested.value = false }
    if (session == null && !choosingFocus) {
        LazyColumn(Modifier.fillMaxSize().testTag("focus_screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { PageTitle("A little focus", "Rest first, or start one small task.") }
            finished?.let { value ->
                item {
                    FocusCompletionCard(
                        session = value,
                        onStop = { vm.dismissFinished(value.id) },
                        onBreak = { vm.requestCompletedBreak(value.id) },
                        stage = stage,
                        reducedMotion = pref.reducedMotion
                    )
                }
            }
            item {
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = CircleShape, color = KinPalette.current.warningContainer, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Coffee, null, tint = KinPalette.current.warningOnContainer, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Take a scrolling break", style = MaterialTheme.typography.titleLarge)
                            Text("Timed break with return prompt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("Choose a task to return to. Your timer stays on screen until the break ends.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { breakTaskId = latest?.taskId; showBreakSetup = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                        Text("Plan a scrolling break")
                    }
                }
            }
            item {
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Timer, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Focus now", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Longer sessions earn higher XP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text("One task. One floating timer. When you finish, a congratulations window celebrates your progress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { vm.focusSetupRequested.value = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                        Text("Choose focus duration")
                    }
                }
            }
        }
        if (showBreakSetup) BreakSetupDialog(vm, breakTaskId, { showBreakSetup = false })
        return
    }
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val count by vm.sessionCount.collectAsStateWithLifecycle()
    val recent by vm.timerOptions.collectAsStateWithLifecycle()
    val options = recent ?: return
    var minutes by rememberSaveable { mutableStateOf(options.focusMinutes.toString()) }
    var taskId by rememberSaveable { mutableStateOf(tasks.firstOrNull { it.id == options.taskId && !it.resolved }?.id) }
    var taskPicked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tasks) { if (!taskPicked && taskId == null) taskId = tasks.firstOrNull { it.id == options.taskId && !it.resolved }?.id }

    val selectedMinutes = minutes.toIntOrNull()?.takeIf { it in 1..180 }
    val targetXp = if (session != null) Rewards.focusForMillis(session.durationMillis) else selectedMinutes?.let(Rewards::focus)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("focus_screen"),
            contentPadding = PaddingValues(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("A little focus", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(session?.taskTitle ?: "You don't have to do everything. Just be here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (session == null) {
                item {
                    SoftCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Choose your focus duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            targetXp?.let { XpBadge("+$it XP") }
                        }
                        MinutesPicker(minutes, { minutes = it }, listOf(5, 10, 15, 25, 45), "Focus minutes")
                        Text(
                            "Longer completed focus earns higher XP: 5m (+15) · 15m (+35) · 25m (+65) · 45m (+130) · 60m (+190)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ChoiceList(
                            "Focus on",
                            tasks.firstOrNull { it.id == taskId }?.title ?: "Just a little time for me",
                            listOf("0" to "Just a little time for me") + tasks.filter { !it.resolved }.map { it.id.toString() to it.title }
                        ) { taskPicked = true; taskId = it.toLong().takeIf { key -> key > 0 } }
                    }
                }
                item { TextButton(onClick = { vm.focusSetupRequested.value = false }) { Text("Back to break or focus") } }
            }
            item {
                val palette = KinPalette.current
                val millis = if (session != null) timer.remainingMillis else (minutes.toIntOrNull() ?: 0) * 60_000L
                val seconds = (millis + 999) / 1000
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(Brush.verticalGradient(listOf(palette.heroStart, palette.heroEnd)))
                        .padding(vertical = 22.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CompanionArt(stage, pref.reducedMotion, Modifier.size(90.dp), if (session?.running == true) CreatureMood.IDLE else CreatureMood.SLEEPING)
                    Box(Modifier.size(226.dp), contentAlignment = Alignment.Center) {
                        Surface(
                            Modifier.fillMaxSize().padding(12.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp
                        ) { }
                        CircularProgressIndicator(
                            progress = { if (session != null) (millis.toFloat() / session.durationMillis).coerceIn(0f, 1f) else 1f },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                            strokeWidth = 9.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "%02d:%02d".format(seconds / 60, seconds % 60),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.displayMedium
                            )
                            Text(
                                if (session?.running == false) "Resting for a moment" else "One moment at a time",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            targetXp?.let { XpBadge("Complete for +$it XP") }
                        }
                    }
                }
            }
            item {
                val current = session
                if (current == null) {
                    Button(
                        enabled = selectedMinutes != null,
                        onClick = {
                            vm.action {
                                val mins = minutes.toInt()
                                vm.app.timerPreferences.focusChoice(mins, taskId)
                                vm.repository.startFocus(mins, tasks.firstOrNull { it.id == taskId })
                                BreakTimerService.start(vm.app)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Outlined.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Start focus")
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { vm.action { if (current.running) vm.repository.pauseFocus(current.id) else vm.repository.resumeFocus(current.id) } },
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                        ) {
                            Icon(if (current.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (current.running) "Pause" else "Resume")
                        }
                        FilledTonalButton(
                            onClick = { vm.action { vm.repository.stopFocus(current.id) } },
                            modifier = Modifier.weight(1f).heightIn(min = 50.dp)
                        ) {
                            Text("Finish early")
                        }
                    }
                }
            }
            if (session != null) item {
                SoftCard {
                    Text("Floating timer & Picture-in-Picture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Your movable focus timer bubble appears when you switch apps, and a Congratulations window pops up when time is complete.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (timer.availability) {
                        PipAvailability.AVAILABLE -> {
                            OutlinedButton(onClick = enterPip, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.PictureInPictureAlt, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Open Picture-in-Picture")
                            }
                            Text("Closing the window keeps your session running. Finish early ends it without XP.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PipAvailability.DISABLED -> {
                            Text("Picture-in-Picture is disabled for KIN in Android settings. Your timer still works here.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = pipSettings) { Text("Allow Picture-in-Picture") }
                        }
                        PipAvailability.UNSUPPORTED -> Text("This device doesn't support Picture-in-Picture. Your floating timer still works.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text("$count completed sessions · No pressure to keep a streak", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            item {
                Text(
                    "Finishing early saves the interruption without XP. Completion notifications may be delayed by Android battery controls.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
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
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompanionArt(Evolution.at(companion?.xp ?: 0), true, Modifier.size(50.dp))
                Column(Modifier.weight(1f)) {
                    Text(pref.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    StatusPill("Offline guided companion", active = true)
                }
                IconButton(onClick = { clear = true }, enabled = messages.isNotEmpty()) {
                    Icon(Icons.Outlined.DeleteOutline, "Delete conversation")
                }
            }
        }
        HorizontalDivider(color = KinPalette.current.cardBorder.copy(alpha = 0.6f))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            state = list,
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SoftCard {
                    Text("A small space to breathe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Tell me what's making it hard to start. I use offline templates, your tasks, and goals to offer gentle next steps. I'm not an AI or a therapist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
                    val shape = if (message.fromUser) RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)
                        else RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
                    Surface(
                        Modifier.widthIn(max = 304.dp),
                        shape = shape,
                        color = if (message.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = if (message.fromUser) null else BorderStroke(1.dp, KinPalette.current.cardBorder)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (message.fromUser) "You" else pref.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (message.fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(message.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 10.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Overwhelmed", "Exhausted", "Distracted").forEach { feeling ->
                    SuggestionChip(onClick = { vm.send("I'm ${feeling.lowercase()}") }, label = { Text(feeling) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    input,
                    { input = it.take(2000) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("What's on your mind?") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4
                )
                FilledIconButton(
                    onClick = { vm.send(input); input = "" },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Send, "Send message")
                }
            }
        }
    }
    if (clear) AlertDialog(
        onDismissRequest = { clear = false },
        title = { Text("Clear this conversation?") },
        text = { Text("All messages are stored only on this device. This deletes them permanently.") },
        confirmButton = { TextButton(onClick = { vm.action { vm.repository.dao.clearMessages() }; clear = false }) { Text("Delete messages") } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("Keep") } }
    )
}
