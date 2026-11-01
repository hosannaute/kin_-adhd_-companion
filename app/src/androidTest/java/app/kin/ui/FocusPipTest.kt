package app.kin.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kin.KinApplication
import app.kin.MainActivity
import app.kin.data.*
import app.kin.services.FloatingCompanionService
import app.kin.services.PipVisibility
import app.kin.services.UsageFixtureActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusPipTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<KinApplication>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var activity: MainActivity
    private var hadOverlay = false
    private var previousPip = AppOpsManager.MODE_DEFAULT
    private var previousFlags = 0
    private var fullScreenHeight = 0
    private var lastNativeClick = "none"

    private fun shell(command: String) {
        automation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    private fun appOp(operation: String, mode: String) = shell("appops set ${app.packageName} $operation $mode")
    private fun await(timeout: Long = 30_000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!condition()) {
            if (SystemClock.elapsedRealtime() >= end) {
                val visible = nodes()
                val own = visible.filter { it.packageName == app.packageName }.mapNotNull { it.text ?: it.contentDescription }.take(20)
                val system = visible.filter { it.packageName?.toString()?.contains("systemui") == true }.mapNotNull { it.text ?: it.contentDescription }.take(30)
                fail("Timed out. PiP=${if (::activity.isInitialized) isPinned() else false}, bounds=${pipBounds()}, coordination=${PipVisibility.visible.value}, last click=$lastNativeClick; KIN=$own; System UI=$system")
            }
            compose.waitForIdle()
            SystemClock.sleep(100)
        }
    }
    private fun expect(text: String) = await {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        automation.windows.mapNotNull { it.root }.forEach(pending::add)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (!node.refresh()) continue // PiP accessibility nodes can otherwise retain unfocused text.
            result += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(pending::add)
        }
        return result
    }
    private fun pipBounds(): Rect? = automation.windows.firstOrNull {
        it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.root?.packageName == app.packageName
    }?.let { window -> Rect().also(window::getBoundsInScreen) }
    private fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
    }
    private fun clickNative(label: String, vararg ids: String) {
        var lastTap = 0L
        await {
            var node = nodes().firstOrNull {
                (it.contentDescription?.toString()?.equals(label, true) == true ||
                    it.text?.toString()?.equals(label, true) == true || ids.any { id -> it.viewIdResourceName?.endsWith(":id/$id") == true })
            }
            while (node != null && !node.isClickable) node = node.parent
            if (node != null) {
                val bounds = Rect(); node.getBoundsInScreen(bounds)
                lastNativeClick = "$label, package=${node.packageName}, id=${node.viewIdResourceName}, bounds=$bounds"
                tap(bounds.exactCenterX(), bounds.exactCenterY())
                true
            } else {
                if (SystemClock.elapsedRealtime() - lastTap > 1_500) {
                    pipBounds()?.let { tap(it.exactCenterX(), it.exactCenterY()); lastTap = SystemClock.elapsedRealtime() }
                }
                false
            }
        }
    }
    private fun isPinned(): Boolean {
        var result = false
        instrumentation.runOnMainSync { result = activity.isInPictureInPictureMode }
        return result
    }
    private suspend fun createSession(): Long {
        app.repository.saveTask(Task(title = "PiP reading", minutes = 5))
        val task = app.database.dao().allTasks().single()
        return app.repository.startFocus(5, task)
    }
    private fun launchFocus() {
        scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).putExtra("route", "focus"))
        scenario!!.onActivity { activity = it }
        val screenshot = automation.takeScreenshot()
        fullScreenHeight = screenshot?.height ?: activity.resources.displayMetrics.heightPixels
        screenshot?.recycle()
        expect("A little focus")
    }
    private fun enter() {
        compose.onNodeWithTag("focus_screen").performScrollToNode(hasText("Open Picture-in-Picture", substring = true))
        compose.onNodeWithText("Open Picture-in-Picture", substring = true).performScrollTo().assertIsEnabled().performClick()
        await { isPinned() }
        await { PipVisibility.visible.value }
        await {
            pipBounds()?.let { it.width() > 0 && it.height() > 0 &&
                it.height() < fullScreenHeight / 2 } == true
        }
    }
    @Before fun setup() = runBlocking<Unit> {
        assertTrue("CI device must support native PiP", app.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
        val info = automation.serviceInfo; previousFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        previousPip = app.getSystemService(AppOpsManager::class.java).checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE, app.applicationInfo.uid, app.packageName)
        hadOverlay = Settings.canDrawOverlays(app)
        appOp("PICTURE_IN_PICTURE", "allow")
        if (android.os.Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        FloatingCompanionService.stop(app)
        await { !FloatingCompanionService.status.value.running }
        app.preferences.save(UserPreferences(onboarded = true, reducedMotion = true, quietStart = 0, quietEnd = 0))
        app.database.dao().currentFocus()?.let { app.repository.stopFocus(it.id) }
        app.database.dao().allTasks().forEach { app.repository.deleteTask(it.id) }
    }
    @After fun cleanup() = runBlocking<Unit> {
        FloatingCompanionService.stop(app)
        scenario?.close()
        await { !PipVisibility.visible.value }
        app.database.dao().currentFocus()?.let { app.repository.stopFocus(it.id) }
        app.database.dao().allTasks().forEach { app.repository.deleteTask(it.id) }
        appOp("SYSTEM_ALERT_WINDOW", if (hadOverlay) "allow" else "default")
        appOp("PICTURE_IN_PICTURE", when(previousPip) {
            AppOpsManager.MODE_ALLOWED -> "allow"; AppOpsManager.MODE_IGNORED -> "ignore"; AppOpsManager.MODE_ERRORED -> "deny"; else -> "default"
        })
        val info = automation.serviceInfo; info.flags = previousFlags; automation.serviceInfo = info
    }

    @Test fun realPipShowsTaskAndTicksWhileAnotherAppIsForegrounded() = runBlocking<Unit> {
        val id = createSession()
        launchFocus(); enter()
        assertFalse(FloatingCompanionService.status.value.running)
        await { nodes().any { it.text?.toString() == "PiP reading" } }
        val pattern = Regex("\\d{2}:\\d{2}")
        fun timer() = nodes().firstOrNull { it.packageName == app.packageName && it.text?.toString()?.matches(pattern) == true }?.text?.toString()
        await { timer() != null }
        val first = timer()
        val before = app.repository.remaining(app.database.dao().session(id)!!)
        shell("am start -W -f 0x18000000 -n ${app.packageName}.test/${UsageFixtureActivity::class.java.name}")
        await { nodes().any { it.packageName == app.packageName + ".test" } }
        await { timer() != null && timer() != first }
        assertTrue(isPinned())
        assertTrue(app.repository.remaining(app.database.dao().session(id)!!) < before)
        assertEquals(id, app.database.dao().currentFocus()!!.id)
    }

    @Test fun nativePauseResumeExpandRotationAndRecreationPreserveTheSameSession() = runBlocking<Unit> {
        val id = createSession()
        launchFocus(); enter()
        clickNative("Pause focus")
        await { runBlocking { app.database.dao().session(id)?.running == false } }
        val paused = app.database.dao().session(id)!!
        val deadline = SystemClock.elapsedRealtime() + 1_200
        await { SystemClock.elapsedRealtime() >= deadline }
        assertEquals(paused.remainingMillis, app.repository.remaining(app.database.dao().session(id)!!))
        clickNative("Resume focus")
        await { runBlocking { app.database.dao().session(id)?.running == true } }
        clickNative("Expand", "expand_button", "fullscreen_button")
        await { !isPinned() && !PipVisibility.visible.value }
        expect("A little focus")
        compose.onNodeWithText("Tasks", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(id, app.database.dao().currentFocus()!!.id)
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        expect("A little focus")
        scenario!!.recreate()
        scenario!!.onActivity { activity = it }
        expect("A little focus")
        assertEquals(id, app.database.dao().currentFocus()!!.id)
        assertTrue(app.database.dao().currentFocus()!!.running)
    }

    @Test fun nativeFinishEarlyPersistsInterruptionWithoutXp() = runBlocking<Unit> {
        val id = createSession()
        val xp = app.database.dao().companion().first()?.xp ?: 0
        launchFocus(); enter()
        clickNative("Finish focus early")
        await { runBlocking { app.database.dao().session(id)!!.ended } }
        val ended = app.database.dao().session(id)!!
        assertFalse(ended.completed); assertFalse(ended.running)
        assertEquals(xp, app.database.dao().companion().first()?.xp ?: 0)
        await { nodes().any { it.text?.toString() == "Saved · No XP" } }
    }

    @Test fun dismissKeepsTimerRunningAndRestoresAnAlreadyRunningOverlay() = runBlocking<Unit> {
        val id = createSession()
        appOp("SYSTEM_ALERT_WINDOW", "allow")
        launchFocus()
        scenario!!.onActivity { assertTrue(FloatingCompanionService.start(it)) }
        await { FloatingCompanionService.status.value.visible }
        enter()
        await { !FloatingCompanionService.status.value.visible }
        assertTrue(FloatingCompanionService.status.value.running)
        clickNative("Dismiss", "dismiss_button")
        await { !PipVisibility.visible.value }
        await { FloatingCompanionService.status.value.visible }
        val current = app.database.dao().currentFocus()!!
        assertEquals(id, current.id); assertTrue(current.running)
        assertFalse(current.ended)
    }

    @Test fun deniedPipOffersSettingsAndCanRecoverWithoutLosingTheSession() = runBlocking<Unit> {
        val id = createSession()
        appOp("PICTURE_IN_PICTURE", "ignore")
        launchFocus()
        compose.onNodeWithTag("focus_screen").performScrollToNode(hasText("Allow Picture-in-Picture"))
        compose.onNodeWithText("Allow Picture-in-Picture").performScrollTo().performClick()
        await { automation.rootInActiveWindow?.packageName?.toString()?.contains("settings") == true }
        assertFalse(PipVisibility.visible.value)
        assertEquals(id, app.database.dao().currentFocus()!!.id)
        appOp("PICTURE_IN_PICTURE", "allow")
        shell("input keyevent 4")
        await { compose.onAllNodesWithText("Open Picture-in-Picture", substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        enter()
        assertEquals(id, app.database.dao().currentFocus()!!.id)
    }

    @Test fun naturalCompletionAwardsOnceAndDoesNotUndoAnOverlayPause() = runBlocking<Unit> {
        val id = createSession()
        val xp = app.database.dao().companion().first()?.xp ?: 0
        appOp("SYSTEM_ALERT_WINDOW", "allow")
        launchFocus()
        scenario!!.onActivity { assertTrue(FloatingCompanionService.start(it)) }
        await { FloatingCompanionService.status.value.visible }
        val notifications = app.getSystemService(NotificationManager::class.java)
        await { notifications.activeNotifications.any { it.id == 21002 } }
        notifications.activeNotifications.single { it.id == 21002 }.notification.actions
            .single { it.title.toString() == "Pause 15 min" }.actionIntent.send()
        await { FloatingCompanionService.status.value.message == "Paused for 15 minutes" }
        enter()
        val session = app.database.dao().session(id)!!
        app.database.dao().updateSession(session.copy(remainingMillis = 1_500, startedElapsed = SystemClock.elapsedRealtime()))
        await { runBlocking { app.database.dao().session(id)!!.completed } }
        assertEquals(xp + 15, app.database.dao().companion().first()!!.xp)
        assertFalse(app.repository.refreshFocus(id))
        assertEquals(xp + 15, app.database.dao().companion().first()!!.xp)
        await { nodes().any { it.text?.toString() == "+15 XP · Well done" } }
        clickNative("Dismiss", "dismiss_button")
        await { !PipVisibility.visible.value }
        assertTrue(FloatingCompanionService.status.value.running)
        assertFalse(FloatingCompanionService.status.value.visible)
        assertEquals("Paused for 15 minutes", FloatingCompanionService.status.value.message)
    }
}
