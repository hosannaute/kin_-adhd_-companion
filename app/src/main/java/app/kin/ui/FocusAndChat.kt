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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.kin.data.UserPreferences
import app.kin.domain.Evolution
import app.kin.features.KinViewModel
import app.kin.services.Notifications
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun FocusScreen(vm: KinViewModel, pref: UserPreferences) {
    val session by vm.focus.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val companion by vm.companion.collectAsStateWithLifecycle()
    val count by vm.sessionCount.collectAsStateWithLifecycle()
    var minutes by rememberSaveable { mutableIntStateOf(25) }
    var custom by rememberSaveable { mutableStateOf("25") }
    var taskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var left by remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(session, lifecycle) {
        val current = session ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                left = vm.repository.remaining(current)
                if (current.running && left == 0L) {
                    vm.action {
                        if (vm.repository.refreshFocus(current.id)) Notifications.show(vm.app, Int.MAX_VALUE,
                            "A little progress, well earned", "Your focus session is complete. +15 XP", "focus")
                    }
                    break
                }
                delay(250)
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        WorldLandscape(night = true, modifier = Modifier.fillMaxSize(), unlocked = 2)
        Box(Modifier.fillMaxSize().background(Color(0xFF101D32).copy(alpha=.55f)))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { Text("A little focus", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            item { Text(session?.taskTitle ?: "You don't have to do everything. Just be here.", color = Color(0xFFDEE7F2)) }
            item { CompanionArt(Evolution.at(companion?.xp ?: 0), pref.reducedMotion, Modifier.size(170.dp), CreatureMood.SLEEPING) }
            item {
                val millis = if (session != null) left else minutes * 60_000L
                val seconds = (millis + 999) / 1000
                Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { if (session != null) (millis.toFloat() / session!!.durationMillis).coerceIn(0f,1f) else 1f },
                        modifier = Modifier.fillMaxSize(), color = Color(0xFFBEB4EE), trackColor = Color(0xFF536178), strokeWidth = 5.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%02d:%02d".format(seconds / 60, seconds % 60), color = Color.White, fontSize = 48.sp)
                        Text(if (session?.running == false) "Resting for a moment" else "One moment at a time", color = Color(0xFFD4DFE9))
                    }
                }
            }
            item {
                val current = session
                if (current == null) Button(onClick = { vm.action { vm.repository.startFocus(minutes, tasks.firstOrNull { it.id == taskId }) } }) {
                    Icon(Icons.Outlined.PlayArrow, null); Text(" Start focus")
                } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.action { if (current.running) vm.repository.pauseFocus(current.id) else vm.repository.resumeFocus(current.id) } }) {
                        Text(if (current.running) "Pause" else "Resume")
                    }
                    FilledTonalButton(onClick = { vm.action { vm.repository.stopFocus(current.id) } }) { Text("Finish early") }
                }
            }
            if (session == null) {
                item { SoftCard {
                    Text("Choose your pace", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(5,15,25,45).forEach { duration -> FilterChip(minutes == duration, { minutes = duration; custom = duration.toString() }, label = { Text("$duration m") }) }
                    }
                    OutlinedTextField(custom, { value -> custom = value.filter(Char::isDigit).take(3); custom.toIntOrNull()?.takeIf { it in 1..180 }?.let { minutes = it } },
                        label = { Text("Custom minutes (1–180)") }, singleLine = true,
                        isError = custom.toIntOrNull()?.let { it !in 1..180 } ?: true,
                        supportingText = { Text("Selected duration: $minutes minutes") })
                    Text("Focus on")
                    FilterChip(taskId == null, { taskId = null }, label = { Text("Just a little time for me") })
                    tasks.filter { !it.resolved }.forEach { task ->
                        FilterChip(taskId == task.id, { taskId = task.id }, label = { Text(task.title) })
                    }
                } }
            }
            item { Text("$count completed sessions · No pressure to keep a streak", color = Color(0xFFDFE4EE), style = MaterialTheme.typography.bodySmall) }
            item { Text("Finishing early saves the interruption without XP. Completion notifications may be delayed by Android battery controls.",
                color = Color(0xFFDFE4EE), style = MaterialTheme.typography.bodySmall) }
        }
    }
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
