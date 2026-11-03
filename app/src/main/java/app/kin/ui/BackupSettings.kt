package app.kin.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kin.features.KinViewModel
import java.time.LocalDate

@Composable
fun BackupSettings(vm: KinViewModel) {
    val busy by vm.backupBusy.collectAsStateWithLifecycle()
    val draft by vm.backupDraft.collectAsStateWithLifecycle()
    val notice by vm.backupNotice.collectAsStateWithLifecycle()
    val focus by vm.focus.collectAsStateWithLifecycle()
    val rest by vm.activeBreak.collectAsStateWithLifecycle()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::readBackup) }
    SoftCard {
        Text("Backup and restore", style = MaterialTheme.typography.titleMedium)
        Text("Save tasks, goals, small steps, companion progress, history, chat and preferences in a file you choose.")
        Text("This file contains your private data and is not encrypted. Keep it somewhere you trust.", style = MaterialTheme.typography.bodySmall)
        Button(enabled = !busy, onClick = { export.launch("KIN-backup-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) { Text("Save backup") }
        OutlinedButton(enabled = !busy && focus==null && rest==null, onClick = { restore.launch(arrayOf("application/json","application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("Restore backup") }
        if (focus!=null || rest!=null) Text("End the current break or focus session before restoring. You can save a backup anytime.", style = MaterialTheme.typography.bodySmall)
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
    if (busy) AlertDialog(onDismissRequest = {}, title = { Text("Working on your backup") }, text = {
        Column { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Please keep KIN open until this finishes.") }
    }, confirmButton = {})
    else draft?.let { snapshot -> AlertDialog(onDismissRequest = vm::discardBackup, title = { Text("Restore this backup?") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(snapshot.summary)
            Text("This replaces the tasks, progress, history and settings currently in KIN. Save a backup of your current data first if you need it.")
            Text("Saved timers return as stopped history. App-time check-ins stay off, and this phone keeps control of permissions and notification channels.")
        }
    }, confirmButton = { TextButton(onClick = vm::restoreBackup) { Text("Replace data and restore") } }, dismissButton = { TextButton(onClick = vm::discardBackup) { Text("Cancel") } }) }
}
