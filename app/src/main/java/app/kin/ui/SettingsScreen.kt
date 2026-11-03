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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.kin.data.UserPreferences
import app.kin.features.KinViewModel
import app.kin.domain.ReminderStyle

@Composable
fun SettingsScreen(vm: KinViewModel, pref: UserPreferences, openFocus: () -> Unit) {
    var setup by remember { mutableStateOf(false) }
    if (setup) { PermissionSetupScreen(vm) { setup = false }; return }
    var timerCheck by remember { mutableStateOf(false) }
    if (timerCheck) { TimerCheckScreen(vm,{ timerCheck=false },openFocus); return }
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
    LazyColumn(Modifier.fillMaxSize().testTag("settings_screen"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Make room for you", "Gentle by default. Always in your control.") }
        item { SoftCard {
            Text("Companion window setup", style = MaterialTheme.typography.titleMedium)
            Text("Check permissions, test a real window over other apps, and review background battery settings.")
            Button(onClick = { setup = true }) { Text("Review permissions and test window") }
            OutlinedButton(onClick = { timerCheck=true }) { Text("Check timer on this phone") }
        } }
        item { SoftCard {
            Text("Your companion", fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it.take(32) }, label = { Text("Companion name") }, singleLine = true)
            TextButton(onClick = { vm.savePreferences(pref.copy(name = name)) }, enabled = name.isNotBlank() && name != pref.name) { Text("Save name") }
            PreferenceSwitch("Reduced motion", "Still artwork with no breathing or blinking.", pref.reducedMotion) { vm.savePreferences(pref.copy(reducedMotion = it)) }
            PreferenceSwitch("Dark mode", "A softer evening palette.", pref.darkMode) { vm.savePreferences(pref.copy(darkMode = it)) }
        } }
        item { TimerAlertSettings(vm,pref,enabled) }
        item { SoftCard {
            Text("Reminders", fontWeight = FontWeight.Bold)
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
            val hours = (0..23).map { it.toString() to "%02d:00".format(it) }
            ChoiceList("Quiet hours start","%02d:00".format(pref.quietStart),hours) { vm.savePreferences(pref.copy(quietStart = it.toInt())) }
            ChoiceList("Quiet hours end","%02d:00".format(pref.quietEnd),hours) { vm.savePreferences(pref.copy(quietEnd = it.toInt())) }
            Text("Equal hours turn quiet hours off. Task reminders during quiet hours are skipped; timer reminders remain visible and silent. Android may delay background delivery.", style = MaterialTheme.typography.bodySmall)
        } }
        item { SoftCard {
            Text("Reminder choices",style = MaterialTheme.typography.titleMedium)
            ChoiceList("Reminder wording",pref.reminderStyle.label,ReminderStyle.entries.map { it.name to it.label }) { vm.action { vm.app.preferences.setReminderStyle(ReminderStyle.valueOf(it)) } }
            Text(if (pref.reminderStyle == ReminderStyle.GENTLE) "Encouragement and a small next step." else "A direct reminder of your chosen task and goal.", style = MaterialTheme.typography.bodySmall)
            ChoiceList("Break snooze length","${pref.snoozeMinutes} minutes",listOf(5,10,15).map { it.toString() to "$it minutes" }) { vm.action { vm.app.preferences.setSnoozeMinutes(it.toInt()) } }
            ChoiceList("Break snooze limit",if (pref.snoozeLimit == 0) "Unlimited" else "${pref.snoozeLimit} per break", listOf("0" to "Unlimited","1" to "1 per break","3" to "3 per break")) { vm.action { vm.app.preferences.setSnoozeLimit(it.toInt()) } }
            Text("Reaching your chosen limit removes Snooze. You can still start the task or end the break.", style = MaterialTheme.typography.bodySmall)
        } }
        item { Button(onClick = openFocus, modifier = Modifier.fillMaxWidth()) { Text("Open focus timer") } }
        item { BackupSettings(vm) }
        item { FloatingSettings(vm) }
        item { SoftCard {
            Text("Private, offline, yours", fontWeight = FontWeight.Bold)
            Text("No accounts, ads, analytics, or cloud chat. Your data stays on this device unless you save a backup file. Android automatic backup is disabled; save a backup before uninstalling.")
            Text("KIN 0.8 · Early development build", style = MaterialTheme.typography.labelMedium)
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
