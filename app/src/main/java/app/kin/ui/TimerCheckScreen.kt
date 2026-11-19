package app.kin.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kin.features.KinViewModel
import app.kin.services.BreakTimerService

@Composable
fun TimerCheckScreen(vm: KinViewModel, onBack: () -> Unit, openFocus: () -> Unit) {
    var permissions by remember { mutableStateOf(false) }
    if (permissions) { PermissionSetupScreen(vm) { permissions=false }; return }
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val rest by vm.activeBreak.collectAsStateWithLifecycle()
    val focus by vm.focus.collectAsStateWithLifecycle()
    val status by BreakTimerService.status.collectAsStateWithLifecycle()
    var setup by remember { mutableStateOf(false) }
    var switched by rememberSaveable { mutableStateOf(false) }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var alerted by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick=onBack) { Text("Back to Settings") }
        PageTitle("Check your timer","${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
        SoftCard {
            Text("1 · Check permissions",style=MaterialTheme.typography.titleMedium)
            TecnoBatteryHint()
            Text("Allow the companion window and notifications. Review KIN's battery settings for background restrictions.")
            OutlinedButton(onClick={ permissions=true }) { Text("Review timer permissions") }
        }
        SoftCard {
            Text("2 · Try a one-minute break",style=MaterialTheme.typography.titleMedium)
            Text("Choose a pending task, then open another app. Move and tap the timer. Lock and unlock the phone, and wait for the large reminder and alert. This check does not complete your task or award XP.")
            Button(enabled=rest==null && focus==null && tasks.any { !it.completed && !it.skipped }, onClick={ setup=true }, modifier=Modifier.fillMaxWidth()) { Text("Set up one-minute check") }
            if (tasks.none { !it.completed && !it.skipped }) Text("Add a pending task in Tasks first.")
            if (rest!=null || focus!=null) Text("Finish the current timer before starting another check.")
            Text(status.message,style=MaterialTheme.typography.bodySmall)
        }
        SoftCard {
            Text("3 · Record what you saw",style=MaterialTheme.typography.titleMedium)
            listOf(Triple(switched,{ v:Boolean -> switched=v },"Timer stayed visible across apps"),
                Triple(unlocked,{ v:Boolean -> unlocked=v },"Timer returned after unlocking"),
                Triple(alerted,{ v:Boolean -> alerted=v },"Ending reminder and chosen alert worked")).forEach { (checked,change,label) ->
                Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(checked,change); Text(label,Modifier.weight(1f)) }
            }
            Text("These are your observations for this check. Also try a longer break and your usual battery-saver setting. Android can hide overlays on secure screens and suspend a force-stopped app.",style=MaterialTheme.typography.bodySmall)
        }
    }
    if (setup) BreakSetupDialog(vm,onDismiss={ setup=false },onStarted={ setup=false;openFocus() },initialMinutes=1)
}
