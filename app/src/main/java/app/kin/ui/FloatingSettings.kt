package app.kin.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.core.net.toUri
import app.kin.data.CompanionModeOptions
import app.kin.features.KinViewModel
import app.kin.services.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FloatingSettings(vm: KinViewModel) {
    val context = LocalContext.current
    val options by vm.app.companionMode.flow.collectAsStateWithLifecycle(CompanionModeOptions())
    val status by FloatingCompanionService.status.collectAsStateWithLifecycle()
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var usageAllowed by remember { mutableStateOf(UsageAccess.granted(context)) }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var search by rememberSaveable { mutableStateOf("") }
    var threshold by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            overlayAllowed = Settings.canDrawOverlays(context); usageAllowed = UsageAccess.granted(context)
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { UsageAccess.apps(context) } }
    fun settings(intent: Intent) {
        try { context.startActivity(intent) } catch (_: RuntimeException) { notice = "Android couldn't open this permission screen. Use your device's app settings for KIN." }
    }
    SoftCard {
        Text("Floating companion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Keep your companion nearby in other apps. Drag the creature to move it, or tap it for a small check-in. Quiet hours hide it. You can stop it anytime.")
        Text(status.message, color = MaterialTheme.colorScheme.primary)
        if (!overlayAllowed) {
            Text("Optional: allow display over other apps. Denying this leaves all core features available.")
            OutlinedButton(onClick = { settings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())) }) { Text("Allow display over other apps") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { FloatingCompanionService.start(context) }, enabled = overlayAllowed) {
                Text(if (status.running) "Resume floating mode" else "Start floating companion")
            }
        }
        if (status.running) OutlinedButton(onClick = { FloatingCompanionService.stop(context) }) { Text("Stop floating companion") }
        Text("Android uses a foreground-service notification with Pause and Stop controls. Allow notifications above to show it in the drawer. After Android stops the service or your phone restarts, start it here again.", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Text("Gentle app-time reminders", fontWeight = FontWeight.Bold)
        Text("Optional usage access lets KIN measure foreground time in apps you select below. It cannot see scrolling gestures, messages, videos, or whether content is useful. All processing stays on this device; no usage history is saved or uploaded.")
        if (!usageAllowed) OutlinedButton(onClick = { settings(UsageAccess.settingsIntent()) }) { Text("Choose usage access for KIN") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(options.monitorUsage, { enabled -> vm.action { vm.app.companionMode.monitoring(enabled) } }, enabled = usageAllowed || options.monitorUsage)
            Text(" App-time reminders${if (!usageAllowed) " · permission off" else ""}")
        }
        OutlinedTextField(threshold, { threshold = it.filter(Char::isDigit).take(3) },
            label = { Text("Minutes before a check-in (1–180)") },
            placeholder = { Text(options.thresholdMinutes.toString()) }, singleLine = true)
        TextButton(onClick = { vm.action { vm.app.companionMode.threshold(threshold.toInt()) }; threshold = "" },
            enabled = threshold.toIntOrNull()?.let { it in 1..180 } == true) { Text("Save threshold") }
        Text("Current threshold: ${options.thresholdMinutes} minutes. App time resets when you switch apps or lock the screen. Check-ins are limited to one per 10 minutes; Taking a break snoozes them for 10 minutes.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(search, { search = it }, label = { Text("Find an app to select") }, singleLine = true)
        Text("${options.selectedApps.size} apps selected")
        Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }.forEach { app ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(app.packageName in options.selectedApps, { selected -> vm.action { vm.app.companionMode.select(app.packageName, selected) } })
                    Text(app.label, Modifier.weight(1f))
                }
            }
            if (apps.isEmpty()) Text("No launchable apps found on this device.")
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
