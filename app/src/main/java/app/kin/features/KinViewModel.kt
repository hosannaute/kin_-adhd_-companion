package app.kin.features

import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kin.KinApplication
import app.kin.data.*
import app.kin.domain.ConversationContext
import app.kin.services.Notifications
import app.kin.services.TimerAlerts
import app.kin.services.TimerAlertKind
import app.kin.domain.BreakEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class KinViewModel(val app: KinApplication) : ViewModel() {
    val repository = app.repository
    val tasks = repository.dao.tasks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val goals = repository.dao.goals().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val subtasks = repository.dao.subtasks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val companion = repository.dao.companion().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val focus = repository.dao.activeFocus().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val activeBreak = repository.dao.activeBreak().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val latestFocus = repository.dao.latestFocus().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val messages = repository.dao.messages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val achievements = repository.dao.achievements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sessionCount = repository.dao.sessionCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val preferences = app.preferences.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val permissionSetupReviewed = app.preferences.permissionSetupReviewed.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val focusSetupRequested = MutableStateFlow(false)
    val timerOptions = app.timerPreferences.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _breakFocusRequest = MutableStateFlow<BreakEntry?>(null)
    val breakFocusRequest = _breakFocusRequest.asStateFlow()
    fun chooseBreakFocus(value: BreakSession) { _breakFocusRequest.value = BreakEntry(value.id, value.revision) }
    fun clearBreakFocusRequest() { _breakFocusRequest.value = null }
    val sounds = app.sounds.flow.stateIn(viewModelScope,SharingStarted.Eagerly,TimerSoundChoices())
    val finishedFocus = combine(app.completions.flow,repository.dao.latestFocus(),repository.dao.activeFocus(),repository.dao.activeBreak()) { pending,latest,active,rest ->
        latest?.takeIf { it.id == pending && it.completed && active == null && rest == null }
    }.stateIn(viewModelScope,SharingStarted.Eagerly,null)
    private val _completedBreakRequest = MutableStateFlow<FocusSession?>(null)
    val completedBreakRequest = _completedBreakRequest.asStateFlow()
    fun clearCompletedBreakRequest() { _completedBreakRequest.value = null }
    fun dismissFinished(id: Long) = action { try { app.completions.dismiss(id) } finally { TimerAlerts.cancelFocus(app,id) } }
    fun requestCompletedBreak(id: Long) = action {
        val latest = repository.dao.latestFocus().first()
        if (app.completions.flow.first() != id || latest?.id != id || !latest.completed || repository.dao.currentFocus() != null || repository.dao.currentBreak() != null) return@action
        TimerAlerts.cancelFocus(app,id);app.completions.dismiss(id)
        _completedBreakRequest.value = latest
    }
    private val _soundBusy = MutableStateFlow(false)
    val soundBusy = _soundBusy.asStateFlow()
    private fun soundAction(work: suspend () -> Unit) {
        if (_soundBusy.value) return
        _soundBusy.value = true
        action { try { work() } finally { _soundBusy.value = false } }
    }
    fun importSound(kind: TimerAlertKind, uri: Uri) = soundAction { app.sounds.import(kind,uri) }
    fun builtInSound(kind: TimerAlertKind) = soundAction { app.sounds.builtIn(kind) }
    fun silentSound(kind: TimerAlertKind) = soundAction { app.sounds.silent(kind) }
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }
    fun reportError(message: String) { _error.value = message }
    fun action(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _error.value = e.message ?: "Something went wrong. Please try again." }
    } }
    private val _backupBusy = MutableStateFlow(false)
    val backupBusy = _backupBusy.asStateFlow()
    private val _backupDraft = MutableStateFlow<BackupSnapshot?>(null)
    val backupDraft = _backupDraft.asStateFlow()
    private val _backupNotice = MutableStateFlow<String?>(null)
    val backupNotice = _backupNotice.asStateFlow()
    fun discardBackup() { _backupDraft.value = null }
    private fun backupAction(work: suspend () -> Unit) {
        if (_backupBusy.value) return
        _backupBusy.value = true
        action { try { work() } finally { _backupBusy.value = false } }
    }
    fun exportBackup(uri: Uri) = backupAction {
        val bytes = app.backups.export()
        withContext(Dispatchers.IO) {
            (app.contentResolver.openOutputStream(uri,"wt") ?: throw IOException("Could not open the backup destination.")).use { it.write(bytes); it.flush() }
        }
        _backupNotice.value = "Backup saved. Keep this file somewhere private."
    }
    fun readBackup(uri: Uri) = backupAction {
        _backupDraft.value = null
        _backupDraft.value = withContext(Dispatchers.IO) {
            app.backups.read(app.contentResolver.openInputStream(uri) ?: throw IOException("Could not open this backup."))
        }
    }
    fun restoreBackup() {
        val draft = _backupDraft.value ?: return
        backupAction { _backupNotice.value = app.backups.restore(draft); _backupDraft.value = null }
    }
    fun context() = ConversationContext(tasks.value.firstOrNull { !it.resolved }?.title,
        goals.value.firstOrNull { !it.achieved }?.title, tasks.value.count { it.completed })
    fun send(text: String) = action { repository.send(text, context()) }
    fun savePreferences(value: UserPreferences) = action { app.preferences.save(value) }
    fun reminderStart(taskId: Long) = action {
        val task = repository.dao.task(taskId)
        Notifications.cancelTask(app, taskId)
        require(task != null && !task.resolved) { "This adventure is already finished or was deleted." }
        val current = repository.dao.currentFocus()
        if (current != null) repository.refreshFocus(current.id)
        val active = repository.dao.currentFocus()
        if (active == null) repository.startFocus(task.minutes, task)
        else if (active.taskId == taskId && !active.running) repository.resumeFocus(active.id)
        else if (active.taskId != taskId) error("A focus session is already open. Finish it before switching tasks.")
    }
}
