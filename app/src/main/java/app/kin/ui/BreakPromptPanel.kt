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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kin.data.*
import app.kin.domain.*

private enum class BreakConfirmStep { NONE, SNOOZE, STOP }

@Composable
fun BreakPromptPanel(
    value: BreakSession,
    pref: UserPreferences,
    stage: Evolution,
    snoozed: Boolean = false,
    maxHeight: Float = 560f,
    onStart: () -> Unit,
    onSnooze: () -> Unit,
    onStop: () -> Unit
) {
    var confirmStep by remember(value.id, value.revision, snoozed) { mutableStateOf(BreakConfirmStep.NONE) }
    val palette = KinPalette.current

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val headerColors = when (confirmStep) {
                BreakConfirmStep.STOP -> listOf(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.surface)
                BreakConfirmStep.SNOOZE -> listOf(palette.warningContainer, MaterialTheme.colorScheme.surface)
                BreakConfirmStep.NONE -> listOf(palette.heroStart, palette.heroEnd)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(headerColors))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (confirmStep) {
                    BreakConfirmStep.NONE -> {
                        val headline = if (snoozed) {
                            if (value.remainingMillis / 60_000 == 5L) "Five more minutes" else "${value.remainingMillis / 60_000} more minutes"
                        } else "Your break is over"
                        Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                            Text(
                                if (snoozed) "SNOOZED" else "BREAK COMPLETE",
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        CompanionArt(stage, pref.reducedMotion, Modifier.fillMaxWidth().height(150.dp), CreatureMood.WAVING)
                    }
                    BreakConfirmStep.SNOOZE -> {
                        Surface(shape = RoundedCornerShape(999.dp), color = palette.warningContainer) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Outlined.Snooze, null, Modifier.size(14.dp), tint = palette.warningOnContainer)
                                Text("CHECK IN", style = MaterialTheme.typography.labelSmall, color = palette.warningOnContainer)
                            }
                        }
                        Text("Are you sure?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        CompanionArt(stage, pref.reducedMotion, Modifier.fillMaxWidth().height(120.dp), CreatureMood.THINKING)
                    }
                    BreakConfirmStep.STOP -> {
                        XpBadge("-${Rewards.BREAK_STOP_PENALTY} XP to ${pref.name}", positive = false)
                        Text("Are you sure?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        CompanionArt(stage, pref.reducedMotion, Modifier.fillMaxWidth().height(120.dp), CreatureMood.THINKING)
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (confirmStep) {
                    BreakConfirmStep.NONE -> {
                        Surface(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("NEXT ADVENTURE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(value.taskTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                if (value.goalTitle.isNotBlank()) {
                                    Text(value.goalTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        Text(
                            BreakPolicy.message(value.taskTitle, value.goalTitle, value.snoozeCount, pref.reminderStyle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start task")
                        }
                        if (!snoozed && AlertPolicy.canSnooze(value.snoozeCount, pref.snoozeLimit)) {
                            OutlinedButton(
                                onClick = { confirmStep = BreakConfirmStep.SNOOZE },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                            ) {
                                Text("Snooze ${pref.snoozeMinutes} min")
                            }
                        }
                        if (!snoozed && !AlertPolicy.canSnooze(value.snoozeCount, pref.snoozeLimit)) {
                            Text(
                                "Your snooze limit is reached. Choose a focus duration or end the break.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        TextButton(onClick = { confirmStep = BreakConfirmStep.STOP }) {
                            Text("Stop break", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    BreakConfirmStep.SNOOZE -> {
                        Text(
                            "Snoozing adds ${pref.snoozeMinutes} more minutes before returning to \"${value.taskTitle}\". Ready to start one tiny step instead?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Start task instead")
                        }
                        OutlinedButton(
                            onClick = { confirmStep = BreakConfirmStep.NONE; onSnooze() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                        ) {
                            Text("Yes, snooze ${pref.snoozeMinutes} min")
                        }
                        TextButton(onClick = { confirmStep = BreakConfirmStep.NONE }) {
                            Text("Go back")
                        }
                    }

                    BreakConfirmStep.STOP -> {
                        Text(
                            "Stopping this break without starting your task will reduce ${pref.name}'s XP by ${Rewards.BREAK_STOP_PENALTY} XP.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Protect XP · Start task")
                        }
                        Button(
                            onClick = { confirmStep = BreakConfirmStep.NONE; onStop() },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text("Yes, stop break (-${Rewards.BREAK_STOP_PENALTY} XP)")
                        }
                        TextButton(onClick = { confirmStep = BreakConfirmStep.NONE }) {
                            Text("Go back")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BreakCountdownPanel(
    value: BreakSession,
    left: Long,
    pref: UserPreferences,
    stage: Evolution,
    onOpen: () -> Unit,
    onPause: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, KinPalette.current.cardBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompanionArt(stage, pref.reducedMotion, Modifier.size(48.dp), CreatureMood.WAVING)
                Column {
                    Text(if (value.phase == "PAUSED") "Break paused" else "Scrolling break", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(app.kin.services.BreakNotifications.format(left), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPause) { Text(if (value.phase == "PAUSED") "Resume" else "Pause") }
                TextButton(onClick = onOpen) { Text("Open KIN") }
            }
        }
    }
}
