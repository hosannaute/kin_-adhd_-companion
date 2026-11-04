package app.kin.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kin.data.UserPreferences
import app.kin.domain.*
import app.kin.features.KinViewModel
import app.kin.services.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimerAlertSettings(vm: KinViewModel, pref: UserPreferences, notificationsEnabled: Boolean) {
    val context = LocalContext.current
    val sounds by vm.sounds.collectAsStateWithLifecycle()
    val busy by vm.soundBusy.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(TimerAlertKind.BREAK.name) }
    val file = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importSound(TimerAlertKind.valueOf(picking),it) }
    }
    val phone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = if (Build.VERSION.SDK_INT >= 33) result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
                else result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            uri?.let { vm.importSound(TimerAlertKind.valueOf(picking),it) }
        }
    }
    fun androidSettings(kind: TimerAlertKind) {
        try { context.startActivity(TimerAlerts.settings(context,kind,pref.alertMode,sounds.forKind(kind))) }
        catch (_: RuntimeException) { vm.reportError("Open Android Settings → Apps → KIN → Notifications to review sounds.") }
    }
    SoftCard {
        Text("Timer alerts", style = MaterialTheme.typography.titleMedium)
        Text("Make the moment yours. The countdown stays silent.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceList("Alert mode",pref.alertMode.label,AlertMode.entries.map { it.name to it.label }) {
            vm.action { vm.app.preferences.setAlertMode(AlertMode.valueOf(it)) }
        }
        TimerAlertKind.entries.forEach { kind ->
            Surface(onClick = { sheet = kind.name }, enabled = !busy, shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant) {
                ListItem(headlineContent = { Text(if (kind == TimerAlertKind.BREAK) "Break sound" else "Focus sound") },
                    supportingContent = { Text(sounds.forKind(kind).title) },
                    leadingContent = { Icon(Icons.Outlined.MusicNote,null) }, trailingContent = { Icon(Icons.Outlined.ChevronRight,null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Quiet hours, notification volume and Do Not Disturb apply. Android notification settings can override your chosen tone.", style = MaterialTheme.typography.bodySmall)
        if (!notificationsEnabled) Text("Enable Android notifications to hear timer alerts.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimerAlertKind.entries.forEach { kind -> OutlinedButton(enabled = notificationsEnabled && !busy,
                onClick = { vm.action { TimerAlerts.preview(context,kind) } }) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp,null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                Text(if (kind == TimerAlertKind.BREAK) "Test break alert" else "Test focus alert")
            } }
        }
    }
    sheet?.let { name ->
        val kind = TimerAlertKind.valueOf(name)
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (kind == TimerAlertKind.BREAK) "Break sound" else "Focus sound", style = MaterialTheme.typography.headlineSmall)
                Text("${sounds.forKind(kind).title} · chosen in KIN", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    picking = name; sheet = null
                    try { phone.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALL)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,"Choose a KIN sound")
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false)) }
                    catch (_: RuntimeException) { vm.reportError("Your phone has no sound picker. Choose Audio file or Android sound settings instead.") }
                }, modifier = Modifier.fillMaxWidth()) { Text("Phone sounds") }
                TextButton(onClick = { picking = name; sheet = null; try { file.launch(arrayOf("audio/*")) }
                    catch (_: RuntimeException) { vm.reportError("No file picker is available on this phone.") } }, modifier = Modifier.fillMaxWidth()) { Text("Audio file from storage") }
                TextButton(onClick = { sheet = null; vm.builtInSound(kind) }, modifier = Modifier.fillMaxWidth()) { Text("KIN chime") }
                TextButton(onClick = { sheet = null; vm.silentSound(kind) }, modifier = Modifier.fillMaxWidth()) { Text("No sound") }
                HorizontalDivider()
                TextButton(onClick = { sheet = null; androidSettings(kind) }, modifier = Modifier.fillMaxWidth()) { Text("Android sound settings") }
                Text("Audio clips up to 10 MB. KIN keeps a private copy on this phone. Choose sounds again when moving to another phone.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
