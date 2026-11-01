package app.kin.services

import androidx.lifecycle.*
import androidx.activity.ComponentActivity
import app.kin.KinApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Main-thread owners prevent a second companion window above KIN's own dialogs. */
object KinVisibility {
    private val owners = mutableSetOf<ComponentActivity>()
    private val _resumed = MutableStateFlow(false)
    val resumed = _resumed.asStateFlow()
    fun bind(activity: ComponentActivity, app: KinApplication) {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                owners += activity; _resumed.value = owners.isNotEmpty()
                activity.lifecycleScope.launch {
                    val old = app.database.dao().currentBreak() ?: return@launch
                    val value = app.breaks.refresh(old.id)
                    if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        value?.phase in listOf("RUNNING", "PAUSED", "DUE") && !BreakTimerService.status.value.running) BreakTimerService.start(activity)
                }
            }
            override fun onPause(owner: LifecycleOwner) { owners -= activity; _resumed.value = owners.isNotEmpty() }
            override fun onDestroy(owner: LifecycleOwner) { owners -= activity; _resumed.value = owners.isNotEmpty() }
        })
    }
}
object BreakPromptVisibility {
    private val owners = mutableSetOf<String>()
    private val _visible = MutableStateFlow(false)
    val visible = _visible.asStateFlow()
    fun acquire(owner: String) { owners += owner; _visible.value = owners.isNotEmpty() }
    fun release(owner: String) { owners -= owner; _visible.value = owners.isNotEmpty() }
}
