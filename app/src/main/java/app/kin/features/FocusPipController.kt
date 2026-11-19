package app.kin.features

import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.*
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.*
import app.kin.KinApplication
import app.kin.R
import app.kin.data.FocusSession
import app.kin.services.Notifications
import app.kin.services.PipVisibility
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

enum class PipAvailability { AVAILABLE, DISABLED, UNSUPPORTED }
private enum class TimerAction { PAUSE, RESUME, STOP }
data class PipFocusState(val session: FocusSession? = null, val remainingMillis: Long = 0,
                         val availability: PipAvailability = PipAvailability.UNSUPPORTED, val message: String? = null)

/** Activity-scoped: PiP is paused but STARTED, so timer collection must not require RESUMED. */
class FocusPipController(private val activity: ComponentActivity, private val app: KinApplication,
                         savedState: Bundle?, private val onError: (String) -> Unit) : DefaultLifecycleObserver {
    private val owner = UUID.randomUUID().toString()
    private val actionName = "${app.packageName}.PIP.$owner"
    private val repository = app.repository
    private val _state = MutableStateFlow(PipFocusState())
    val state = _state.asStateFlow()
    private val _compact = MutableStateFlow(activity.isInPictureInPictureMode)
    val compact = _compact.asStateFlow()
    private var boundSessionId: Long? = savedState?.getLong("pipSessionId", 0)?.takeIf { it > 0 }
    private var entering = false
    private var watchdog: Job? = null
    private var actionKey: String? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != actionName || !activity.isInPictureInPictureMode) return
            val id = intent.getLongExtra("sessionId", 0)
            if (id != boundSessionId) return
            val action = intent.getStringExtra("timerAction")?.let { runCatching { TimerAction.valueOf(it) }.getOrNull() } ?: return
            activity.lifecycleScope.launch {
                try {
                    when (action) {
                        TimerAction.PAUSE -> repository.pauseFocus(id)
                        TimerAction.RESUME -> repository.resumeFocus(id)
                        TimerAction.STOP -> repository.stopFocus(id)
                    }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { report(e.message ?: "Couldn't update the session. Open KIN to try again.") }
            }
        }
    }

    init {
        refreshAvailability()
        ContextCompat.registerReceiver(activity, receiver, IntentFilter(actionName).apply {
            // PendingIntent identity includes URI data; an action-only filter rejects it.
            addDataScheme("kin-pip")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        activity.lifecycle.addObserver(this)
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    combine(repository.dao.activeFocus(), _compact) { active, compact -> active to compact }
                        .collectLatest { (active, compact) ->
                            val selected = if (compact && boundSessionId != null && active?.id != boundSessionId)
                                repository.dao.session(boundSessionId!!) else active
                            if (selected == null) {
                                _state.value = _state.value.copy(session = null, remainingMillis = 0)
                                updateActions()
                            } else while (currentCoroutineContext().isActive) {
                                val remaining = repository.remaining(selected)
                                _state.value = _state.value.copy(session = selected, remainingMillis = remaining)
                                updateActions()
                                if (selected.running && !selected.ended && remaining == 0L) {
                                    repository.refreshFocus(selected.id)
                                    break // Room emits the ended session; XP is guarded by its transaction.
                                }
                                if (selected.ended) break
                                delay(250)
                            }
                        }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { report(e.message ?: "Couldn't read your session. Reopen Focus to try again.") }
            }
        }
    }

    @Suppress("DEPRECATION") // checkOpNoThrow also supports the minimum Android 8 API.
    private fun availability(): PipAvailability {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return PipAvailability.UNSUPPORTED
        val mode = activity.getSystemService(AppOpsManager::class.java).checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE, app.applicationInfo.uid, app.packageName)
        return if (mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT) PipAvailability.AVAILABLE else PipAvailability.DISABLED
    }
    private fun refreshAvailability() { _state.value = _state.value.copy(availability = availability()) }
    private fun report(message: String) { _state.value = _state.value.copy(message = message); onError(message) }

    fun enter() {
        refreshAvailability()
        if (entering || activity.isInPictureInPictureMode) return
        val session = state.value.session
        if (session == null || session.ended) { report("Start a focus session before opening Picture-in-Picture."); return }
        if (state.value.availability != PipAvailability.AVAILABLE) { report("Picture-in-Picture is unavailable. Check its setting for KIN."); return }
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        boundSessionId = session.id
        entering = true
        _state.value = _state.value.copy(message = null)
        _compact.value = true
        PipVisibility.acquire(owner)
        // Render the compact presentation before Android snapshots the activity for its transition.
        activity.window.decorView.post {
            if (!entering || activity.isFinishing || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                failedEntry(); return@post
            }
            try {
                if (!activity.enterPictureInPictureMode(params())) { failedEntry(); return@post }
                watchdog = activity.lifecycleScope.launch {
                    delay(3_000)
                    if (entering && !activity.isInPictureInPictureMode) failedEntry()
                }
            } catch (_: RuntimeException) { failedEntry() }
        }
    }
    private fun failedEntry() {
        entering = false; boundSessionId = null; _compact.value = false
        PipVisibility.release(owner); refreshAvailability()
        report("Android couldn't open Picture-in-Picture. Your session is still available in Focus.")
    }

    private fun params(): PictureInPictureParams {
        val session = state.value.session
        val actions = if (session == null || session.ended) emptyList() else listOf(
            action(session.id, if (session.running) TimerAction.PAUSE else TimerAction.RESUME), action(session.id, TimerAction.STOP))
        val bounds = Rect()
        val hasBounds = activity.window.decorView.getGlobalVisibleRect(bounds)
        val width = minOf(bounds.width(), bounds.height() * 16 / 9)
        val height = width * 9 / 16
        val source = Rect(bounds.centerX() - width / 2, bounds.centerY() - height / 2,
            bounds.centerX() + width / 2, bounds.centerY() + height / 2)
        return PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).setActions(actions).apply {
            if (hasBounds && width > 0 && height > 0) setSourceRectHint(source)
            if (Build.VERSION.SDK_INT >= 31) { setAutoEnterEnabled(false); setSeamlessResizeEnabled(false) }
            if (Build.VERSION.SDK_INT >= 33) { setTitle("KIN focus"); setSubtitle(session?.taskTitle ?: "A little time for you") }
        }.build()
    }
    private fun action(id: Long, action: TimerAction): RemoteAction {
        val (icon, label) = when (action) {
            TimerAction.PAUSE -> R.drawable.ic_pip_pause to "Pause focus"
            TimerAction.RESUME -> R.drawable.ic_pip_play to "Resume focus"
            TimerAction.STOP -> R.drawable.ic_pip_stop to "Finish focus early"
        }
        val intent = Intent(actionName).setPackage(app.packageName)
            .setData("kin-pip://focus/$owner/$id/${action.name}".toUri())
            .putExtra("sessionId", id).putExtra("timerAction", action.name)
        val pending = PendingIntent.getBroadcast(activity, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return RemoteAction(Icon.createWithResource(app, icon), label, label, pending)
    }
    private fun updateActions(force: Boolean = false) {
        if (!activity.isInPictureInPictureMode) return
        val session = state.value.session
        val key = "${session?.id}:${session?.running}:${session?.ended}"
        if (force || key != actionKey) {
            try { activity.setPictureInPictureParams(params()); actionKey = key }
            catch (_: RuntimeException) { /* The system may have just removed the window or revoked PiP access. */ }
        }
    }

    fun modeChanged(active: Boolean) {
        entering = false; watchdog?.cancel(); _compact.value = active
        if (active) { PipVisibility.acquire(owner); updateActions(force = true) }
        else { boundSessionId = null; actionKey = null; PipVisibility.release(owner) }
    }
    fun save(outState: Bundle) { boundSessionId?.let { outState.putLong("pipSessionId", it) } }
    fun openSettings() {
        try { activity.startActivity(Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", "package:${app.packageName}".toUri())) }
        catch (_: ActivityNotFoundException) {
            try { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${app.packageName}".toUri())) }
            catch (_: ActivityNotFoundException) { report("Open Android's app settings for KIN to allow Picture-in-Picture.") }
        }
    }
    override fun onStart(owner: LifecycleOwner) {
        if (activity.isInPictureInPictureMode) { _compact.value = true; PipVisibility.acquire(this.owner) }
    }
    override fun onResume(owner: LifecycleOwner) {
        refreshAvailability()
        if (!entering && !activity.isInPictureInPictureMode) modeChanged(false)
    }
    override fun onStop(owner: LifecycleOwner) { PipVisibility.release(this.owner) }
    override fun onDestroy(owner: LifecycleOwner) {
        watchdog?.cancel(); PipVisibility.release(this.owner)
        activity.unregisterReceiver(receiver)
    }
}
