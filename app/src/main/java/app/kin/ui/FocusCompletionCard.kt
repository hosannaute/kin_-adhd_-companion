package app.kin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kin.data.FocusSession

@Composable
fun FocusCompletionCard(session: FocusSession, onStop: () -> Unit, onBreak: () -> Unit) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.CheckCircle,null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column {
                Text("Congratulations!", style = MaterialTheme.typography.headlineSmall)
                Text("Focus complete · +15 XP", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("You made time for ${session.taskTitle}. Your progress is saved. You can stop here or enjoy a break.")
        Button(onClick = onBreak, modifier = Modifier.fillMaxWidth()) { Text("Take a break") }
        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
    }
}
