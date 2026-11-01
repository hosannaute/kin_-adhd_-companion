package app.kin.services

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.kin.KinApplication
import app.kin.MainActivity
import app.kin.ReminderActivity
import app.kin.data.*
import androidx.work.WorkManager
import androidx.work.WorkInfo
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderActionsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<KinApplication>()
    private val notifications get() = app.getSystemService(NotificationManager::class.java)
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun setup() = runBlocking<Unit> {
        if (android.os.Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(app.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        app.preferences.save(UserPreferences(onboarded = true, reducedMotion = true, quietStart = 0, quietEnd = 0))
        app.database.dao().allTasks().forEach { app.repository.deleteTask(it.id) }
        app.database.dao().currentFocus()?.let { app.repository.stopFocus(it.id) }
    }
    @After fun cleanup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.STARTED).flatMap {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it).toList()
            }.filterIsInstance<ReminderActivity>().distinct().forEach { it.finish() }
        }
        scenario?.close()
        runBlocking {
            app.database.dao().currentFocus()?.let { app.repository.stopFocus(it.id) }
            app.database.dao().allTasks().forEach { app.repository.deleteTask(it.id) }
        }
    }
    private fun foreground() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        expect("Hello, dreamer")
    }
    private fun expect(text: String) { compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() } }
    private fun post(task: Task): Notification {
        Notifications.task(app, task)
        compose.waitUntil(15_000) { notifications.activeNotifications.any { it.tag == Notifications.taskTag(task.id) } }
        return notifications.activeNotifications.single { it.tag == Notifications.taskTag(task.id) }.notification
    }
    private suspend fun create(title: String): Task {
        app.repository.saveTask(Task(title = title, minutes = 5, description = "Preserve my notes",
            reminder = true, scheduledAt = System.currentTimeMillis() + 3_600_000))
        return app.database.dao().allTasks().single { it.title == title }
    }
    @Test fun actualStartPendingIntentOpensFocusForItsTaskAndDoesNotDuplicateSession() = runBlocking<Unit> {
        val task = create("Read a chapter")
        foreground()
        val notification = post(task)
        val start = notification.actions.single { it.title.toString() == "Start" }.actionIntent
        start.send()
        expect("A little focus")
        compose.waitUntil(20_000) { runBlocking { app.database.dao().currentFocus()?.taskId == task.id } }
        val session = app.database.dao().currentFocus()!!
        start.send()
        compose.waitForIdle()
        assertEquals(session.id, app.database.dao().currentFocus()!!.id)
        assertEquals(task.id, session.taskId)
        compose.waitUntil(15_000) { notifications.activeNotifications.none { it.tag == Notifications.taskTag(task.id) } }
    }
    @Test fun snoozeNotificationPersistsNewTimeAndCancelsOnlyItsOwnNotification() = runBlocking<Unit> {
        val first = create("Snooze me")
        val second = create("Keep this reminder")
        foreground()
        val notification = post(first)
        post(second)
        val before = System.currentTimeMillis()
        notification.actions.single { it.title.toString() == "Snooze 10 min" }.actionIntent.send()
        compose.waitUntil(45_000) { runBlocking { app.database.dao().task(first.id)!!.snoozeCount == 1 } }
        assertTrue(app.database.dao().task(first.id)!!.scheduledAt >= before + 600_000)
        compose.waitUntil(15_000) { notifications.activeNotifications.none { it.tag == Notifications.taskTag(first.id) } }
        compose.waitUntil(15_000) { WorkManager.getInstance(app).getWorkInfosForUniqueWork("task:${first.id}")
            .get().any { it.state == WorkInfo.State.ENQUEUED } }
        assertTrue(notifications.activeNotifications.any { it.tag == Notifications.taskTag(second.id) })
    }
    @Test fun notificationOptionsTargetTheCorrectTaskAndExposeRescheduleAndSkip() = runBlocking<Unit> {
        val first = create("First adventure")
        val second = create("Second adventure")
        foreground()
        val firstOptions = post(first).contentIntent
        val secondOptions = post(second).actions.single { it.title.toString() == "Options" }.actionIntent
        firstOptions.send()
        expect("Make it smaller")
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("First adventure") and hasAnyAncestor(isDialog())).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        secondOptions.send()
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("Second adventure") and hasAnyAncestor(isDialog())).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onNodeWithText("Reschedule or edit").performClick()
        compose.onNodeWithText("Task name").assertTextContains("Second adventure")
        compose.onNodeWithText("Cancel").performClick()
        secondOptions.send()
        expect("Make it smaller")
        compose.onNodeWithText("Skip this occurrence").performClick()
        compose.onNodeWithText("Skip occurrence").performClick()
        compose.waitUntil(15_000) { runBlocking { app.database.dao().task(second.id)!!.skipped } }
        assertFalse(app.database.dao().task(first.id)!!.resolved)
    }
    @Test fun staleStartCannotStartFocusOrAwardXpAndExportedLauncherCannotAutoStart() = runBlocking<Unit> {
        val task = create("A finished adventure")
        foreground()
        scenario!!.onActivity { activity ->
            activity.startActivity(Intent(activity.intent).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("route", "focus"); putExtra("taskId", task.id); putExtra("taskAction", "START")
            })
        }
        expect("Make it smaller")
        assertNull(app.database.dao().currentFocus())
        compose.onNodeWithText("Close").performClick()
        val start = post(task).actions.single { it.title.toString() == "Start" }.actionIntent
        app.repository.skipTask(task.id)
        start.send()
        expect("A little focus")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("This adventure is already finished or was deleted.").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        assertNull(app.database.dao().currentFocus())
    }
}
