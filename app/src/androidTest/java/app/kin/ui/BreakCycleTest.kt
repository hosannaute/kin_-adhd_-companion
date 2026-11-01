package app.kin.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.kin.*
import app.kin.data.*
import app.kin.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BreakCycleTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<KinApplication>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private var scenario: ActivityScenario<MainActivity>? = null
    private var previousFlags = 0
    private var hadOverlay = false
    private lateinit var task: Task
    private lateinit var goal: PersonalGoal
    private val notifications get() = app.getSystemService(NotificationManager::class.java)
    private fun shell(command: String) {
        automation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    private fun appOp(operation: String, mode: String) = shell("appops set ${app.packageName} $operation $mode")
    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        automation.windows.mapNotNull { it.root }.forEach(pending::add)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.packageName == app.packageName && !node.refresh()) continue
            result += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(pending::add)
        }
        return result
    }
    private fun await(timeout: Long = 30_000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!condition()) {
            if (SystemClock.elapsedRealtime() >= end) fail("Timed out: service=${BreakTimerService.status.value}; bubble=${FloatingCompanionService.status.value}; UI=${nodes().mapNotNull { it.text ?: it.contentDescription }.take(55)}")
            compose.waitForIdle(); SystemClock.sleep(100)
        }
    }
    private fun expect(text: String) = await {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
    }
    private fun nativeText(text: String) = nodes().any { it.packageName == app.packageName && it.text?.contains(text) == true }
    private fun clickWindow(text: String) {
        await {
            val all = nodes()
            var node = all.firstOrNull { it.packageName == app.packageName && it.text?.toString() == text }
            while (node != null && !node.isClickable) node = node.parent
            if (node != null) node.performAction(AccessibilityNodeInfo.ACTION_CLICK) else {
                all.lastOrNull { it.packageName == app.packageName && it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                false
            }
        }
    }
    private fun launchFocus() {
        scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).putExtra("route", "focus"))
        expect(if (runBlocking { app.database.dao().currentBreak() } == null) "A little focus" else "Your scrolling break")
    }
    private fun anotherApp() {
        shell("am start -W -f 0x18000000 -n ${app.packageName}.test/${UsageFixtureActivity::class.java.name}")
        await { nodes().any { it.packageName == app.packageName + ".test" } }
    }
    private suspend fun begin(): BreakSession {
        val value = app.breaks.begin(task.id, goal.id, 1)
        launchFocus()
        await { BreakTimerService.status.value.running }
        return value
    }
    private suspend fun nearExpiry(id: Long) {
        val value = app.database.dao().breakSession(id)!!
        app.database.dao().updateBreak(value.copy(remainingMillis = 1_500, startedElapsed = SystemClock.elapsedRealtime(), dueAtWall = System.currentTimeMillis() + 1_500))
    }
    @Before fun setup() = runBlocking<Unit> {
        val info = automation.serviceInfo; previousFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        hadOverlay = Settings.canDrawOverlays(app)
        appOp("SYSTEM_ALERT_WINDOW", "deny")
        appOp("POST_NOTIFICATION", "allow")
        if (android.os.Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        app.database.dao().currentFocus()?.let { app.repository.stopFocus(it.id) }
        app.database.dao().currentBreak()?.let { app.breaks.stop(it.id, it.revision) }
        app.stopService(Intent(app, BreakTimerService::class.java)); FloatingCompanionService.stop(app)
        await { !BreakTimerService.status.value.running && !FloatingCompanionService.status.value.running }
        app.preferences.save(UserPreferences(onboarded = true, reducedMotion = true, quietStart = 0, quietEnd = 0))
        app.database.dao().allTasks().forEach { app.repository.deleteTask(it.id) }
        app.database.dao().goals().first().forEach { app.repository.deleteGoal(it.id) }
        app.repository.saveGoal(PersonalGoal(title = "Graduate confidently", why = "Build my future"))
        goal = app.database.dao().goals().first().single()
        app.repository.saveTask(Task(title = "Write one paragraph", goalId = goal.id))
        task = app.database.dao().allTasks().single()
    }
    @After fun cleanup() = runBlocking<Unit> {
        app.database.dao().currentFocus()?.let { app.repository.stopFocus(it.id) }
        app.database.dao().currentBreak()?.let { app.breaks.stop(it.id, it.revision) }
        app.stopService(Intent(app, BreakTimerService::class.java)); FloatingCompanionService.stop(app)
        instrumentation.runOnMainSync {
            listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.STARTED).flatMap {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it).toList()
            }.filterIsInstance<ReminderActivity>().distinct().forEach { it.finish() }
        }
        scenario?.close()
        if (automation.rootInActiveWindow?.packageName == app.packageName + ".test") shell("input keyevent 4")
        await { !BreakTimerService.status.value.running && !BreakPromptVisibility.visible.value }
        appOp("SYSTEM_ALERT_WINDOW", if (hadOverlay) "allow" else "default")
        appOp("POST_NOTIFICATION", "allow")
        val info = automation.serviceInfo; info.flags = previousFlags; automation.serviceInfo = info
    }

    @Test fun setupPauseRecreateAndChosenFocusDurationWorkAsOneCycle() = runBlocking<Unit> {
        launchFocus()
        compose.onNodeWithTag("focus_screen").performScrollToNode(hasText("Plan a scrolling break"))
        compose.onNodeWithText("Plan a scrolling break").performClick()
        compose.onNodeWithText("Write one paragraph").performScrollTo().performClick()
        compose.onNodeWithText("Graduate confidently").performScrollTo().performClick()
        compose.onNodeWithText("Begin break").performClick()
        expect("Your scrolling break")
        val value = app.database.dao().currentBreak()!!
        assertEquals(300_000L, value.durationMillis); assertEquals(goal.title, value.goalTitle)
        compose.onNodeWithText("Pause break").performScrollTo().performClick()
        await { runBlocking { app.database.dao().currentBreak()?.phase == "PAUSED" } }
        val paused = app.database.dao().currentBreak()!!
        scenario!!.recreate()
        expect("Break paused")
        assertEquals(paused.remainingMillis, app.breaks.remaining(app.database.dao().currentBreak()!!))
        compose.onNodeWithText("Start task").performScrollTo().performClick()
        expect("How long do you want to focus?")
        assertNull(app.database.dao().currentFocus())
        compose.onNodeWithText("Focus minutes (1–180)").performTextReplacement("10")
        compose.onNodeWithText("Begin focus").performClick()
        expect("A little focus")
        await { runBlocking { app.database.dao().currentFocus() != null } }
        val focus = app.database.dao().currentFocus()!!
        assertEquals(600_000L, focus.durationMillis); assertEquals(task.id, focus.taskId)
        assertEquals("FOCUSING", app.database.dao().breakSession(value.id)!!.phase)
        await { !BreakTimerService.status.value.running }
    }

    @Test fun realWindowExpiresOverAnotherAppSnoozesWithGoalAndStartsChosenFocus() = runBlocking<Unit> {
        appOp("SYSTEM_ALERT_WINDOW", "allow")
        app.repository.saveTask(Task(title = "Different higher-priority task", priority = 2))
        val value = begin()
        scenario!!.onActivity { assertTrue(FloatingCompanionService.start(it)) }
        await { FloatingCompanionService.status.value.visible }
        assertTrue(FloatingCompanionService.status.value.message.contains("planned break"))
        anotherApp(); nearExpiry(value.id)
        await { BreakTimerService.status.value.visible && nativeText("Your break is over") }
        await { !FloatingCompanionService.status.value.visible }
        assertNull(app.database.dao().currentFocus())
        clickWindow("Snooze 5 min")
        await { runBlocking { app.database.dao().currentBreak()?.snoozeCount == 1 } }
        await { nativeText("Five more minutes") && nativeText(goal.title) }
        val snoozed = app.database.dao().currentBreak()!!
        assertEquals("RUNNING", snoozed.phase); assertTrue(app.breaks.remaining(snoozed) > 240_000)
        await { !BreakTimerService.status.value.visible }
        await { FloatingCompanionService.status.value.visible }
        nearExpiry(value.id)
        await { BreakTimerService.status.value.visible && nativeText(goal.title) }
        clickWindow("Start task")
        expect("How long do you want to focus?")
        assertNull(app.database.dao().currentFocus())
        compose.onNodeWithText("5 m").performClick()
        compose.onNodeWithText("Begin focus").performClick()
        await { runBlocking { app.database.dao().currentFocus() != null } }
        assertEquals(300_000L, app.database.dao().currentFocus()!!.durationMillis)
        assertEquals(task.id, app.database.dao().currentFocus()!!.taskId)
        assertEquals("FOCUSING", app.database.dao().breakSession(value.id)!!.phase)
        await { !BreakTimerService.status.value.running && !BreakTimerService.status.value.visible }
    }

    @Test fun deniedOverlayUsesRealNotificationActionsAndRejectsStaleSnooze() = runBlocking<Unit> {
        val xp = app.database.dao().companion().first()?.xp ?: 0
        val value = begin(); anotherApp(); nearExpiry(value.id)
        await { notifications.activeNotifications.any { it.tag == "kin.break.${value.id}" && it.id == BreakNotifications.DUE_ID } }
        assertFalse(BreakTimerService.status.value.visible)
        val notification = notifications.activeNotifications.single { it.tag == "kin.break.${value.id}" && it.id == BreakNotifications.DUE_ID }.notification
        val snooze = notification.actions.single { it.title.toString() == "Snooze 5 min" }.actionIntent
        snooze.send()
        await { runBlocking { app.database.dao().currentBreak()?.snoozeCount == 1 } }
        snooze.send()
        await { androidx.work.WorkManager.getInstance(app).getWorkInfosForUniqueWork("break-action:${value.id}:0").get().all { it.state.isFinished } }
        assertEquals(1, app.database.dao().currentBreak()!!.snoozeCount)
        notification.actions.single { it.title.toString() == "Start task" }.actionIntent.send()
        expect("This break changed or ended")
        assertNull(app.database.dao().currentFocus())
        await { notifications.activeNotifications.any { it.id == BreakNotifications.FOREGROUND_ID &&
            it.notification.actions?.any { action -> action.title.toString() == "Pause break" } == true } }
        notifications.activeNotifications.single { it.id == BreakNotifications.FOREGROUND_ID }.notification.actions
            .single { it.title.toString() == "Stop break" }.actionIntent.send()
        await { runBlocking { app.database.dao().currentBreak() == null } }
        await { !BreakTimerService.status.value.running }
        assertEquals(xp, app.database.dao().companion().first()?.xp ?: 0)
    }

    @Test fun naturalFocusCompletionRewardsOnceAndOffersTheNextBreak() = runBlocking<Unit> {
        val xp = app.database.dao().companion().first()?.xp ?: 0
        val value = begin(); nearExpiry(value.id)
        expect("Your break is over")
        compose.onNode(hasText("Start task") and hasAnyAncestor(isDialog())).performClick()
        expect("How long do you want to focus?")
        compose.onNodeWithText("Begin focus").performClick()
        await { runBlocking { app.database.dao().currentFocus() != null } }
        val focus = app.database.dao().currentFocus()!!
        app.database.dao().updateSession(focus.copy(remainingMillis = 1_500, startedElapsed = SystemClock.elapsedRealtime()))
        await { runBlocking { app.database.dao().session(focus.id)!!.completed } }
        expect("Focus complete · +15 XP")
        assertEquals(xp + 15, app.database.dao().companion().first()!!.xp)
        assertFalse(app.repository.refreshFocus(focus.id))
        assertEquals("COMPLETED", app.database.dao().breakSession(value.id)!!.phase)
        compose.onNodeWithTag("focus_screen").performScrollToNode(hasText("Plan next break"))
        compose.onNodeWithText("Plan next break").performClick()
        compose.onNodeWithText("Begin break").performClick()
        expect("Your scrolling break")
        await { runBlocking { app.database.dao().currentBreak()?.let { it.id != value.id } == true } }
        assertEquals(xp + 15, app.database.dao().companion().first()!!.xp)
    }

    @Test fun pausedBreakRecoversServiceAndQuietHoursDelayTheWindow() = runBlocking<Unit> {
        appOp("SYSTEM_ALERT_WINDOW", "allow")
        val value = begin()
        compose.onNodeWithText("Pause break").performScrollTo().performClick()
        await { runBlocking { app.database.dao().currentBreak()?.phase == "PAUSED" } }
        val paused = app.database.dao().currentBreak()!!
        app.stopService(Intent(app, BreakTimerService::class.java))
        await { !BreakTimerService.status.value.running }
        scenario!!.recreate()
        expect("Break paused")
        await { BreakTimerService.status.value.running }
        assertEquals(paused.remainingMillis, app.breaks.remaining(app.database.dao().currentBreak()!!))
        val hour = java.time.LocalTime.now().hour
        app.preferences.save(UserPreferences(onboarded = true, reducedMotion = true, quietStart = (hour + 23) % 24, quietEnd = (hour + 1) % 24))
        compose.onNodeWithText("Resume break").performScrollTo().performClick()
        await { runBlocking { app.database.dao().currentBreak()?.phase == "RUNNING" } }
        anotherApp(); nearExpiry(value.id)
        await { runBlocking { app.database.dao().currentBreak()?.phase == "DUE" } }
        assertFalse(BreakTimerService.status.value.visible)
        app.preferences.save(UserPreferences(onboarded = true, reducedMotion = true, quietStart = 0, quietEnd = 0))
        await { BreakTimerService.status.value.visible }
        clickWindow("Stop break")
        await { !BreakTimerService.status.value.running }
    }

    @Test fun deniedWindowAndNotificationsStillRecoverTheDueReminderInApp() = runBlocking<Unit> {
        appOp("POST_NOTIFICATION", "ignore")
        assertFalse(Notifications.allowed(app))
        val value = begin(); anotherApp(); nearExpiry(value.id)
        await { runBlocking { app.database.dao().currentBreak()?.phase == "DUE" } }
        assertFalse(BreakTimerService.status.value.visible)
        assertTrue(notifications.activeNotifications.none { it.tag == "kin.break.${value.id}" && it.id == BreakNotifications.DUE_ID })
        shell("input keyevent 4")
        expect("Your break is over")
        assertNull(app.database.dao().currentFocus())
        compose.onNode(hasText("Stop break") and hasAnyAncestor(isDialog())).performClick()
        await { runBlocking { app.database.dao().currentBreak() == null } }
    }
}
