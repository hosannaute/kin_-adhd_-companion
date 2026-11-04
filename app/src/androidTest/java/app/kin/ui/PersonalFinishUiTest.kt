package app.kin.ui

import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kin.KinApplication
import app.kin.MainActivity
import app.kin.data.*
import app.kin.services.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PersonalFinishUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<KinApplication>()
    private val dao get() = app.database.dao()
    private val manager get() = app.getSystemService(NotificationManager::class.java)
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var task: Task
    private lateinit var previous: UserPreferences
    private fun shell(command: String) = automation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
    }
    @Before fun before() = runBlocking<Unit> {
        previous = app.preferences.flow.first()
        dao.currentFocus()?.let { app.repository.stopFocus(it.id) };dao.currentBreak()?.let { app.breaks.stop(it.id,it.revision) }
        app.stopService(Intent(app,BreakTimerService::class.java));app.completions.clear();manager.cancelAll()
        shell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW deny")
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(app.packageName,android.Manifest.permission.POST_NOTIFICATIONS)
        app.preferences.markPermissionSetupReviewed()
        app.preferences.save(UserPreferences(onboarded = true,reducedMotion = true,quietStart = 0,quietEnd = 0))
        dao.allTasks().forEach { app.repository.deleteTask(it.id) }
        app.repository.saveTask(Task(title = "Read a chapter",minutes = 15))
        task = dao.allTasks().single()
    }
    @After fun after() = runBlocking<Unit> {
        scenario?.close()
        dao.currentFocus()?.let { app.repository.stopFocus(it.id) };dao.currentBreak()?.let { app.breaks.stop(it.id,it.revision) }
        app.stopService(Intent(app,BreakTimerService::class.java));app.completions.clear();manager.cancelAll()
        app.sounds.builtIn(TimerAlertKind.BREAK);app.preferences.save(previous)
    }
    private fun expect(text: String) {
        compose.waitUntil(60_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
    private suspend fun complete(): Long {
        val id = app.repository.startFocus(1,task)
        dao.updateSession(dao.session(id)!!.copy(remainingMillis = 1,startedElapsed = SystemClock.elapsedRealtime() - 100))
        assertTrue(app.repository.refreshFocus(id));assertEquals(id,app.completions.flow.first())
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (manager.activeNotifications.none { it.tag == "kin.focus.$id" }) {
            check(SystemClock.elapsedRealtime() < deadline);SystemClock.sleep(50)
        }
        return id
    }
    private fun screenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureKinUi") != "true") return
        compose.waitForIdle()
        val bitmap = automation.takeScreenshot() ?: error("No screenshot")
        File(app.filesDir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun homeFinishOffersStopAndAcknowledgementSurvivesRecreationWithoutChangingXp() = runBlocking<Unit> {
        val xp = dao.companion().first()?.xp ?: 0
        complete()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        expect("Congratulations!")
        compose.onNodeWithTag("home_screen").performScrollToNode(hasText("Stop"))
        screenshot("focus-complete")
        compose.onNodeWithText("Stop").performClick()
        compose.waitUntil(15_000) { runBlocking { app.completions.flow.first() == 0L } }
        assertNull(dao.currentFocus());assertNull(dao.currentBreak())
        assertEquals(xp + 15,dao.companion().first()!!.xp)
        scenario!!.recreate();expect("Hello, dreamer")
        compose.onNodeWithText("Congratulations!").assertDoesNotExist()
        compose.onNodeWithTag("home_screen").performScrollToNode(hasText("Floating timer"))
        compose.onNodeWithText("Floating timer").assertIsDisplayed()
        screenshot("home")
    }
    @Test fun completionNotificationOpensBreakChoicesAndStaleStopCannotDismissANewerCompletion() = runBlocking<Unit> {
        val first = complete()
        val oldStop = manager.activeNotifications.single { it.tag == "kin.focus.$first" }.notification.actions.single { it.title.toString() == "Stop" }.actionIntent
        val second = complete()
        oldStop.send()
        SystemClock.sleep(300)
        assertEquals(second,app.completions.flow.first())
        manager.activeNotifications.single { it.tag == "kin.focus.$second" }.notification.actions.single { it.title.toString() == "Take a break" }.actionIntent.send()
        expect("Plan a scrolling break")
        compose.waitUntil(15_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        assertNull(dao.currentBreak());assertNull(dao.currentFocus())
        compose.onNodeWithText("10 m").performClick()
        compose.onNodeWithText("Begin break").performClick()
        expect("Your scrolling break")
        assertEquals(600_000L,dao.currentBreak()!!.durationMillis)
        assertEquals(task.id,dao.currentBreak()!!.taskId)
    }
    @Test fun soundSheetUsesOptionsAndPersistsSelectionAcrossRecreation() = runBlocking<Unit> {
        scenario = ActivityScenario.launch(Intent(app,MainActivity::class.java).putExtra("route","more"))
        expect("Make room for you")
        compose.onNodeWithTag("settings_screen").performScrollToNode(hasText("Break sound"))
        compose.onNodeWithText("Break sound").performClick()
        expect("Audio file from storage")
        screenshot("sound-choices")
        compose.onNodeWithText("No sound").performClick()
        compose.waitUntil(15_000) { runBlocking { app.sounds.flow.first().breakEnd.silent } }
        scenario!!.recreate();expect("Make room for you")
        compose.onNodeWithTag("settings_screen").performScrollToNode(hasText("No sound"))
        compose.onNodeWithText("No sound").assertIsDisplayed()
        screenshot("timer-sounds")
    }
}
