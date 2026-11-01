package app.kin.services

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import app.kin.KinApplication
import app.kin.data.*
import app.kin.domain.*
import app.kin.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime

data class BreakTimerStatus(val running: Boolean = false, val visible: Boolean = false, val message: String = "Break timer is off")

/** Explicitly user-started foreground countdown; WorkManager is the persisted fallback. */
class BreakTimerService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    private val savedState = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    private val app get() = application as KinApplication
    private lateinit var windows: WindowManager
    private var root: FrameLayout? = null
    private var attached = false
    private var activated = false
    private var current by mutableStateOf<BreakSession?>(null)
    private var pref by mutableStateOf<UserPreferences?>(null)
    private var stage by mutableStateOf(Evolution.EGG)
    private var acknowledgment by mutableStateOf(false)
    private var ackUntil = 0L
    private var previous: BreakSession? = null
    private var notificationKey = ""

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach(); savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windows = getSystemService(WindowManager::class.java)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!activated) {
            try { startForeground(BreakNotifications.FOREGROUND_ID, BreakNotifications.foreground(this, null)) }
            catch (_: RuntimeException) {
                status.value = BreakTimerStatus(message = "Background countdown unavailable. The saved timer and notification fallback remain available.")
                stopSelf(); return START_NOT_STICKY
            }
            activated = true
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START); registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            scope.launch { app.preferences.flow.collect { pref = it; tick() } }
            scope.launch { app.database.dao().companion().collect { stage = Evolution.at(it?.xp ?: 0) } }
            scope.launch { while (isActive) { tick(); delay(500) } }
        }
        return START_STICKY
    }
    private suspend fun tick() = lock.withLock {
        if (!activated) return@withLock
        try {
            val found = app.database.dao().currentBreak()
            val value = found?.let { app.breaks.refresh(it.id) }
            if (value == null || value.phase !in listOf("RUNNING", "PAUSED", "DUE")) { stopSelf(); return@withLock }
            val old = previous
            if (old?.id == value.id && value.snoozeCount > old.snoozeCount && value.phase == "RUNNING") ackUntil = SystemClock.elapsedRealtime() + 4_000
            previous = value; current = value
            val left = app.breaks.remaining(value)
            val key = "${value.id}:${value.revision}:${value.phase}:${if (value.phase == "RUNNING") (left + 999) / 1000 else 0}"
            if (key != notificationKey) {
                try { getSystemService(NotificationManager::class.java).notify(BreakNotifications.FOREGROUND_ID, BreakNotifications.foreground(this, value, left)) }
                catch (_: SecurityException) { /* Muting notifications must not stop the countdown. */ }
                notificationKey = key
            }
            acknowledgment = value.phase == "RUNNING" && SystemClock.elapsedRealtime() < ackUntil
            val preference = pref
            val quiet = preference == null || ReminderPolicy.isQuiet(LocalTime.now().hour, preference.quietStart, preference.quietEnd)
            val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked || !getSystemService(PowerManager::class.java).isInteractive
            val eligible = (value.phase == "DUE" || acknowledgment) && Settings.canDrawOverlays(this) &&
                !quiet && !locked && !PipVisibility.visible.value && !KinVisibility.resumed.value
            if (eligible) show() else hide()
            status.value = BreakTimerStatus(true, attached, when {
                attached && acknowledgment -> "Snoozed · remember your goal"
                attached -> "Time to return to your task"
                value.phase == "PAUSED" -> "Break paused"
                value.phase == "RUNNING" -> "Break countdown is running"
                quiet -> "Break ended · quiet hours"
                locked -> "Break ended · waiting for screen unlock"
                else -> "Break ended · open KIN or its reminder to choose focus time"
            })
            // Keep the notification available even if Android hides overlays on a sensitive screen.
            if (value.phase == "DUE") BreakNotifications.due(app, value)
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {
            hide(); status.value = BreakTimerStatus(message = "Couldn't refresh the break timer. Reopen KIN to recover it."); stopSelf()
        }
    }
    @Suppress("DEPRECATION")
    private fun screen(): DisplayMetrics = DisplayMetrics().also { windows.defaultDisplay.getRealMetrics(it) }
    private fun show() {
        if (attached) return
        val preference = pref ?: return
        val metrics = screen()
        val width = minOf(metrics.widthPixels - (32 * metrics.density).toInt(), (340 * metrics.density).toInt()).coerceAtLeast(1)
        val maxHeight = ((metrics.heightPixels / metrics.density) - 72).coerceAtLeast(180f)
        if (root == null) root = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(this@BreakTimerService)
            setViewTreeSavedStateRegistryOwner(this@BreakTimerService)
            addView(ComposeView(this@BreakTimerService).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent {
                    val value = current
                    val settings = pref ?: preference
                    KinTheme(settings.darkMode) {
                        if (value != null) BreakPromptPanel(value, settings, stage, acknowledgment, maxHeight,
                            onStart = { openFocusPicker(value) },
                            onSnooze = { scope.launch { if (app.breaks.snooze(value.id, value.revision)) {
                                app.database.dao().breakSession(value.id)?.let { BreakNotifications.snoozed(app, it) }; tick()
                            } } },
                            onStop = { scope.launch { app.breaks.stop(value.id, value.revision); tick() } })
                    }
                }
            })
        }
        val params = WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        try { windows.addView(root, params); attached = true; BreakPromptVisibility.acquire("break-service") }
        catch (_: RuntimeException) { hide() /* The notification remains a usable fallback. */ }
    }
    private fun openFocusPicker(value: BreakSession) {
        try {
            // This follows a direct tap on KIN's visible, permission-granted overlay.
            startActivity(BreakNotifications.intent(this, value, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            hide()
        } catch (_: RuntimeException) {
            current = value
            status.value = BreakTimerStatus(true, attached, "Open the KIN notification to choose your focus time.")
        }
    }
    private fun hide() {
        if (attached) try { root?.let { windows.removeView(it) } } catch (_: IllegalArgumentException) { }
        attached = false; BreakPromptVisibility.release("break-service")
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); hide(); root = null }
    override fun onDestroy() {
        hide(); scope.cancel()
        if (activated) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        root = null
        if (status.value.running) {
            status.value = BreakTimerStatus()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
    companion object {
        val status = MutableStateFlow(BreakTimerStatus())
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, BreakTimerService::class.java)); true
        } catch (_: RuntimeException) {
            status.value = BreakTimerStatus(message = "Android blocked the background countdown. Reopen KIN; saved timers still recover."); false
        }
    }
}
