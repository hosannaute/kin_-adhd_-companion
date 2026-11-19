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
import app.kin.domain.Evolution

@Composable
fun ScrollAlertPanel(
    companionName: String,
    appLabel: String,
    minutes: Int,
    stage: Evolution,
    reducedMotion: Boolean,
    maxHeight: Float = 560f,
    onGoals: () -> Unit,
    onQuickFocus: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = KinPalette.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, palette.warning.copy(alpha = 0.45f))
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
                    .background(Brush.verticalGradient(listOf(palette.warningContainer, MaterialTheme.colorScheme.surface)))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Outlined.TouchApp, null, Modifier.size(14.dp), tint = palette.warningOnContainer)
                        Text("SCROLL CHECK-IN", style = MaterialTheme.typography.labelSmall, color = palette.warningOnContainer)
                    }
                }
                Text(
                    "You've been scrolling for more than $minutes min",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                CompanionArt(stage, reducedMotion, Modifier.fillMaxWidth().height(136.dp), CreatureMood.WAVING)
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (appLabel.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Text(
                            "In $appLabel · $companionName is checking in",
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "Take a breath. Are you still choosing to scroll, or ready to return to your goals?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onGoals, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Flag, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Return to my goals")
                }
                FilledTonalButton(onClick = onQuickFocus, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
                    Icon(Icons.Outlined.Timer, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start a quick focus")
                }
                OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
                    Text("Snooze 10 min")
                }
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}
