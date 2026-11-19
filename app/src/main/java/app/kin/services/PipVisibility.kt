package app.kin.services

import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Only the already-running overlay observes this; PiP never starts an overlay service. */
object PipVisibility {
    private val owners = mutableSetOf<String>()
    private val _visible = MutableStateFlow(false)
    val visible = _visible.asStateFlow()
    @MainThread fun acquire(owner: String) { owners += owner; _visible.value = owners.isNotEmpty() }
    @MainThread fun release(owner: String) { owners -= owner; _visible.value = owners.isNotEmpty() }
}
