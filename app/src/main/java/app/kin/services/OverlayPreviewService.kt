package app.kin.services

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import app.kin.*
import app.kin.R
import app.kin.domain.Evolution
import app.kin.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/** User-triggered, short-lived permission test. Never creates or modifies a timer. */
class OverlayPreviewService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle get() = registry
    private val saved = SavedStateRegistryController.create(this)
    override val savedStateRegistry get() = saved.savedStateRegistry
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var root: FrameLayout? = null
    private var started = false
    override fun onCreate() {
        super.onCreate(); saved.performAttach(); saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (started) return START_NOT_STICKY
        started = true
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("kin_window_test", "Companion window test", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 22012, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        try {
            startForeground(22012, NotificationCompat.Builder(this, "kin_window_test").setSmallIcon(R.drawable.ic_kin)
                .setContentTitle("KIN window test").setContentText("A sample reminder will close automatically.")
                .setContentIntent(open).setSilent(true).build())
        } catch (_: RuntimeException) { stopSelf(); return START_NOT_STICKY }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START); registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        scope.launch {
            try {
                delay(1_500)
                val deadline = SystemClock.elapsedRealtime() + 15_000
                while (isActive && SystemClock.elapsedRealtime() < deadline) {
                    if (!Settings.canDrawOverlays(this@OverlayPreviewService)) break
                    val unlocked = !getSystemService(KeyguardManager::class.java).isKeyguardLocked && getSystemService(PowerManager::class.java).isInteractive
                    if (!KinVisibility.resumed.value && unlocked && root == null) show()
                    if (KinVisibility.resumed.value && root != null) break
                    delay(250)
                }
            } finally { stopSelf() }
        }
        return START_NOT_STICKY
    }
    @Suppress("DEPRECATION")
    private fun show() {
        val windows = getSystemService(WindowManager::class.java)
        val metrics = DisplayMetrics().also { windows.defaultDisplay.getRealMetrics(it) }
        val view = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(this@OverlayPreviewService); setViewTreeSavedStateRegistryOwner(this@OverlayPreviewService)
            addView(ComposeView(this@OverlayPreviewService).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent { KinTheme(false) { Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                    Column(Modifier.heightIn(max = (metrics.heightPixels / metrics.density - 64).coerceAtLeast(180f).dp)
                        .verticalScroll(rememberScrollState()).padding(20.dp)) {
                        Text("Your companion window works", style = MaterialTheme.typography.titleLarge)
                        CompanionArt(Evolution.CURIOUS, true, Modifier.fillMaxWidth().height(180.dp), CreatureMood.WAVING)
                        Text("This is a test reminder over another app. When your break ends, Start task and Snooze will appear here.")
                        Button(onClick = {
                            stopSelf()
                            try { startActivity(Intent(this@OverlayPreviewService, MainActivity::class.java)
                                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
                            catch (_: RuntimeException) { /* The user can reopen KIN normally. */ }
                        }) { Text("Back to KIN") }
                    }
                } } }
            })
        }
        try {
            windows.addView(view, WindowManager.LayoutParams(minOf(metrics.widthPixels - (32 * metrics.density).toInt(), (340 * metrics.density).toInt()),
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER })
            root = view; visible.value = true; BreakPromptVisibility.acquire("window-preview")
        } catch (_: RuntimeException) { stopSelf() }
    }
    override fun onDestroy() {
        root?.let { try { getSystemService(WindowManager::class.java).removeView(it) } catch (_: RuntimeException) { } }
        root = null; visible.value = false; BreakPromptVisibility.release("window-preview"); scope.cancel()
        if (registry.currentState.isAtLeast(Lifecycle.State.RESUMED)) registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
    companion object {
        val visible = MutableStateFlow(false)
        fun start(context: Context): Boolean = try {
            if (!Settings.canDrawOverlays(context)) false else {
                ContextCompat.startForegroundService(context, Intent(context, OverlayPreviewService::class.java)); true
            }
        } catch (_: RuntimeException) { false }
    }
}
