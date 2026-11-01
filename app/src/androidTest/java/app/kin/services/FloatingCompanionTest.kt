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
import app.kin.KinApplication
import app.kin.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingCompanionTest {
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
        fun nodes(text: String): List<AccessibilityNodeInfo> = automation.windows.flatMap {
            it.root?.findAccessibilityNodeInfosByText(text) ?: emptyList()
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
                scenario.onActivity { it.startActivity(settingsIntent) }
                withTimeout(180_000) { while (nodes("Still taking a break").isEmpty()) delay(1000) }
                assertTrue(nodes("Taking a break").isNotEmpty())
                assertTrue(nodes("Just scrolling").isNotEmpty())
                val returnButton = nodes("Return to my goals").first()
                assertTrue(returnButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                await { nodes("Your adventures").isNotEmpty() }
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
