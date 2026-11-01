package app.kin.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.kin.data.UserPreferences
import app.kin.features.KinViewModel

@Composable
fun SettingsScreen(vm: KinViewModel, pref: UserPreferences, openFocus: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) enabled = NotificationManagerCompat.from(context).areNotificationsEnabled() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var name by remember(pref.name) { mutableStateOf(pref.name) }
    var start by remember(pref.quietStart) { mutableStateOf(pref.quietStart.toString()) }
    var end by remember(pref.quietEnd) { mutableStateOf(pref.quietEnd.toString()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Make room for you", "Gentle by default. Always in your control.") }
        item { SoftCard {
            Text("Your companion", fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it.take(32) }, label = { Text("Companion name") }, singleLine = true)
            TextButton(onClick = { vm.savePreferences(pref.copy(name = name)) }, enabled = name.isNotBlank() && name != pref.name) { Text("Save name") }
            PreferenceSwitch("Reduced motion", "Still artwork with no breathing or blinking.", pref.reducedMotion) { vm.savePreferences(pref.copy(reducedMotion = it)) }
            PreferenceSwitch("Dark mode", "A softer evening palette.", pref.darkMode) { vm.savePreferences(pref.copy(darkMode = it)) }
        } }
        item { SoftCard {
            Text("Gentle reminders", fontWeight = FontWeight.Bold)
            PreferenceSwitch("Allow task reminders", "Turn off anytime for an emergency pause.", pref.reminders) { value ->
                vm.action {
                    vm.app.preferences.save(pref.copy(reminders = value))
                    if (value) vm.repository.rescheduleAll()
                    else vm.repository.dao.allTasks().forEach { vm.app.scheduler.cancelTask(it.id) }
                }
            }
            Text(if (enabled) "Android notifications are enabled." else "Android notifications are off. Tasks and timers still work.")
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !enabled) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text(if (enabled) "Android notification settings" else "Enable notifications") }
            Text("Quiet hours (24-hour clock)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, { start = it.filter(Char::isDigit).take(2) }, modifier = Modifier.weight(1f), label = { Text("From hour") }, singleLine = true)
                OutlinedTextField(end, { end = it.filter(Char::isDigit).take(2) }, modifier = Modifier.weight(1f), label = { Text("Until hour") }, singleLine = true)
            }
            TextButton(onClick = { vm.savePreferences(pref.copy(quietStart = start.toInt(), quietEnd = end.toInt())) },
                enabled = start.toIntOrNull()?.let { it in 0..23 } == true && end.toIntOrNull()?.let { it in 0..23 } == true) { Text("Save quiet hours") }
            Text("Equal hours disable quiet hours. Reminders falling in quiet hours are skipped. Android may delay reminders; exact alarms and escalating reminders are not in this version.", style = MaterialTheme.typography.bodySmall)
        } }
        item { Button(onClick = openFocus, modifier = Modifier.fillMaxWidth()) { Text("Open focus timer") } }
        item { FloatingSettings(vm) }
        item { SoftCard {
            Text("Private, offline, yours", fontWeight = FontWeight.Bold)
            Text("No accounts, ads, analytics, or cloud chat. Your tasks, goals, and messages stay on this device. Android backup is disabled; uninstalling deletes your data.")
            Text("KIN 0.5 · Early development build", style = MaterialTheme.typography.labelMedium)
        } }
    }
}

@Composable
private fun PreferenceSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, change)
    }
}
