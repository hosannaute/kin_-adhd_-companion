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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BreakTimerStatus(val running: Boolean = false, val visible: Boolean = false, val message: String = "Timer icon is off", val expanded: Boolean = false)

/** One user-started foreground owner for both persisted clocks. View attachment never owns time. */
class BreakTimerService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    private val saved = SavedStateRegistryController.create(this)
    override val savedStateRegistry get() = saved.savedStateRegistry
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    private val app get() = application as KinApplication
    private lateinit var windows: WindowManager
    private var root: FrameLayout? = null
    private var attached = false
    private var activated = false
    private var lastStartId = 0
    private var current by mutableStateOf<BreakSession?>(null)
    private var focus by mutableStateOf<FocusSession?>(null)
    private var pref by mutableStateOf<UserPreferences?>(null)
    private var stage by mutableStateOf(Evolution.EGG)
    private var expanded by mutableStateOf(false)
    private var acknowledgment by mutableStateOf(false)
    private var remaining by mutableLongStateOf(0)
    private var options = TimerOptions()
    private var positionLoaded = false
    private var dragging = false
    private var ackUntil = 0L
    private var seenDue = ""
    private var previous: BreakSession? = null
    private var modeKey = ""
    private var notificationKey = ""
    private var layoutKey = ""
    private var params: WindowManager.LayoutParams? = null
    private var retryAfter = 0L
    private var windowError: String? = null

    override fun onCreate() {
        super.onCreate(); saved.performAttach(); saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windows = getSystemService(WindowManager::class.java)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (!activated) {
            try { startForeground(BreakNotifications.FOREGROUND_ID, BreakNotifications.foreground(this, null)) }
            catch (_: RuntimeException) { status.value = BreakTimerStatus(message = "Background timer unavailable. Reopen KIN to recover the saved timer."); stopSelf(); return START_NOT_STICKY }
            activated = true
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START); registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            scope.launch { options = app.timerPreferences.flow.first(); positionLoaded = true; tick() }
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
            val rest = found?.let { app.breaks.refresh(it.id) }?.takeIf { it.phase in listOf("RUNNING", "PAUSED", "DUE") }
            var work = if (rest == null) app.database.dao().currentFocus() else null
            if (work != null) {
                app.repository.refreshFocus(work.id)
                work = app.database.dao().currentFocus()
            }
            if (rest == null && work == null) { stopSelf(lastStartId); return@withLock }
            val nextMode = if (rest != null) "break:${rest.id}" else "focus:${work!!.id}"
            if (nextMode != modeKey) { expanded = false; modeKey = nextMode; ackUntil = 0; layoutKey = "" }
            val old = previous
            if (rest != null && old?.id == rest.id && rest.snoozeCount > old.snoozeCount && rest.phase == "RUNNING") {
                ackUntil = SystemClock.elapsedRealtime() + 4_000; expanded = !KinVisibility.resumed.value
            }
            previous = rest; current = rest; focus = work
            if (rest?.phase == "DUE") {
                val key = "${rest.id}:${rest.revision}"
                if (key != seenDue) { seenDue = key; expanded = true }
            }
            val ack = rest?.phase == "RUNNING" && SystemClock.elapsedRealtime() < ackUntil
            if (acknowledgment && !ack) expanded = false
            acknowledgment = ack
            remaining = if (rest != null) app.breaks.remaining(rest) else app.repository.remaining(work!!)
            val key = "$nextMode:${rest?.revision}:${rest?.phase}:${work?.running}:${(remaining + 999) / 1000}:${pref?.snoozeMinutes}:${pref?.snoozeLimit}"
            if (key != notificationKey) {
                try { getSystemService(NotificationManager::class.java).notify(BreakNotifications.FOREGROUND_ID,
                    if (rest != null) BreakNotifications.foreground(this, rest, remaining, pref ?: UserPreferences()) else FocusTimerNotifications.foreground(this, work!!, remaining)) }
                catch (_: SecurityException) { }
                notificationKey = key
            }
            val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked || !getSystemService(PowerManager::class.java).isInteractive
            val activeBreak = rest?.phase in listOf("RUNNING", "PAUSED")
            // A running break stays visible in KIN as well as in other apps.
            // At expiry KIN owns its due dialog; outside KIN this service owns the large prompt.
            val presentationAllowed = activeBreak || (!KinVisibility.resumed.value && !PipVisibility.visible.value)
            val eligible = pref != null && positionLoaded && Settings.canDrawOverlays(this) && !locked &&
                !OverlayPreviewService.visible.value && presentationAllowed
            if (eligible) show() else hide()
            status.value = BreakTimerStatus(true, attached, when {
                attached && acknowledgment -> "Snoozed · remember your goal"
                attached && expanded -> "Timer controls are open"
                attached -> "Timer icon is at the screen edge · tap for controls"
                !Settings.canDrawOverlays(this) -> "Window permission is off · enable it to show the timer icon"
                windowError != null -> windowError!!
                locked -> "Timer saved · window returns after unlock"
                else -> "Timer is active"
            }, expanded)
            if (rest?.phase == "DUE") BreakNotifications.due(app, rest)
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {
            // A transient read/window failure must not turn off the foreground clock owner.
            hide(); status.value = BreakTimerStatus(true, false, "Recovering the saved timer. You can reopen KIN anytime.")
        }
    }
    @Suppress("DEPRECATION")
    private fun screen() = DisplayMetrics().also { windows.defaultDisplay.getRealMetrics(it) }
    private fun collapse() { expanded = false; acknowledgment = false; ackUntil = 0; layoutKey = ""; scope.launch { tick() } }
    private fun compactSize(metrics: DisplayMetrics): Pair<Int,Int> {
        val scale = resources.configuration.fontScale.coerceAtLeast(1f)
        val width = ((112 + 56 * (scale - 1)) * metrics.density).toInt()
            .coerceAtMost((metrics.widthPixels - 16 * metrics.density).toInt()).coerceAtLeast(1)
        return width to ((64 + 32 * (scale - 1)) * metrics.density).toInt()
    }
    private fun show() {
        if (SystemClock.elapsedRealtime() < retryAfter) return
        val settings = pref ?: return
        val metrics = screen()
        val (compactWidth, icon) = compactSize(metrics)
        val width = if (expanded) minOf(metrics.widthPixels - (16 * metrics.density).toInt(), (320 * metrics.density).toInt()) else compactWidth
        val margin = (32 * metrics.density).toInt()
        val maxHeight = (metrics.heightPixels / metrics.density - 72).coerceAtLeast(180f)
        if (root == null) root = object : FrameLayout(this) {
            // ViewGroup may discard ACTION_OUTSIDE before an OnTouchListener sees it.
            // Observe it at dispatch; actual clicks remain with Compose and the other app.
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) { if (expanded) collapse(); return false }
                return super.dispatchTouchEvent(event)
            }
        }.apply {
            setViewTreeLifecycleOwner(this@BreakTimerService); setViewTreeSavedStateRegistryOwner(this@BreakTimerService)
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> constrain() }
            addView(ComposeView(this@BreakTimerService).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent { KinTheme(pref?.darkMode ?: settings.darkMode) {
                    val rest = current; val work = focus; val preference = pref ?: settings
                    if (!expanded) TimerBubble(work != null, rest?.phase == "PAUSED" || work?.running == false,
                        rest?.phase == "DUE", remaining, stage,
                        onTap = { expanded = true; layoutKey = ""; scope.launch { tick() } }, onDrag = ::drag, onDrop = ::dock,
                        widthDp = compactWidth / metrics.density, heightDp = icon / metrics.density)
                    else if (rest != null && (rest.phase == "DUE" || acknowledgment)) {
                        androidx.compose.foundation.layout.Column {
                            androidx.compose.material3.TextButton(onClick = { collapse() }) { androidx.compose.material3.Text("Collapse") }
                            BreakPromptPanel(rest, preference, stage, acknowledgment, maxHeight - 48,
                                onStart = { openBreak(rest) }, onSnooze = { scope.launch {
                                    if (app.breaks.snooze(rest.id, rest.revision)) app.database.dao().breakSession(rest.id)?.let { BreakNotifications.snoozed(app, it) }; tick()
                                } }, onStop = { scope.launch { app.breaks.stop(rest.id, rest.revision); tick() } })
                        }
                    } else if (rest != null || work != null) TimerControls(work != null,
                        rest?.phase == "PAUSED" || work?.running == false, rest?.taskTitle ?: work!!.taskTitle, remaining, maxHeight,
                        onCollapse = { collapse() }, onPause = { scope.launch {
                            if (rest != null) { if (rest.phase == "PAUSED") app.breaks.resume(rest.id, rest.revision) else app.breaks.pause(rest.id, rest.revision) }
                            else if (work != null) { if (work.running) app.repository.pauseFocus(work.id) else app.repository.resumeFocus(work.id) }; tick()
                        } }, onStart = { rest?.let { openBreak(it) } }, onStop = { scope.launch {
                            if (rest != null) app.breaks.stop(rest.id, rest.revision) else if (work != null) app.repository.stopFocus(work.id); tick()
                        } })
                } }
            })
        }
        if (attached && root?.isAttachedToWindow != true) {
            try { root?.let { windows.removeViewImmediate(it) } } catch (_: RuntimeException) { }
            attached = false; layoutKey = ""; BreakPromptVisibility.release("break-service") }
        val height = if (expanded) WindowManager.LayoutParams.WRAP_CONTENT else icon
        val key = "$expanded:${metrics.widthPixels}:${metrics.heightPixels}:${options.dockRight}:${options.verticalFraction}"
        @Suppress("RtlHardcoded")
        val layout = WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = BubblePosition.x(options.dockRight, metrics.widthPixels, width)
                val expectedHeight = if (expanded) root?.height?.takeIf { it > icon } ?: (360 * metrics.density).toInt() else icon
                y = BubblePosition.y(options.verticalFraction, metrics.heightPixels, expectedHeight, margin)
            }
        try {
            if (!attached) { windows.addView(root, layout); params = layout; attached = true; layoutKey = key; BreakPromptVisibility.acquire("break-service") }
            else if (!dragging && layoutKey != key) { windows.updateViewLayout(root, layout); params = layout; layoutKey = key }
            windowError = null
        } catch (_: RuntimeException) {
            hide(); retryAfter = SystemClock.elapsedRealtime() + 2_000
            windowError = "Android hid the window · retrying without resetting your timer"
        }
    }
    private fun drag(dx: Float, dy: Float) {
        if (expanded || !attached) return
        val layout = params ?: return
        val metrics = screen(); dragging = true
        layout.x = (layout.x + dx.toInt()).coerceIn(0, (metrics.widthPixels - layout.width).coerceAtLeast(0))
        layout.y = (layout.y + dy.toInt()).coerceIn(0, (metrics.heightPixels - layout.height).coerceAtLeast(0))
        try { windows.updateViewLayout(root, layout) } catch (_: RuntimeException) { hide() }
    }
    private fun constrain() {
        if (!attached || dragging) return
        val view = root ?: return; val layout = params ?: return; val metrics = screen()
        val y = BubblePosition.y(options.verticalFraction, metrics.heightPixels, view.height, (32 * metrics.density).toInt())
        if (layout.y != y) {
            layout.y = y
            try { windows.updateViewLayout(view, layout) } catch (_: RuntimeException) { hide() }
        }
    }
    private fun dock() {
        val layout = params ?: return
        val metrics = screen(); val height = compactSize(metrics).second
        val right = BubblePosition.nearestRight(layout.x, metrics.widthPixels, layout.width)
        val fraction = BubblePosition.fraction(layout.y, metrics.heightPixels, height, (32 * metrics.density).toInt())
        options = options.copy(dockRight = right, verticalFraction = fraction); dragging = false; layoutKey = ""
        scope.launch { app.timerPreferences.position(right, fraction); tick() }
    }
    private fun openBreak(value: BreakSession) {
        expanded = false; acknowledgment = false; ackUntil = 0
        try { startActivity(BreakNotifications.intent(this, value, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); hide() }
        catch (_: RuntimeException) { windowError = "Open KIN or its notification to choose focus time." }
    }
    private fun hide() {
        if (attached) try { root?.let { windows.removeView(it) } } catch (_: RuntimeException) { }
        attached = false; layoutKey = ""; BreakPromptVisibility.release("break-service")
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); hide(); root = null; dragging = false }
    override fun onDestroy() {
        hide(); scope.cancel()
        if (activated) { registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY); root = null; if (status.value.running) status.value = BreakTimerStatus()
        stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
    companion object {
        val status = MutableStateFlow(BreakTimerStatus())
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, BreakTimerService::class.java)); true
        } catch (_: RuntimeException) { status.value = BreakTimerStatus(message = "Android blocked the background timer. Reopen KIN to recover it."); false }
    }
}
