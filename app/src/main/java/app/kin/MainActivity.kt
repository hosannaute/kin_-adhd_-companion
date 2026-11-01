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

open class MainActivity : ComponentActivity() {
    protected open val acceptsReminderStart: Boolean = false
    private var routeRequest by mutableStateOf<Pair<String, Int>?>(null)
    private var taskEntry by mutableStateOf<TaskEntry?>(null)
    private lateinit var pip: FocusPipController
    private fun acceptRoute(intent: Intent) {
        val route = intent.getStringExtra("route")?.takeIf { it in listOf("tasks", "focus", "more") } ?: return
        val taskId = intent.getLongExtra("taskId", 0)
        val action = intent.getStringExtra("taskAction")?.let { runCatching { TaskEntryAction.valueOf(it) }.getOrNull() }
        taskEntry = if (taskId > 0 && action != null) TaskEntry(taskId,
            if (action == TaskEntryAction.START && !acceptsReminderStart) TaskEntryAction.OPTIONS else action) else null
        val target = when (taskEntry?.action) { TaskEntryAction.START -> "focus"; TaskEntryAction.OPTIONS -> "tasks"; else -> route }
        routeRequest = target to ((routeRequest?.second ?: 0) + 1)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) acceptRoute(intent) else {
            savedInstanceState.getString("requestedRoute")?.let { routeRequest = it to savedInstanceState.getInt("requestId") }
            val id = savedInstanceState.getLong("taskId")
            val action = savedInstanceState.getString("taskAction")?.let { runCatching { TaskEntryAction.valueOf(it) }.getOrNull() }
            if (id > 0 && action != null) taskEntry = TaskEntry(id, action)
        }
        val vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = KinViewModel(application as KinApplication) as T
        })[KinViewModel::class.java]
        pip = FocusPipController(this, application as KinApplication, savedInstanceState, vm::reportError)
        setContent {
            val compact = pip.compact.collectAsStateWithLifecycle()
            val focusState = pip.state.collectAsStateWithLifecycle()
            KinApp(vm, routeRequest?.first, routeRequest?.second ?: 0, taskEntry,
                compact, focusState, pip::enter, pip::openSettings)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptRoute(intent)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        routeRequest?.let { outState.putString("requestedRoute", it.first); outState.putInt("requestId", it.second) }
        taskEntry?.let { outState.putLong("taskId", it.taskId); outState.putString("taskAction", it.action.name) }
        if (::pip.isInitialized) pip.save(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (::pip.isInitialized) pip.modeChanged(isInPictureInPictureMode)
    }
}
