package app.kin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.kin.data.UserPreferences
import app.kin.domain.*
import app.kin.features.KinViewModel
import app.kin.services.*

@Composable
fun TimerAlertSettings(vm: KinViewModel, pref: UserPreferences, notificationsEnabled: Boolean) {
    val context = LocalContext.current
    SoftCard {
        Text("Timer alerts", style = MaterialTheme.typography.titleMedium)
        Text("One short chime when a break ends. A softer chime when focus finishes. The countdown stays silent.")
        ChoiceList("Alert mode",pref.alertMode.label,AlertMode.entries.map { it.name to it.label }) {
            vm.action { vm.app.preferences.setAlertMode(AlertMode.valueOf(it)) }
        }
        Text("Quiet hours, phone notification volume and Do Not Disturb apply. Android channel settings can further silence or change an alert.", style = MaterialTheme.typography.bodySmall)
        if (!notificationsEnabled) Text("Enable Android notifications to hear timer alerts.")
        OutlinedButton(enabled = notificationsEnabled, onClick = { vm.action { TimerAlerts.preview(context,TimerAlertKind.BREAK) } }, modifier = Modifier.fillMaxWidth()) { Text("Test break alert") }
        OutlinedButton(enabled = notificationsEnabled, onClick = { vm.action { TimerAlerts.preview(context,TimerAlertKind.FOCUS) } }, modifier = Modifier.fillMaxWidth()) { Text("Test focus alert") }
        TimerAlertKind.entries.forEach { kind -> TextButton(onClick = {
            try { context.startActivity(TimerAlerts.settings(context,kind,pref.alertMode)) }
            catch (_: RuntimeException) { vm.reportError("Open Android Settings → Apps → KIN → Notifications to review sounds.") }
        }) { Text("${kind.label} in Android settings") } }
    }
}
