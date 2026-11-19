package app.kin.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import app.kin.data.CompanionModeOptions
import app.kin.features.KinViewModel
import app.kin.services.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingSettings(vm: KinViewModel) {
    val context = LocalContext.current
    val options by vm.app.companionMode.flow.collectAsStateWithLifecycle(CompanionModeOptions())
    val status by FloatingCompanionService.status.collectAsStateWithLifecycle()
    val scrollStatus by ScrollDetectorService.status.collectAsStateWithLifecycle()
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var usageAllowed by remember { mutableStateOf(UsageAccess.granted(context)) }
    var accessibilityAllowed by remember { mutableStateOf(ScrollDetectorService.isEnabled(context)) }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var search by rememberSaveable { mutableStateOf("") }
    var threshold by rememberSaveable { mutableStateOf("") }
    var scrollThresholdInput by rememberSaveable { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayAllowed = Settings.canDrawOverlays(context)
                usageAllowed = UsageAccess.granted(context)
                accessibilityAllowed = ScrollDetectorService.isEnabled(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { UsageAccess.apps(context) } }
    fun settings(intent: Intent) {
        try { context.startActivity(intent) } catch (_: RuntimeException) { notice = "Android couldn't open this permission screen. Use your device's app settings for KIN." }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SoftCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Auto scroll detection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                StatusPill(if (accessibilityAllowed && options.scrollDetectionEnabled) "Active" else "Off", active = accessibilityAllowed && options.scrollDetectionEnabled)
            }
            Text(
                "Detects continuous scrolling offline and pops up a gentle window when you've been scrolling past your limit (for example, more than 10 minutes). KIN never reads screen text, messages, or passwords.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (accessibilityAllowed) scrollStatus.message else "Enable \"KIN Auto Scroll Detection\" in Android Accessibility settings to activate automatic scroll alerts.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (!accessibilityAllowed) {
                Button(onClick = { settings(ScrollDetectorService.settingsIntent()) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.TouchApp, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Enable Auto Scroll Detection")
                }
            } else {
                OutlinedButton(onClick = { settings(ScrollDetectorService.settingsIntent()) }) {
                    Text("Accessibility settings")
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Scroll alert window", fontWeight = FontWeight.SemiBold)
                    Text("Show a reminder window when scrolling exceeds your limit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(options.scrollDetectionEnabled, { enabled -> vm.action { vm.app.companionMode.scrollDetection(enabled) } })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Detect across all apps", fontWeight = FontWeight.SemiBold)
                    Text("Turn off to monitor only the specific apps you select below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(options.scrollAllApps, { all -> vm.action { vm.app.companionMode.scrollAllApps(all) } })
            }
            Text("Alert after scrolling for (${options.scrollThresholdMinutes} min)", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(5, 10, 15, 20, 30).forEach { mins ->
                    FilterChip(
                        selected = options.scrollThresholdMinutes == mins,
                        onClick = { vm.action { vm.app.companionMode.scrollThreshold(mins) } },
                        label = { Text("$mins min") }
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    scrollThresholdInput,
                    { scrollThresholdInput = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Custom scroll limit (1–180 min)") },
                    placeholder = { Text(options.scrollThresholdMinutes.toString()) },
                    singleLine = true
                )
                TextButton(
                    onClick = {
                        vm.action { vm.app.companionMode.scrollThreshold(scrollThresholdInput.toInt()) }
                        scrollThresholdInput = ""
                    },
                    enabled = scrollThresholdInput.toIntOrNull()?.let { it in 1..180 } == true
                ) { Text("Save") }
            }
            if (accessibilityAllowed) {
                FilledTonalButton(
                    onClick = {
                        if (!ScrollDetectorService.preview()) {
                            notice = "Auto scroll detection is connecting. Toggle it on in Accessibility settings if needed."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test scroll alert window")
                }
            }
        }

        SoftCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Floating companion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                StatusPill(if (status.running) "Running" else "Off", active = status.running)
            }
            Text("Keep your companion nearby in other apps. Drag the creature to move it, or tap it for a small check-in. Quiet hours hide it. You can stop it anytime.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(status.message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            if (!overlayAllowed) {
                Text("Optional: allow display over other apps. Denying this leaves all core features available.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { settings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())) }) { Text("Allow display over other apps") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { FloatingCompanionService.start(context) }, enabled = overlayAllowed) {
                    Text(if (status.running) "Resume floating mode" else "Start floating companion")
                }
            }
            if (status.running) OutlinedButton(onClick = { FloatingCompanionService.stop(context) }) { Text("Stop floating companion") }
            Text("Android uses a foreground-service notification with Pause and Stop controls. Allow notifications above to show it in the drawer. After Android stops the service or your phone restarts, start it here again.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("Gentle app-time reminders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Optional usage access lets KIN measure foreground time in apps you select below. All processing stays on this device; no usage history is saved or uploaded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!usageAllowed) OutlinedButton(onClick = { settings(UsageAccess.settingsIntent()) }) { Text("Choose usage access for KIN") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(options.monitorUsage, { enabled -> vm.action { vm.app.companionMode.monitoring(enabled) } }, enabled = usageAllowed || options.monitorUsage)
                Text(" App-time reminders${if (!usageAllowed) " · permission off" else ""}")
            }
            OutlinedTextField(
                threshold,
                { threshold = it.filter(Char::isDigit).take(3) },
                label = { Text("Minutes before a check-in (1–180)") },
                placeholder = { Text(options.thresholdMinutes.toString()) },
                singleLine = true
            )
            TextButton(
                onClick = { vm.action { vm.app.companionMode.threshold(threshold.toInt()) }; threshold = "" },
                enabled = threshold.toIntOrNull()?.let { it in 1..180 } == true
            ) { Text("Save threshold") }
            Text("Current threshold: ${options.thresholdMinutes} minutes. App time resets when you switch apps or lock the screen. Check-ins are limited to one per 10 minutes; Taking a break snoozes them for 10 minutes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(search, { search = it }, label = { Text("Find an app to select") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("${options.selectedApps.size} apps selected", style = MaterialTheme.typography.labelMedium)
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
}
