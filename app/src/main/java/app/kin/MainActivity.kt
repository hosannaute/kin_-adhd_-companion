package app.kin

import android.os.Bundle
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.kin.features.KinViewModel
import app.kin.ui.KinApp
import app.kin.domain.TaskEntry
import app.kin.domain.TaskEntryAction
import app.kin.features.FocusPipController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kin.domain.BreakEntry
import app.kin.services.KinVisibility
import app.kin.services.TimerAlerts

open class MainActivity : ComponentActivity() {
    protected open val acceptsReminderStart: Boolean = false
    private var routeRequest by mutableStateOf<Pair<String, Int>?>(null)
    private var taskEntry by mutableStateOf<TaskEntry?>(null)
    private var breakEntry by mutableStateOf<BreakEntry?>(null)
    private var completedFocusId by mutableStateOf<Long?>(null)
    private lateinit var pip: FocusPipController
    private fun acceptRoute(intent: Intent) {
        val route = intent.getStringExtra("route")?.takeIf { it in listOf("tasks", "focus", "more") } ?: return
        completedFocusId = intent.getLongExtra("completedFocusId",0).takeIf { it > 0 && acceptsReminderStart && intent.getBooleanExtra("chooseBreakAfterFocus",false) }
        completedFocusId?.let { TimerAlerts.cancelFocus(this,it) }
        val taskId = intent.getLongExtra("taskId", 0)
        val breakId = intent.getLongExtra("breakId", 0)
        val breakRevision = intent.getIntExtra("breakRevision", -1)
        breakEntry = if (intent.getBooleanExtra("chooseBreakFocus", false) && breakId > 0 && breakRevision >= 0)
            BreakEntry(breakId, breakRevision) else null
        val action = intent.getStringExtra("taskAction")?.let { runCatching { TaskEntryAction.valueOf(it) }.getOrNull() }
        taskEntry = if (taskId > 0 && action != null) TaskEntry(taskId,
            if (action == TaskEntryAction.START && !acceptsReminderStart) TaskEntryAction.OPTIONS else action) else null
        if (breakEntry != null) taskEntry = null
        val target = if (breakEntry != null) "focus" else when (taskEntry?.action) { TaskEntryAction.START -> "focus"; TaskEntryAction.OPTIONS -> "tasks"; else -> route }
        routeRequest = target to ((routeRequest?.second ?: 0) + 1)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) acceptRoute(intent) else {
            savedInstanceState.getString("requestedRoute")?.let { routeRequest = it to savedInstanceState.getInt("requestId") }
            completedFocusId = savedInstanceState.getLong("completedFocusId").takeIf { it > 0 }
            val id = savedInstanceState.getLong("taskId")
            val action = savedInstanceState.getString("taskAction")?.let { runCatching { TaskEntryAction.valueOf(it) }.getOrNull() }
            if (id > 0 && action != null) taskEntry = TaskEntry(id, action)
            val breakId = savedInstanceState.getLong("breakEntryId")
            if (breakId > 0) breakEntry = BreakEntry(breakId, savedInstanceState.getInt("breakEntryRevision"))
        }
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = KinViewModel(application as KinApplication) as T
        })[KinViewModel::class.java]
        pip = FocusPipController(this, application as KinApplication, savedInstanceState, vm::reportError)
        KinVisibility.bind(this, application as KinApplication)
        setContent {
            val compact = pip.compact.collectAsStateWithLifecycle()
            val focusState = pip.state.collectAsStateWithLifecycle()
            KinApp(vm, routeRequest?.first, routeRequest?.second ?: 0, taskEntry,
                compact, focusState, pip::enter, pip::openSettings, breakEntry, completedFocusId)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptRoute(intent)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        routeRequest?.let { outState.putString("requestedRoute", it.first); outState.putInt("requestId", it.second) }
        completedFocusId?.let { outState.putLong("completedFocusId",it) }
        taskEntry?.let { outState.putLong("taskId", it.taskId); outState.putString("taskAction", it.action.name) }
        if (::pip.isInitialized) pip.save(outState)
        breakEntry?.let { outState.putLong("breakEntryId", it.id); outState.putInt("breakEntryRevision", it.revision) }
        super.onSaveInstanceState(outState)
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (::pip.isInitialized) pip.modeChanged(isInPictureInPictureMode)
    }
}
