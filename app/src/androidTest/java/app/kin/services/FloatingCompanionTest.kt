package app.kin.services

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import app.kin.KinApplication
import app.kin.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingCompanionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<KinApplication>()
    @Before fun allowControlNotificationOnTestDevices() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    private fun appOp(mode: String, operation: String = "SYSTEM_ALERT_WINDOW") {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "appops set ${app.packageName} $operation $mode").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        }
    }
    private suspend fun await(condition: () -> Boolean) {
        withTimeout(45_000) { while (!condition()) delay(100) }
    }

    @Test fun deniedOverlayPermissionDoesNotStartAService() = runBlocking {
        val wasAllowed = Settings.canDrawOverlays(app)
        app.stopService(Intent(app, FloatingCompanionService::class.java))
        await { !FloatingCompanionService.status.value.running }
        appOp("deny")
        try {
            assertFalse(Settings.canDrawOverlays(app))
            assertFalse(FloatingCompanionService.start(app))
            assertFalse(FloatingCompanionService.status.value.running)
        } finally { appOp(if (wasAllowed) "allow" else "default") }
    }

    @Test fun standardUsageAccessSettingsCanBeOpened() {
        assertNotNull(app.packageManager.resolveActivity(UsageAccess.settingsIntent(), 0))
    }

    @Test fun realWindowAttachesAndNotificationStopRemovesIt() = runBlocking {
        val previous = app.preferences.flow.first()
        val wasAllowed = Settings.canDrawOverlays(app)
        app.preferences.save(previous.copy(onboarded = true, reducedMotion = true, quietStart = 0, quietEnd = 0))
        appOp("allow")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { assertTrue(FloatingCompanionService.start(it)) }
                await { FloatingCompanionService.status.value.visible }
                val hour = java.time.LocalTime.now().hour
                app.preferences.save(previous.copy(onboarded = true, reducedMotion = true,
                    quietStart = (hour + 23) % 24, quietEnd = (hour + 2) % 24))
                await { !FloatingCompanionService.status.value.visible }
                assertTrue(FloatingCompanionService.status.value.running)
                assertTrue(FloatingCompanionService.status.value.message.contains("Quiet hours"))
                app.preferences.save(previous.copy(onboarded = true, reducedMotion = true, quietStart = 0, quietEnd = 0))
                await { FloatingCompanionService.status.value.visible }
                val notifications = app.getSystemService(NotificationManager::class.java)
                // NotificationManager posts across a process boundary; a window can attach first.
                await { notifications.activeNotifications.any { item ->
                    item.id == 21002 && item.notification.actions?.any { it.title.toString() == "Stop" } == true
                } }
                val notification = notifications.activeNotifications
                    .first { it.id == 21002 }.notification
                val stop = notification.actions.first { it.title.toString() == "Stop" }
                stop.actionIntent.send()
                await { !FloatingCompanionService.status.value.running }
                assertFalse(FloatingCompanionService.status.value.visible)
            }
        } finally {
            FloatingCompanionService.stop(app)
            appOp(if (wasAllowed) "allow" else "default")
            app.preferences.save(previous)
        }
    }

    @Test fun revokingPermissionStopsTheActiveOverlay() = runBlocking {
        val previous = app.preferences.flow.first()
        val wasAllowed = Settings.canDrawOverlays(app)
        app.preferences.save(previous.copy(onboarded = true, reducedMotion = true, quietStart = 0, quietEnd = 0))
        appOp("allow")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { assertTrue(FloatingCompanionService.start(it)) }
                await { FloatingCompanionService.status.value.visible }
                appOp("deny")
                await { !FloatingCompanionService.status.value.running }
                assertFalse(FloatingCompanionService.status.value.visible)
            }
        } finally {
            FloatingCompanionService.stop(app)
            appOp(if (wasAllowed) "allow" else "default")
            app.preferences.save(previous)
        }
    }

    @Test fun selectedAppForegroundTimeOffersARealCheckInAndReturnsToTasks() = runBlocking {
        val previous = app.preferences.flow.first()
        val previousMode = app.companionMode.flow.first()
        val wasAllowed = Settings.canDrawOverlays(app)
        val hadUsage = UsageAccess.granted(app)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val serviceInfo = automation.serviceInfo
        val previousFlags = serviceInfo.flags
        serviceInfo.flags = serviceInfo.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = serviceInfo
        val settingsIntent = Intent(Settings.ACTION_SETTINGS)
        val selectedPackage = app.packageManager.resolveActivity(settingsIntent, 0)!!.activityInfo.packageName
        // Compose exposes virtual children. Traverse them, as UIAutomator does, rather
        // than relying on the platform's provider-specific find-by-text shortcut.
        fun nodes(text: String): List<AccessibilityNodeInfo> {
            val found = mutableListOf<AccessibilityNodeInfo>()
            val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
            automation.windows.mapNotNull { it.root }.forEach(pending::add)
            while (pending.isNotEmpty()) {
                val node = pending.removeFirst()
                if (node.text?.contains(text) == true || node.contentDescription?.contains(text) == true) found += node
                for (index in 0 until node.childCount) node.getChild(index)?.let(pending::add)
            }
            return found
        }
        app.preferences.save(previous.copy(onboarded = true, reducedMotion = true, quietStart = 0, quietEnd = 0))
        app.companionMode.select(selectedPackage, true)
        app.companionMode.threshold(1)
        app.companionMode.monitoring(true)
        appOp("allow"); appOp("allow", "GET_USAGE_STATS")
        try {
            assertTrue(UsageAccess.granted(app))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { assertTrue(FloatingCompanionService.start(it)) }
                await { FloatingCompanionService.status.value.visible }
                await { nodes("Stop").isNotEmpty() }
                scenario.onActivity { it.startActivity(settingsIntent) }
                val wallStart = System.currentTimeMillis()
                val elapsedStart = android.os.SystemClock.elapsedRealtime()
                try {
                    withTimeout(180_000) { while (nodes("Still taking a break").isEmpty()) delay(1000) }
                } catch (e: TimeoutCancellationException) {
                    val wall = System.currentTimeMillis()
                    val elapsed = android.os.SystemClock.elapsedRealtime()
                    val events = app.getSystemService(android.app.usage.UsageStatsManager::class.java)
                        .queryEvents(wall - 240_000, wall)
                    val event = android.app.usage.UsageEvents.Event()
                    val selectedEvents = mutableListOf<String>()
                    while (events != null && events.hasNextEvent()) {
                        events.getNextEvent(event)
                        if (event.packageName == selectedPackage) selectedEvents += "${event.eventType}:${wall - event.timeStamp}ms ago"
                    }
                    throw AssertionError("No check-in: status=${FloatingCompanionService.status.value}; " +
                        "usageGranted=${UsageAccess.granted(app)}; selectedEvents=$selectedEvents; " +
                        "clockSkew=${(wall - wallStart) - (elapsed - elapsedStart)}ms; " +
                        "stopVisible=${nodes("Stop").isNotEmpty()}", e)
                }
                assertTrue(nodes("Taking a break").isNotEmpty())
                assertTrue(nodes("Just scrolling").isNotEmpty())
                var returnButton = nodes("Return to my goals").first()
                while (!returnButton.isClickable) {
                    val parent = returnButton.parent ?: break
                    returnButton = parent
                }
                assertTrue(returnButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                await {
                    var openedTasks = false
                    InstrumentationRegistry.getInstrumentation().runOnMainSync {
                        openedTasks = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .any { it is MainActivity && it.intent.getStringExtra("route") == "tasks" }
                    }
                    openedTasks
                }
                compose.waitUntil(45_000) { compose.onAllNodesWithText("Your adventures").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Your adventures").assertIsDisplayed()
            }
        } finally {
            FloatingCompanionService.stop(app)
            await { !FloatingCompanionService.status.value.running }
            appOp(if (wasAllowed) "allow" else "default")
            appOp(if (hadUsage) "allow" else "default", "GET_USAGE_STATS")
            app.companionMode.monitoring(previousMode.monitorUsage)
            app.companionMode.threshold(previousMode.thresholdMinutes)
            if (selectedPackage !in previousMode.selectedApps) app.companionMode.select(selectedPackage, false)
            app.preferences.save(previous)
            serviceInfo.flags = previousFlags; automation.serviceInfo = serviceInfo
        }
    }
}
