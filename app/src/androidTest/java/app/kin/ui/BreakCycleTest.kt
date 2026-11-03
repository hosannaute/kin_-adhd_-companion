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
    private fun shell(command: String): String = automation.executeShellCommand(command).use { descriptor ->
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().toString(Charsets.UTF_8).trim() }
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
            // There may be no KIN Compose root before launch, after close, or while
            // Settings/another app owns the screen. waitForIdle() expects a root and
            // can wait indefinitely here; tick rendering without requiring one.
            compose.mainClock.advanceTimeByFrame(); SystemClock.sleep(100)
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
    private fun tapIcon(focus: Boolean = false) {
        val label = if (focus) "Open focus timer" else "Open break timer"
        await {
            var node = nodes().firstOrNull { it.packageName == app.packageName && it.contentDescription?.toString() == label }
            while (node != null && !node.isClickable) node = node.parent
            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
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
        app.preferences.markPermissionSetupReviewed()
        app.timerPreferences.breakChoice(5,0,null)
        app.timerPreferences.focusChoice(25,null)
        app.timerPreferences.position(true,.25f)
        app.preferences.save(UserPreferences(onboarded = true, reducedMotion = true, quietStart = 0, quietEnd = 0))
        app.database.dao().allTasks().forEach { app.repository.deleteTask(it.id) }
        app.database.dao().goals().first().forEach { app.repository.deleteGoal(it.id) }
        app.repository.saveGoal(PersonalGoal(title = "Graduate confidently", why = "Build my future"))
        goal = app.database.dao().goals().first().single()
        app.repository.saveTask(Task(title = "Write one paragraph", goalId = goal.id))
        task = app.database.dao().allTasks().single()
    }
    @After fun cleanup() = runBlocking<Unit> {
        automation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
        app.stopService(Intent(app, OverlayPreviewService::class.java))
        app.preferences.markPermissionSetupReviewed()
        appOp("POST_NOTIFICATION", "allow")
        if (android.os.Build.VERSION.SDK_INT < 33 && !Notifications.allowed(app)) {
            instrumentation.runOnMainSync { app.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            await {
                nodes().firstOrNull { it.packageName?.toString()?.contains("settings") == true &&
                    it.isCheckable && !it.isChecked && it.className?.contains("Switch") == true }
                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
            await { Notifications.allowed(app) }
            shell("input keyevent 4")
        }
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
        if (android.os.Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val info = automation.serviceInfo; info.flags = previousFlags; automation.serviceInfo = info
    }

    @Test(timeout = 120_000) fun iconMovesToEdgeRestoresAfterRestartAndExpandsAndCollapses() = runBlocking<Unit> {
        appOp("SYSTEM_ALERT_WINDOW", "allow")
        val value = begin(); anotherApp()
        await { nodes().any { it.contentDescription?.toString() == "Open break timer" } }
        val before = app.breaks.remaining(app.database.dao().breakSession(value.id)!!)
        val bounds = Rect()
        nodes().first { it.contentDescription?.toString() == "Open break timer" }.getBoundsInScreen(bounds)
        val rootBounds = Rect()
        nodes().first { it.packageName == app.packageName + ".test" }.getBoundsInScreen(rootBounds)
        var down = SystemClock.uptimeMillis()
        fun motion(action: Int, x: Float, y: Float) {
            val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,x,y,0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event,true)) } finally { event.recycle() }
        }
        motion(android.view.MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY())
        for (step in 1..10) {
            val fraction = step / 10f
            motion(android.view.MotionEvent.ACTION_MOVE,
                bounds.exactCenterX() + (bounds.width()/2f + 4 - bounds.exactCenterX()) * fraction,
                bounds.exactCenterY() + (rootBounds.height()*.6f - bounds.exactCenterY()) * fraction)
            SystemClock.sleep(30)
        }
        motion(android.view.MotionEvent.ACTION_UP,bounds.width()/2f+4,rootBounds.height()*.6f)
        await { !runBlocking { app.timerPreferences.flow.first().dockRight } }
        val stored = app.timerPreferences.flow.first()
        assertTrue(stored.verticalFraction > .3f)
        tapIcon(); await { nativeText("Scrolling break") && nativeText("Start task") }
        // A real touch outside the window must collapse it.
        down = SystemClock.uptimeMillis()
        motion(android.view.MotionEvent.ACTION_DOWN,rootBounds.right - 6f,rootBounds.bottom - 6f)
        motion(android.view.MotionEvent.ACTION_UP,rootBounds.right - 6f,rootBounds.bottom - 6f)
        await { !BreakTimerService.status.value.expanded }
        app.stopService(Intent(app, BreakTimerService::class.java))
        await { !BreakTimerService.status.value.running }
        shell("input keyevent 4"); expect("Your scrolling break")
        await { BreakTimerService.status.value.running }
        anotherApp()
        await { nodes().any { it.contentDescription?.toString() == "Open break timer" } }
        val restored = Rect()
        nodes().first { it.contentDescription?.toString() == "Open break timer" }.getBoundsInScreen(restored)
        assertTrue(restored.left < restored.width()/2)
        assertEquals(stored.verticalFraction,app.timerPreferences.flow.first().verticalFraction,0f)
        assertTrue(app.breaks.remaining(app.database.dao().breakSession(value.id)!!) < before)
        assertEquals(value.id,app.database.dao().currentBreak()!!.id)
        automation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90)
        await {
            val fixture = nodes().firstOrNull { it.packageName == app.packageName + ".test" }
            val screen = Rect(); fixture?.getBoundsInScreen(screen)
            val icon = Rect(); nodes().firstOrNull { it.contentDescription?.toString() == "Open break timer" }?.getBoundsInScreen(icon)
            screen.width() > screen.height() && icon.width() > 0 && icon.left < icon.width()/2 && icon.bottom <= screen.bottom
        }
        assertEquals(stored.verticalFraction,app.timerPreferences.flow.first().verticalFraction,0f)
        automation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
    }

    @Test(timeout = 120_000) fun repeatedAppSwitchesKeepOneIconAndFocusReusesItWithLiveControls() = runBlocking<Unit> {
        appOp("SYSTEM_ALERT_WINDOW", "allow")
        val value = begin()
        repeat(3) {
            anotherApp()
            await { nodes().count { it.contentDescription?.toString() == "Open break timer" } == 1 }
            shell("input keyevent 4"); expect("Your scrolling break")
        }
        app.breaks.startFocus(value.id,value.revision,10)
        await { app.run { runBlocking { database.dao().currentFocus() != null } } }
        anotherApp()
        await { nodes().count { it.contentDescription?.toString() == "Open focus timer" } == 1 }
        tapIcon(true); await { nativeText("Focus timer") }
        clickWindow("Pause")
        await { !runBlocking { app.database.dao().currentFocus()!!.running } }
        val paused = app.database.dao().currentFocus()!!
        clickWindow("Collapse"); tapIcon(true); clickWindow("Resume")
        await { runBlocking { app.database.dao().currentFocus()!!.running } }
        assertEquals(paused.id,app.database.dao().currentFocus()!!.id)
        clickWindow("Finish early")
        await { !BreakTimerService.status.value.running }
        assertFalse(app.database.dao().session(paused.id)!!.completed)
    }

    @Test(timeout = 120_000) fun focusNotificationControlsWorkWhenOverlayPermissionIsDenied() = runBlocking<Unit> {
        val xp = app.database.dao().companion().first()?.xp ?: 0
        val id = app.repository.startFocus(15,task)
        launchFocus(); anotherApp()
        fun action(label: String): android.app.PendingIntent? = notifications.activeNotifications
            .firstOrNull { it.id == BreakNotifications.FOREGROUND_ID }?.notification?.actions
            ?.firstOrNull { it.title.toString() == label }?.actionIntent
        await { action("Pause focus") != null }
        assertFalse(BreakTimerService.status.value.visible)
        val pause = action("Pause focus")!!
        pause.send()
        await { runBlocking { app.database.dao().currentFocus()?.running == false } }
        val paused = app.database.dao().currentFocus()!!
        pause.send()
        await { action("Resume focus") != null }
        action("Resume focus")!!.send()
        await { runBlocking { app.database.dao().currentFocus()?.running == true } }
        assertEquals(id,app.database.dao().currentFocus()!!.id)
        assertTrue(app.repository.remaining(app.database.dao().currentFocus()!!) <= paused.remainingMillis)
        await { action("Pause focus") != null }
        action("Finish focus early")!!.send()
        await { !BreakTimerService.status.value.running }
        assertTrue(app.database.dao().session(id)!!.ended)
        assertFalse(app.database.dao().session(id)!!.completed)
        assertEquals(xp,app.database.dao().companion().first()?.xp ?: 0)
    }

    @Test(timeout = 120_000) fun presetsRememberDurationAndTaskAndCustomInputIsOnlyShownWhenChosen() = runBlocking<Unit> {
        launchFocus()
        compose.onNodeWithText("Plan a scrolling break").performClick()
        compose.onNodeWithText("Break minutes (1–180)").assertDoesNotExist()
        compose.onNodeWithText("10 m").performClick()
        compose.onNodeWithText("Begin break").performClick()
        expect("Your scrolling break")
        assertEquals(600_000L,app.database.dao().currentBreak()!!.durationMillis)
        compose.onNodeWithText("Stop break").performScrollTo().performClick()
        expect("Plan a scrolling break")
        compose.onNodeWithText("Plan a scrolling break").performClick()
        compose.onNodeWithText("10 m").assertIsSelected()
        compose.onNodeWithText("Begin break").performClick()
        expect("Your scrolling break")
        compose.onNodeWithText("Start task").performScrollTo().performClick()
        compose.onNodeWithText("Focus minutes (1–180)").assertDoesNotExist()
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onNodeWithText("Focus minutes (1–180)").performTextReplacement("7")
        compose.onNodeWithText("Begin focus").performClick()
        await { runBlocking { app.database.dao().currentFocus() != null } }
        assertEquals(420_000L,app.database.dao().currentFocus()!!.durationMillis)
        assertEquals(task.id,app.database.dao().currentFocus()!!.taskId)
        assertEquals(7,app.timerPreferences.flow.first().focusMinutes)
    }

    @Test(timeout = 120_000) fun firstOpenShowsPermissionsAndSkippingPersistsAcrossRecreation() = runBlocking<Unit> {
        app.preferences.markPermissionSetupReviewed(false)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        expect("Set up your companion")
        compose.onNodeWithText("Allow companion window").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue without floating window").performScrollTo().performClick()
        expect("Hello, dreamer")
        assertTrue(app.preferences.permissionSetupReviewed.first())
        scenario!!.recreate()
        expect("Hello, dreamer")
        compose.onNodeWithContentDescription("Settings").performClick()
        expect("Review permissions and test window")
        compose.onNodeWithText("Review permissions and test window").performClick()
        expect("Set up your companion")
    }

    @Test(timeout = 120_000) fun freshInstallReachesHomeFromPermissionSetupWithoutTyping() = runBlocking<Unit> {
        app.preferences.save(UserPreferences(onboarded = false, reducedMotion = true))
        app.preferences.markPermissionSetupReviewed(false)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        expect("Set up your companion")
        compose.onNodeWithText("Continue without floating window").performScrollTo().performClick()
        expect("Hello, dreamer")
        assertTrue(app.preferences.flow.first().onboarded)
    }

    @Test(timeout = 120_000) fun permissionPreviewShowsARealWindowAndReturnsWithoutCreatingATimer() = runBlocking<Unit> {
        appOp("SYSTEM_ALERT_WINDOW", "allow")
        app.preferences.markPermissionSetupReviewed(false)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        expect("Set up your companion")
        compose.onNodeWithText("Test window over other apps").performScrollTo().performClick()
        await { OverlayPreviewService.visible.value && nativeText("Your companion window works") }
        assertNull(app.database.dao().currentBreak())
        assertNull(app.database.dao().currentFocus())
        clickWindow("Back to KIN")
        expect("Set up your companion")
        await { !OverlayPreviewService.visible.value }
    }

    @Test(timeout = 120_000) fun homeStartsTheBreakFlowAndFocusHasADirectTab() = runBlocking<Unit> {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        expect("Hello, dreamer")
        compose.onNodeWithText("Take a scrolling break").performScrollTo().performClick()
        expect("Plan a scrolling break")
        compose.onNodeWithTag("choice:Return to task").performScrollTo().performClick()
        compose.onNodeWithTag("option:Return to task:${task.id}").performClick()
        compose.onNodeWithText("Begin break").performClick()
        expect("Your scrolling break")
        assertEquals(task.id, app.database.dao().currentBreak()!!.taskId)
        compose.onNodeWithText("Home", useUnmergedTree = true).performClick()
        expect("Open active timer")
        compose.onNodeWithText("Focus", useUnmergedTree = true).performClick()
        expect("Your scrolling break")
    }

    @Test(timeout = 120_000) fun setupPauseRecreateAndChosenFocusDurationWorkAsOneCycle() = runBlocking<Unit> {
        launchFocus()
        compose.onNodeWithTag("focus_screen").performScrollToNode(hasText("Plan a scrolling break"))
        compose.onNodeWithText("Plan a scrolling break").performClick()
        compose.onNodeWithTag("choice:Return to task").performScrollTo().performClick()
        compose.onNodeWithTag("option:Return to task:${task.id}").performClick()
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
        compose.onNodeWithText("10 m").performClick()
        compose.onNodeWithText("Begin focus").performClick()
        expect("A little focus")
        await { runBlocking { app.database.dao().currentFocus() != null } }
        val focus = app.database.dao().currentFocus()!!
        assertEquals(600_000L, focus.durationMillis); assertEquals(task.id, focus.taskId)
        assertEquals("FOCUSING", app.database.dao().breakSession(value.id)!!.phase)
        await { BreakTimerService.status.value.running }
    }

    @Test(timeout = 120_000) fun realWindowExpiresOverAnotherAppSnoozesWithGoalAndStartsChosenFocus() = runBlocking<Unit> {
        appOp("SYSTEM_ALERT_WINDOW", "allow")
        app.repository.saveTask(Task(title = "Different higher-priority task", priority = 2))
        val value = begin()
        scenario!!.onActivity { assertTrue(FloatingCompanionService.start(it)) }
        await { !FloatingCompanionService.status.value.visible && FloatingCompanionService.status.value.message.contains("Timer companion") }
        anotherApp()
        await { BreakTimerService.status.value.visible && nodes().any { it.contentDescription?.toString() == "Open break timer" } }
        nearExpiry(value.id)
        await { BreakTimerService.status.value.visible && nativeText("Your break is over") }
        await { !FloatingCompanionService.status.value.visible }
        assertNull(app.database.dao().currentFocus())
        clickWindow("Snooze 5 min")
        await { runBlocking { app.database.dao().currentBreak()?.snoozeCount == 1 } }
        await { nativeText("Five more minutes") && nativeText(goal.title) }
        val snoozed = app.database.dao().currentBreak()!!
        assertEquals("RUNNING", snoozed.phase); assertTrue(app.breaks.remaining(snoozed) > 240_000)
        await { nodes().any { it.contentDescription?.toString() == "Open break timer" } && !nativeText("Five more minutes") }
        assertFalse(FloatingCompanionService.status.value.visible)
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
        await { BreakTimerService.status.value.running && !BreakTimerService.status.value.visible }
    }

    @Test(timeout = 120_000) fun deniedOverlayUsesRealNotificationActionsAndRejectsStaleSnooze() = runBlocking<Unit> {
        val xp = app.database.dao().companion().first()?.xp ?: 0
        val value = begin(); anotherApp(); nearExpiry(value.id)
        await { notifications.activeNotifications.any { it.tag == "kin.break.${value.id}" && it.id == BreakNotifications.DUE_ID } }
        assertFalse(BreakTimerService.status.value.visible)
        val notification = notifications.activeNotifications.single { it.tag == "kin.break.${value.id}" && it.id == BreakNotifications.DUE_ID }.notification
        val snooze = notification.actions.single { it.title.toString() == "Snooze 5 min" }.actionIntent
        snooze.send()
        await { runBlocking { app.database.dao().currentBreak()?.snoozeCount == 1 } }
        snooze.send()
        await { androidx.work.WorkManager.getInstance(app).getWorkInfosForUniqueWork("break-action:${value.id}:0")
            .get(10, java.util.concurrent.TimeUnit.SECONDS).all { it.state.isFinished } }
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

    @Test(timeout = 120_000) fun naturalFocusCompletionRewardsOnceAndOffersTheNextBreak() = runBlocking<Unit> {
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

    @Test(timeout = 120_000) fun pausedBreakRecoversServiceAndExplicitBreakStillRemindsDuringQuietHours() = runBlocking<Unit> {
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
        await { BreakTimerService.status.value.visible && nativeText("Your break is over") }
        clickWindow("Stop break")
        await { !BreakTimerService.status.value.running }
    }

    @Test(timeout = 120_000) fun deniedWindowAndNotificationsStillRecoverTheDueReminderInApp() = runBlocking<Unit> {
        val value = begin()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            // Android's dedicated TestApi revokes the real permission without killing the
            // instrumentation target. Settings/pm revoke would terminate this test process.
            // Access is enabled only on the test emulator and restored immediately afterward.
            val policy = shell("settings get global hidden_api_policy")
            try {
                shell("settings put global hidden_api_policy 1")
                automation.adoptShellPermissionIdentity("android.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL",
                    "android.permission.REVOKE_RUNTIME_PERMISSIONS")
                val manager = app.getSystemService("permission")
                manager.javaClass.getMethod("revokePostNotificationPermissionWithoutKillForTest",
                    String::class.java, Int::class.javaPrimitiveType).invoke(manager, app.packageName, android.os.Process.myUid() / 100_000)
            } finally {
                automation.dropShellPermissionIdentity()
                if (policy == "null") shell("settings delete global hidden_api_policy")
                else shell("settings put global hidden_api_policy $policy")
            }
        } else {
            // The legacy Settings master switch updates NotificationManager's package state;
            // changing AppOps alone does not reliably disable already-enabled notifications.
            scenario!!.onActivity { it.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)) }
            await { automation.rootInActiveWindow?.packageName?.toString()?.contains("settings") == true }
            await {
                nodes().firstOrNull { it.packageName?.toString()?.contains("settings") == true &&
                    it.isCheckable && it.isChecked && it.className?.contains("Switch") == true }
                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
            await { !Notifications.allowed(app) }
            shell("input keyevent 4")
            expect("Your scrolling break")
        }
        await { !Notifications.allowed(app) }
        assertFalse(Notifications.allowed(app))
        anotherApp(); nearExpiry(value.id)
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
