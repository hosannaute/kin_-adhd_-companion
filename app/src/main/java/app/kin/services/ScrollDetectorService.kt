package app.kin.services

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.*
import androidx.savedstate.*
import app.kin.KinApplication
import app.kin.MainActivity
import app.kin.data.CompanionModeOptions
import app.kin.data.UserPreferences
import app.kin.domain.Evolution
import app.kin.domain.ReminderPolicy
import app.kin.domain.ScrollAlert
import app.kin.domain.ScrollSessionTracker
import app.kin.ui.KinTheme
import app.kin.ui.ScrollAlertPanel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.ref.WeakReference
import java.time.LocalTime

data class ScrollDetectorStatus(
    val connected: Boolean = false,
    val monitoring: Boolean = false,
    val alertVisible: Boolean = false,
    val message: String = "Auto scroll detection is off"
)

/**
 * Offline AccessibilityService that listens strictly to scroll & window-change events
 * (never reading window content) and surfaces a floating reminder when scrolling exceeds
 * the user's chosen duration threshold.
 */
class ScrollDetectorService : AccessibilityService(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    private val savedState = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as KinApplication
    private lateinit var windows: WindowManager

    private var options = CompanionModeOptions()
    private var prefs by mutableStateOf(UserPreferences())
    private var stage by mutableStateOf(Evolution.EGG)
    private var tracker = ScrollSessionTracker(10 * 60_000L)
    private var ignoredPackages: Set<String> = emptySet()
    private var timerBusy = false

    private var activeAlert by mutableStateOf<ScrollAlert?>(null)
    private var activeAppLabel by mutableStateOf("")
    private var root: FrameLayout? = null
    private var attached = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        savedState.performAttach()
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windows = getSystemService(WindowManager::class.java)
        ignoredPackages = computeIgnoredPackages()

        scope.launch {
            app.companionMode.flow.collect { mode ->
                val thresholdChanged = mode.scrollThresholdMinutes != options.scrollThresholdMinutes
                options = mode
                if (thresholdChanged) {
                    tracker = ScrollSessionTracker(mode.scrollThresholdMinutes * 60_000L)
                }
                if (!mode.scrollDetectionEnabled) {
                    tracker.reset(SystemClock.elapsedRealtime())
                    dismissOverlay()
                }
                updateStatus()
            }
        }
        scope.launch {
            app.preferences.flow.collect { pref ->
                prefs = pref
                if (isQuietNow()) {
                    tracker.reset(SystemClock.elapsedRealtime())
                    dismissOverlay()
                }
            }
        }
        scope.launch {
            app.database.dao().companion().collect { stage = Evolution.at(it?.xp ?: 0) }
        }
        scope.launch {
            app.database.dao().activeFocus().collect { focus ->
                timerBusy = focus != null
                if (timerBusy) {
                    tracker.reset(SystemClock.elapsedRealtime())
                    dismissOverlay()
                }
                updateStatus()
            }
        }
        scope.launch {
            while (isActive) {
                delay(15_000L)
                periodicCheck()
            }
        }
        updateStatus()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        val pkg = ev.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (!options.scrollDetectionEnabled || timerBusy || isQuietNow() || isScreenLocked()) return
        if (BreakPromptVisibility.visible.value || PipVisibility.visible.value) return

        val now = SystemClock.elapsedRealtime()
        if (ev.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg == packageName) {
                tracker.reset(now)
                dismissOverlay()
            } else {
                tracker.onWindowChanged(pkg, now, ignoredPackages)
            }
        } else if (ev.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            val alert = tracker.onScroll(
                packageName = pkg,
                nowElapsed = now,
                monitorAllApps = options.scrollAllApps,
                selectedApps = options.selectedApps,
                ignoredPackages = ignoredPackages
            )
            if (alert != null) {
                showAlert(alert)
            }
        }
    }

    override fun onInterrupt() {
        tracker.reset(SystemClock.elapsedRealtime())
        dismissOverlay()
    }

    private fun periodicCheck() {
        val now = SystemClock.elapsedRealtime()
        if (!options.scrollDetectionEnabled || timerBusy || isQuietNow() || isScreenLocked()) {
            tracker.reset(now)
            dismissOverlay()
            updateStatus()
            return
        }
        val alert = tracker.checkAlert(now)
        if (alert != null) showAlert(alert)
        updateStatus()
    }

    private fun isQuietNow(): Boolean =
        ReminderPolicy.isQuiet(LocalTime.now().hour, prefs.quietStart, prefs.quietEnd)

    private fun isScreenLocked(): Boolean =
        getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !getSystemService(PowerManager::class.java).isInteractive

    private fun computeIgnoredPackages(): Set<String> {
        val ignored = mutableSetOf(
            packageName,
            "com.android.systemui",
            "com.android.settings",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher"
        )
        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.let {
                ignored += it
            }
        }
        return ignored
    }

    private fun resolveAppLabel(pkg: String): String = runCatching {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault("")

    fun triggerPreview() {
        val mins = options.scrollThresholdMinutes
        showAlert(ScrollAlert("preview", mins, 25), previewLabel = "your scrolling feed")
    }

    private fun showAlert(alert: ScrollAlert, previewLabel: String? = null) {
        if (BreakPromptVisibility.visible.value || PipVisibility.visible.value) return
        activeAlert = alert
        activeAppLabel = previewLabel ?: resolveAppLabel(alert.packageName)
        attachOverlay()
        updateStatus()
    }

    @Suppress("DEPRECATION")
    private fun screenMetrics(): DisplayMetrics =
        DisplayMetrics().also { windows.defaultDisplay.getRealMetrics(it) }

    private fun attachOverlay() {
        val metrics = screenMetrics()
        val width = minOf(metrics.widthPixels - (24 * metrics.density).toInt(), (336 * metrics.density).toInt())
        val maxHeight = (metrics.heightPixels / metrics.density - 96f).coerceAtLeast(240f)

        if (root == null) {
            root = object : FrameLayout(this) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                        dismissOverlay()
                        return false
                    }
                    return super.dispatchTouchEvent(event)
                }
            }.apply {
                setViewTreeLifecycleOwner(this@ScrollDetectorService)
                setViewTreeSavedStateRegistryOwner(this@ScrollDetectorService)
                addView(ComposeView(this@ScrollDetectorService).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                    setContent {
                        KinTheme(prefs.darkMode) {
                            val alert = activeAlert
                            if (alert != null) {
                                ScrollAlertPanel(
                                    companionName = prefs.name,
                                    appLabel = activeAppLabel,
                                    minutes = alert.elapsedMinutes,
                                    stage = stage,
                                    reducedMotion = prefs.reducedMotion,
                                    maxHeight = maxHeight,
                                    onGoals = {
                                        tracker.snooze(SystemClock.elapsedRealtime(), 10)
                                        dismissOverlay()
                                        openKin("tasks")
                                    },
                                    onQuickFocus = {
                                        tracker.snooze(SystemClock.elapsedRealtime(), 10)
                                        dismissOverlay()
                                        openKin("focus")
                                    },
                                    onSnooze = {
                                        tracker.snooze(SystemClock.elapsedRealtime(), 10)
                                        dismissOverlay()
                                    },
                                    onDismiss = {
                                        dismissOverlay()
                                    }
                                )
                            }
                        }
                    }
                })
            }
        }

        val layout = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            if (!attached) {
                windows.addView(root, layout)
                attached = true
            } else {
                windows.updateViewLayout(root, layout)
            }
        } catch (_: RuntimeException) {
            attached = false
            activeAlert?.let { alert ->
                Notifications.show(
                    this,
                    22099,
                    "You've been scrolling for more than ${alert.elapsedMinutes} min",
                    "Tap to return to your goals in KIN.",
                    "tasks"
                )
            }
        }
    }

    private fun dismissOverlay() {
        activeAlert = null
        if (attached) {
            try { root?.let { windows.removeView(it) } } catch (_: RuntimeException) { }
            attached = false
        }
        updateStatus()
    }

    private fun openKin(route: String) {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra("route", route)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    private fun updateStatus() {
        val active = options.scrollDetectionEnabled && !timerBusy && !isQuietNow()
        status.value = ScrollDetectorStatus(
            connected = true,
            monitoring = active,
            alertVisible = attached,
            message = when {
                attached -> "Scroll check-in window is showing"
                !options.scrollDetectionEnabled -> "Accessibility connected · scroll alerts paused"
                timerBusy -> "Focus timer active · scroll detection resting"
                isQuietNow() -> "Quiet hours · scroll detection resting"
                options.scrollAllApps -> "Active · alerts after ${options.scrollThresholdMinutes} min of scrolling"
                options.selectedApps.isEmpty() -> "Choose apps below or turn on All apps"
                else -> "Active · ${options.selectedApps.size} apps · ${options.scrollThresholdMinutes} min limit"
            }
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (attached) {
            dismissOverlay()
            root = null
        }
    }

    override fun onDestroy() {
        dismissOverlay()
        scope.cancel()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        root = null
        if (instance?.get() === this) instance = null
        status.value = ScrollDetectorStatus()
        super.onDestroy()
    }

    companion object {
        val status = MutableStateFlow(ScrollDetectorStatus())
        private var instance: WeakReference<ScrollDetectorService>? = null

        fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        fun isEnabled(context: Context): Boolean {
            if (status.value.connected) return true
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expected = ComponentName(context, ScrollDetectorService::class.java).flattenToString()
            val shortExpected = ComponentName(context, ScrollDetectorService::class.java).flattenToShortString()
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) || it.equals(shortExpected, ignoreCase = true) }
        }

        fun preview(): Boolean {
            val service = instance?.get() ?: return false
            service.triggerPreview()
            return true
        }
    }
}
