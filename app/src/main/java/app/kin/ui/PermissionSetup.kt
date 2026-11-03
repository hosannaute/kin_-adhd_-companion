package app.kin.ui

import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kin.features.KinViewModel
import app.kin.services.*
import kotlinx.coroutines.flow.first

@Composable
fun PermissionSetupScreen(vm: KinViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activeBreak by vm.activeBreak.collectAsStateWithLifecycle()
    val activeFocus by vm.focus.collectAsStateWithLifecycle()
    var overlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var notifications by remember { mutableStateOf(Notifications.allowed(context)) }
    var battery by remember { mutableStateOf(context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) }
    var notice by remember { mutableStateOf<String?>(null) }
    val grant = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifications = Notifications.allowed(context) }
    fun open(intent: Intent) { try { context.startActivity(intent) } catch (_: RuntimeException) { notice = "Open your phone's Settings → Apps → KIN to change this permission." } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            overlay = Settings.canDrawOverlays(context); notifications = Notifications.allowed(context)
            battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        } }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !notifications && !vm.app.preferences.notificationPromptAttempted.first()) {
            vm.app.preferences.markNotificationPromptAttempted()
            grant.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Set up your companion", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("So KIN can bring you back when your scrolling break ends. Android asks you to approve each permission.")
        SoftCard {
            Text("1 · Show over other apps", style = MaterialTheme.typography.titleMedium)
            Text(if (overlay) "Allowed · the countdown and large reminder can appear over other apps." else "Needed for the floating countdown and the large return-to-task window.")
            OutlinedButton(onClick = { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())) }) {
                Text(if (overlay) "Manage companion window" else "Allow companion window")
            }
        }
        SoftCard {
            Text("2 · Notifications", style = MaterialTheme.typography.titleMedium)
            Text(if (notifications) "Allowed · timer controls and backup reminders are available." else "Off · enable these for backup reminders and timer controls.")
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !notifications) {
                    vm.action { vm.app.preferences.markNotificationPromptAttempted() }
                    grant.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Notification permission") }
            TextButton(onClick = { open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Open notification settings") }
        }
        SoftCard {
            Text("3 · Keep timers running", style = MaterialTheme.typography.titleMedium)
            Text(if (battery) "Battery restrictions are relaxed for KIN." else "Some phones stop background timers. In KIN's battery settings, allow background activity or choose Unrestricted if available.")
            OutlinedButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }) { Text("Open KIN battery settings") }
        }
        if (overlay) {
            Button(onClick = {
                if (OverlayPreviewService.start(context)) open(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                else notice = "Couldn't start the window test. Check the permission and try again."
            }, enabled = activeBreak == null && activeFocus == null, modifier = Modifier.fillMaxWidth()) { Text("Test window over other apps") }
            if (activeBreak != null || activeFocus != null) Text("Finish the current timer before opening a sample window.", style = MaterialTheme.typography.bodySmall)
            Text("This opens your home screen, then shows a sample reminder. It does not start a break or change your tasks.", style = MaterialTheme.typography.bodySmall)
        }
        Text(if (overlay) "Ready for floating break reminders." else "Window permission is off. You can continue, but reminders will only appear inside KIN or in notifications.", fontWeight = FontWeight.Medium)
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(if (overlay) "Continue to KIN" else "Continue without floating window") }
        Text("You can revisit this setup in Settings. App usage access is optional and only needed for automatic app-time check-ins.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun rememberWindowPermission(): State<Boolean> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permission = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permission.value = Settings.canDrawOverlays(context) }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    return permission
}
