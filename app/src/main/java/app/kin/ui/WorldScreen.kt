package app.kin.ui

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kin.data.*
import app.kin.domain.Evolution
import app.kin.features.KinViewModel

@Composable
fun WorldScreen(vm: KinViewModel, pref: UserPreferences) {
    val companion by vm.companion.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val achievements by vm.achievements.collectAsStateWithLifecycle()
    val xp = companion?.xp ?: 0
    val stage = Evolution.at(xp)
    val next = Evolution.entries.getOrNull(stage.ordinal + 1)
    var goalEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PersonalGoal?>(null) }
    var deleting by remember { mutableStateOf<PersonalGoal?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Your little world", "Every step counts. Even the small ones.") }
        item {
            Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(28.dp))) {
                WorldLandscape(modifier = Modifier.fillMaxSize(), unlocked = stage.ordinal)
                CompanionArt(stage, pref.reducedMotion, Modifier.align(Alignment.BottomCenter).size(210.dp), CreatureMood.CELEBRATING)
            }
        }
        item { SoftCard {
            Text("${pref.name} · ${stage.label}", style = MaterialTheme.typography.titleLarge)
            Text(if (next == null) "$xp XP · Fully evolved, always growing" else "$xp / ${next.threshold} XP · ${next.label} is ahead")
            LinearProgressIndicator(progress = { if (next == null) 1f else ((xp - stage.threshold).toFloat() / (next.threshold - stage.threshold)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text("Missed days never take away your progress.", style = MaterialTheme.typography.bodySmall)
        } }
        item { SoftCard {
            Text("Evolution stages", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Evolution.entries.forEach { evolution ->
                    Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CompanionArt(evolution, true, Modifier.size(84.dp))
                        Text(evolution.label, style = MaterialTheme.typography.labelMedium)
                        Text("${evolution.threshold} XP${if (xp >= evolution.threshold) " · reached" else ""}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(when {
                stage.ordinal >= 2 -> "Your cottage and flower garden are growing with you."
                stage.ordinal >= 1 -> "A little cottage has appeared in your clearing."
                else -> "A peaceful clearing. A cottage unlocks at 50 XP, flowers at 150 XP."
            })
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Your goals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = { editing = null; goalEditor = true }) { Icon(Icons.Outlined.Add, null); Text(" Add goal") }
        } }
        if (goals.isEmpty()) item { SoftCard { Text("What kind of person do you want to become?")
            Text("A goal can be a direction, rather than another deadline.") } }
        items(goals, key = { it.id }) { goal -> SoftCard {
            Text(goal.title, style = MaterialTheme.typography.titleMedium)
            if (goal.why.isNotBlank()) Text(goal.why)
            if (goal.achieved) Text("Milestone reached · +50 XP", color = MaterialTheme.colorScheme.primary)
            Row {
                if (!goal.achieved) TextButton(onClick = { vm.action { vm.repository.achieveGoal(goal.id) } }) { Text("Mark achieved") }
                TextButton(onClick = { editing = goal; goalEditor = true }) { Text("Edit") }
                IconButton(onClick = { deleting = goal }) { Icon(Icons.Outlined.DeleteOutline, "Delete goal ${goal.title}") }
            }
        } }
        item { Text("Memories of progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (achievements.isEmpty()) item { Text("Your completed adventures and focus sessions will be remembered here.") }
        items(achievements, key = { it.key }) { achievement -> SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text(achievement.title, Modifier.weight(1f).padding(horizontal = 12.dp))
                IconButton(onClick = { vm.action { vm.repository.dao.deleteAchievement(achievement.key) } }) { Icon(Icons.Outlined.Close, "Delete memory") }
            }
        } }
    }
    if (goalEditor) GoalEditor(editing, { goalEditor = false }, { vm.action { vm.repository.saveGoal(it) }; goalEditor = false })
    deleting?.let { goal -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this goal?") },
        text = { Text("Its tasks will be kept without a goal association. Earned XP stays with you.") },
        confirmButton = { TextButton(onClick = { vm.action { vm.repository.deleteGoal(goal.id) }; deleting = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }) }
}

@Composable
private fun GoalEditor(goal: PersonalGoal?, dismiss: () -> Unit, save: (PersonalGoal) -> Unit) {
    var title by remember { mutableStateOf(goal?.title ?: "") }
    var why by remember { mutableStateOf(goal?.why ?: "") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("A direction for you") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Who do you want to become?") })
            OutlinedTextField(why, { why = it }, label = { Text("Why does this matter to you?") }, minLines = 3)
        }
    }, confirmButton = { TextButton(onClick = { save((goal ?: PersonalGoal(title = title, why = why)).copy(title = title, why = why)) }, enabled = title.isNotBlank()) { Text("Save goal") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
