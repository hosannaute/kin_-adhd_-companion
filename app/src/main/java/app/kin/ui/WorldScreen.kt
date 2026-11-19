package app.kin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kin.data.*
import app.kin.domain.Evolution
import app.kin.features.KinViewModel

@Composable
fun WorldScreen(vm: KinViewModel, pref: UserPreferences, openChat: () -> Unit = {}) {
    val companion by vm.companion.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val achievements by vm.achievements.collectAsStateWithLifecycle()
    val xp = companion?.xp ?: 0
    val stage = Evolution.at(xp)
    val next = Evolution.entries.getOrNull(stage.ordinal + 1)
    var goalEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PersonalGoal?>(null) }
    var deleting by remember { mutableStateOf<PersonalGoal?>(null) }
    val palette = KinPalette.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            PageTitle("Your little world", "Every step counts. Even the small ones.") {
                FilledTonalButton(onClick = openChat, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Talk to your companion")
                }
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 3.dp,
                border = BorderStroke(1.dp, palette.cardBorder)
            ) {
                Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(28.dp))) {
                    WorldLandscape(modifier = Modifier.fillMaxSize(), night = pref.darkMode, unlocked = stage.ordinal)
                    CompanionArt(stage, pref.reducedMotion, Modifier.align(Alignment.BottomCenter).size(204.dp), CreatureMood.CELEBRATING)
                    Row(
                        Modifier.align(Alignment.TopStart).padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        XpBadge("$xp XP")
                        Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)) {
                            Text(
                                stage.label,
                                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
        item {
            SoftCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${pref.name} · ${stage.label}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    XpBadge("$xp XP")
                }
                Text(
                    if (next == null) "$xp XP · Fully evolved, always growing"
                    else "$xp / ${next.threshold} XP · ${next.label} is ahead",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { if (next == null) 1f else ((xp - stage.threshold).toFloat() / (next.threshold - stage.threshold)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp))
                )
                Text(
                    "Longer focus sessions award more XP. Stopping a break without returning to your task reduces XP.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SoftCard {
                Text("Evolution stages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Evolution.entries.forEach { evolution ->
                        val reached = xp >= evolution.threshold
                        val isCurrent = evolution == stage
                        Surface(
                            Modifier.width(114.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(if (isCurrent) 1.5.dp else 1.dp, if (isCurrent) MaterialTheme.colorScheme.primary else palette.cardBorder.copy(alpha = 0.6f))
                        ) {
                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CompanionArt(evolution, true, Modifier.size(72.dp))
                                Text(evolution.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
                                Text(
                                    "${evolution.threshold} XP${if (reached) " · ✓" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Text(
                    when {
                        stage.ordinal >= 2 -> "Your cottage and flower garden are growing with you."
                        stage.ordinal >= 1 -> "A little cottage has appeared in your clearing."
                        else -> "A peaceful clearing. A cottage unlocks at 50 XP, flowers at 150 XP."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Your goals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                FilledTonalButton(onClick = { editing = null; goalEditor = true }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add goal")
                }
            }
        }
        if (goals.isEmpty()) item {
            SoftCard {
                Text("What kind of person do you want to become?", style = MaterialTheme.typography.titleMedium)
                Text("A goal can be a direction, rather than another deadline.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(goals, key = { it.id }) { goal ->
            SoftCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(goal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (goal.achieved) XpBadge("+50 XP")
                }
                if (goal.why.isNotBlank()) Text(goal.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (goal.achieved) Text("Milestone reached · +50 XP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!goal.achieved) TextButton(onClick = { vm.action { vm.repository.achieveGoal(goal.id) } }) { Text("Mark achieved") }
                    TextButton(onClick = { editing = goal; goalEditor = true }) { Text("Edit") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { deleting = goal }) { Icon(Icons.Outlined.DeleteOutline, "Delete goal ${goal.title}") }
                }
            }
        }
        item { Text("Memories of progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (achievements.isEmpty()) item {
            Text("Your completed adventures and focus sessions will be remembered here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(achievements, key = { it.key }) { achievement ->
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = palette.xpGold)
                    Text(achievement.title, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { vm.action { vm.repository.dao.deleteAchievement(achievement.key) } }) { Icon(Icons.Outlined.Close, "Delete memory") }
                }
            }
        }
    }
    if (goalEditor) GoalEditor(editing, { goalEditor = false }, { vm.action { vm.repository.saveGoal(it) }; goalEditor = false })
    deleting?.let { goal ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this goal?") },
            text = { Text("Its tasks will be kept without a goal association. Earned XP stays with you.") },
            confirmButton = { TextButton(onClick = { vm.action { vm.repository.deleteGoal(goal.id) }; deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }
        )
    }
}

@Composable
private fun GoalEditor(goal: PersonalGoal?, dismiss: () -> Unit, save: (PersonalGoal) -> Unit) {
    var title by remember { mutableStateOf(goal?.title ?: "") }
    var why by remember { mutableStateOf(goal?.why ?: "") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("A direction for you") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Who do you want to become?") })
                OutlinedTextField(why, { why = it }, label = { Text("Why does this matter to you?") }, minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { save((goal ?: PersonalGoal(title = title, why = why)).copy(title = title, why = why)) }, enabled = title.isNotBlank()) { Text("Save goal") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }
    )
}
