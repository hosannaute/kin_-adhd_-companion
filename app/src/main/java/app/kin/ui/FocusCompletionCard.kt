package app.kin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kin.data.FocusSession
import app.kin.domain.Evolution
import app.kin.domain.Rewards

@Composable
fun FocusCompletionCard(
    session: FocusSession,
    onStop: () -> Unit,
    onBreak: () -> Unit,
    stage: Evolution = Evolution.BABY,
    reducedMotion: Boolean = false
) {
    val palette = KinPalette.current
    val earnedXp = Rewards.focusForMillis(session.durationMillis)
    val mins = (session.durationMillis / 60_000L).coerceAtLeast(1)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(palette.celebrationStart, palette.celebrationEnd)))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CompanionArt(stage, reducedMotion, Modifier.size(64.dp), CreatureMood.CELEBRATING)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Congratulations!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Focus complete · +$earnedXp XP",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$mins min focused · Longer sessions earn more XP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "You made time for ${session.taskTitle}. Your progress is saved. You can stop here or enjoy a break.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onBreak, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Coffee, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Take a break")
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
fun FocusCompletionOverlayPanel(
    session: FocusSession,
    companionName: String,
    stage: Evolution,
    reducedMotion: Boolean,
    maxHeight: Float = 560f,
    onBreak: () -> Unit,
    onStop: () -> Unit
) {
    val palette = KinPalette.current
    val earnedXp = Rewards.focusForMillis(session.durationMillis)
    val mins = (session.durationMillis / 60_000L).coerceAtLeast(1)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(palette.celebrationStart, palette.celebrationEnd)))
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                XpBadge("+$earnedXp XP earned")
                Text("Congratulations!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    "Focus complete · $mins min",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                CompanionArt(stage, reducedMotion, Modifier.fillMaxWidth().height(150.dp), CreatureMood.CELEBRATING)
            }
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("COMPLETED FOCUS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(session.taskTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("$companionName grew stronger from your $mins-minute focus!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                Button(onClick = onBreak, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Coffee, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Take a break")
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
                    Text("Done")
                }
            }
        }
    }
}
