package app.kin.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import app.kin.KinApplication
import app.kin.MainActivity
import app.kin.R
import app.kin.data.*
import app.kin.domain.*
import app.kin.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalTime

data class FloatingStatus(val running: Boolean = false, val visible: Boolean = false,
                          val message: String = "Floating companion is off")

class FloatingCompanionService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    private val savedState = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as KinApplication
    private lateinit var windows: WindowManager
    private var root: FrameLayout? = null
    private var attached = false
    private var activated = false
    private var pausedUntil = 0L
    private var nextUsagePoll = 0L
    private var options = CompanionModeOptions()
    private var prefs by mutableStateOf(UserPreferences())
    private var stage by mutableStateOf(Evolution.EGG)
    private var expanded by mutableStateOf(false)
    private var bubble by mutableStateOf("A little company, at your pace.")
    private var tracker = UsageSessionTracker(30 * 60_000L)
    private lateinit var poller: UsagePoller
    // Window offsets are physical screen coordinates; START would invert dragging in RTL.
    @android.annotation.SuppressLint("RtlHardcoded")
    private val layout = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.LEFT }

    override fun onCreate() {
        super.onCreate()
        savedState.performAttach(); savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windows = getSystemService(WindowManager::class.java)
        poller = UsagePoller(this, tracker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> { stopSelf(); return START_NOT_STICKY }
            PAUSE -> {
                if (!activated) { stopSelf(); return START_NOT_STICKY }
                pausedUntil = SystemClock.elapsedRealtime() + 15 * 60_000L
                poller.reset(); expanded = false; hide()
                status.value = FloatingStatus(true, false, "Paused for 15 minutes")
                return START_NOT_STICKY
            }
        }
        if (!Settings.canDrawOverlays(this)) {
            status.value = FloatingStatus(message = "Overlay permission is off. Core features still work.")
            stopSelf(); return START_NOT_STICKY
        }
        if (!activated) {
            try { startForeground(NOTIFICATION_ID, notification()) }
            catch (_: RuntimeException) {
                status.value = FloatingStatus(message = "Android could not start floating mode. Open KIN and try again.")
                stopSelf(); return START_NOT_STICKY
            }
            activated = true
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            scope.launch {
                try {
                    combine(app.companionMode.flow, app.preferences.flow, PipVisibility.visible) { mode, preference, _ -> mode to preference }
                        .collect { (mode, preference) ->
                            val changed = mode.monitorUsage != options.monitorUsage ||
                                mode.selectedApps != options.selectedApps || mode.thresholdMinutes != options.thresholdMinutes
                            options = mode; prefs = preference
                            if (changed) { tracker = UsageSessionTracker(mode.thresholdMinutes * 60_000L); poller = UsagePoller(this@FloatingCompanionService, tracker); nextUsagePoll = 0 }
                            if (root == null) { layout.x = mode.x; layout.y = mode.y; createView() }
                            tick()
                        }
                } catch (_: java.io.IOException) {
                    status.value = FloatingStatus(message = "Could not read preferences. Floating mode stopped.")
                    stopSelf()
                }
            }
            scope.launch { app.database.dao().companion().collect { stage = Evolution.at(it?.xp ?: 0) } }
            scope.launch { while (isActive) { delay(15_000); tick() } }
        } else { pausedUntil = 0; tick() }
        return START_NOT_STICKY
    }

    private fun createView() {
        root = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(this@FloatingCompanionService)
            setViewTreeSavedStateRegistryOwner(this@FloatingCompanionService)
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> constrainPosition() }
            addView(ComposeView(this@FloatingCompanionService).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent {
                    KinTheme(prefs.darkMode) {
                        FloatingCompanionPanel(prefs.name, stage, prefs.reducedMotion, expanded, bubble,
                            onToggle = { expanded = !expanded; root?.post { constrainPosition() } },
                            onDrag = { dx, dy -> layout.x += dx; layout.y += dy; constrainPosition() },
                            onDragEnd = { scope.launch { app.companionMode.position(layout.x, layout.y) } },
                            onBreak = { acknowledge(10, "Rest is part of the journey.") },
                            onScrolling = { acknowledge(5, "No judgment. I'm here when you're ready.") },
                            onGoals = { openGoals() }, onStop = { stopSelf() })
                    }
                }
            })
        }
    }

    private fun tick() {
        if (!activated) return
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val paused = SystemClock.elapsedRealtime() < pausedUntil
        val quiet = ReminderPolicy.isQuiet(LocalTime.now().hour, prefs.quietStart, prefs.quietEnd)
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive
        val pip = PipVisibility.visible.value
        if (paused || quiet || locked || pip) {
            poller.reset(); nextUsagePoll = 0; expanded = false; hide()
            status.value = FloatingStatus(true, false, when { paused -> "Paused for 15 minutes"; quiet -> "Quiet hours · companion resting";
                pip -> "Picture-in-Picture is visible · floating companion resting"; else -> "Resting while your screen is locked or off" })
            return
        }
        if (!attached) show()
        if (!attached) return
        val granted = UsageAccess.granted(this)
        status.value = FloatingStatus(true, true, when {
            !options.monitorUsage -> "Floating companion is on · usage reminders off"
            !granted -> "Floating companion is on · usage access is off"
            options.selectedApps.isEmpty() -> "Floating companion is on · choose apps for reminders"
            else -> "Floating companion is on · local app-time reminders active"
        })
        if (options.monitorUsage && granted && options.selectedApps.isNotEmpty()) {
            if (SystemClock.elapsedRealtime() < nextUsagePoll) return
            nextUsagePoll = SystemClock.elapsedRealtime() + 15_000L
            try {
                if (poller.poll(options.selectedApps) != null) {
                    bubble = "You've spent about ${options.thresholdMinutes} minutes here. Still taking a break, or ready for a small step?"
                    expanded = true
                    root?.post { constrainPosition() }
                }
            } catch (_: RuntimeException) {
                poller.reset()
                status.value = FloatingStatus(true, true, "Floating companion is on · app-time monitoring unavailable")
            }
        } else { poller.reset(); nextUsagePoll = 0 }
    }

    private fun acknowledge(minutes: Int, message: String) {
        tracker.takeBreak(SystemClock.elapsedRealtime(), minutes)
        bubble = message; expanded = false
        root?.post { constrainPosition() }
    }
    private fun openGoals() {
        acknowledge(10, "One tiny step is enough.")
        try {
            startActivity(Intent(this, MainActivity::class.java).putExtra("route", "tasks")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (_: ActivityNotFoundException) { bubble = "Open KIN to return to your goals." }
    }
    private fun show() {
        val view = root ?: return
        try {
            windows.addView(view, layout); attached = true
            view.post { constrainPosition() }
        } catch (_: RuntimeException) { stopSelf() }
    }
    private fun hide() {
        if (attached) {
            try { root?.let { windows.removeView(it) } } catch (_: IllegalArgumentException) { /* Android already removed it. */ }
            attached = false
        }
    }
    private fun constrainPosition() {
        val view = root ?: return
        val metrics = resources.displayMetrics
        val margin = (32 * metrics.density).toInt()
        layout.x = layout.x.coerceIn(0, (metrics.widthPixels - view.width).coerceAtLeast(0))
        layout.y = layout.y.coerceIn(margin, (metrics.heightPixels - view.height - margin).coerceAtLeast(margin))
        if (attached) try { windows.updateViewLayout(view, layout) } catch (_: RuntimeException) { stopSelf() }
    }

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(
            "kin_floating", "Floating companion controls", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) })
        fun action(action: String, code: Int) = PendingIntent.getService(this, code,
            Intent(this, FloatingCompanionService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 50, Intent(this, MainActivity::class.java)
            .setAction("app.kin.OPEN_FLOATING_CONTROLS").putExtra("route", "more"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, "kin_floating").setSmallIcon(R.drawable.ic_kin)
            .setContentTitle("KIN is keeping you company").setContentText("Tap to open KIN. Pause or stop anytime.")
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .addAction(0, "Pause 15 min", action(PAUSE, 51)).addAction(0, "Stop", action(STOP, 52)).build()
    }

    override fun onDestroy() {
        hide(); scope.cancel()
        if (activated) { registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        root = null
        if (status.value.running) status.value = FloatingStatus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        root?.post { constrainPosition() }
    }
    override fun onBind(intent: Intent?) = null

    companion object {
        private const val NOTIFICATION_ID = 21002
        private const val STOP = "app.kin.FLOAT_STOP"
        private const val PAUSE = "app.kin.FLOAT_PAUSE"
        val status = MutableStateFlow(FloatingStatus())
        fun start(context: Context): Boolean {
            if (!Settings.canDrawOverlays(context)) {
                status.value = FloatingStatus(message = "Allow display over other apps before starting floating mode.")
                return false
            }
            return try { ContextCompat.startForegroundService(context, Intent(context, FloatingCompanionService::class.java)); true }
            catch (_: RuntimeException) {
                status.value = FloatingStatus(message = "Android could not start floating mode. Keep KIN open and try again."); false
            }
        }
        fun stop(context: Context) {
            val service = Intent(context, FloatingCompanionService::class.java)
            context.stopService(service)
        }
    }
}
