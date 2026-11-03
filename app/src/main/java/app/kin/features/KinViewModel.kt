package app.kin.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kin.KinApplication
import app.kin.data.*
import app.kin.domain.ConversationContext
import app.kin.services.Notifications
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
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }
    fun reportError(message: String) { _error.value = message }
    fun action(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _error.value = e.message ?: "Something went wrong. Please try again." }
    } }
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
