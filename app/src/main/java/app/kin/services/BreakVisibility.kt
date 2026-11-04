package app.kin.services

import androidx.lifecycle.*
import androidx.activity.ComponentActivity
import app.kin.KinApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
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

            }
            override fun onPause(owner: LifecycleOwner) { owners -= activity; _resumed.value = owners.isNotEmpty() }
            override fun onDestroy(owner: LifecycleOwner) { owners -= activity; _resumed.value = owners.isNotEmpty() }
        })
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var last: String? = null
                var attempts = 0
                val timer = combine(app.database.dao().activeBreak(), app.database.dao().activeFocus()) { rest, focus ->
                    if (rest?.phase in listOf("RUNNING", "PAUSED", "DUE")) "break:${rest!!.id}" else focus?.let { "focus:${it.id}" }
                }.distinctUntilChanged()
                combine(timer, BreakTimerService.status.map { it.running }.distinctUntilChanged()) { active, running -> active to running }
                    .collectLatest { (active,running) ->
                        if (active != last) { last = active; attempts = 0 }
                        if (active != null && !running && attempts < 3) {
                            if (attempts > 0) delay(500)
                            attempts++
                            if (!BreakTimerService.status.value.running) BreakTimerService.start(activity)
                        }
                    }
            }
        }

    }
}
object BreakPromptVisibility {
    private val owners = mutableSetOf<String>()
    private val _visible = MutableStateFlow(false)
    val visible = _visible.asStateFlow()
    fun acquire(owner: String) { owners += owner; _visible.value = owners.isNotEmpty() }
    fun release(owner: String) { owners -= owner; _visible.value = owners.isNotEmpty() }
}
