package app.kin.ui

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kin.KinApplication
import app.kin.MainActivity
import app.kin.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlannerUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app = ApplicationProvider.getApplicationContext<KinApplication>()
    @Before fun setup() = runBlocking<Unit> {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        app.preferences.save(UserPreferences(onboarded = true, reducedMotion = true))
        app.database.dao().allTasks().forEach { app.repository.deleteTask(it.id) }
        app.database.dao().currentFocus()?.let { app.repository.stopFocus(it.id) }
    }
    @After fun cleanup() = runBlocking<Unit> {
        app.database.dao().allTasks().forEach { app.repository.deleteTask(it.id) }
    }
    private fun launchTasks() = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).putExtra("route", "tasks"))
    private fun expect(text: String) { compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() } }
    private fun back() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.injectInputEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK), true)
        automation.injectInputEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK), true)
    }
    @Test fun createRepeatingTaskWithNativePickersAndPersistActionableStep() = runBlocking<Unit> {
        launchTasks().use { scenario ->
            expect("Your adventures")
            compose.onNodeWithContentDescription("Create task").performClick()
            compose.onNodeWithText("Task name").performTextInput("Read one page")
            compose.onNodeWithText("Date:", substring = true).performScrollTo().performClick()
            // Android's picker is a platform window, outside the Compose semantics tree.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            assertNotNull(root)
            fun hasClass(node: android.view.accessibility.AccessibilityNodeInfo, name: String): Boolean =
                node.className?.contains(name) == true || (0 until node.childCount).any { i -> node.getChild(i)?.let { hasClass(it, name) } == true }
            assertTrue(hasClass(root, "DatePicker") || root.findAccessibilityNodeInfosByViewId("android:id/date_picker_header_date").isNotEmpty())
            back()
            compose.onNodeWithText("Time:", substring = true).performScrollTo().performClick()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val timeRoot = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
            assertNotNull(timeRoot)
            assertTrue(hasClass(timeRoot, "TimePicker") || timeRoot.findAccessibilityNodeInfosByViewId("android:id/hours").isNotEmpty())
            back()
            compose.onNodeWithText("Daily").performScrollTo().performClick()
            compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
            expect("Read one page")
            assertEquals("DAILY", app.database.dao().allTasks().single().repeatRule)
            compose.onNodeWithText("Add a small step", substring = true).performScrollTo().performClick()
            compose.onNodeWithText("Step name").performTextInput("Open the book")
            compose.onNodeWithText("Save step").performClick()
            expect("Open the book")
            compose.onNodeWithContentDescription("Complete step Open the book").performScrollTo().performClick()
            compose.waitUntil(15_000) { runBlocking { app.database.dao().steps(app.database.dao().allTasks().single().id).single().completed } }
            scenario.recreate()
            expect("Open the book")
            compose.onNodeWithContentDescription("Complete step Open the book").assertIsOn()
            compose.onNodeWithContentDescription("Edit step Open the book").performScrollTo().performClick()
            compose.onNodeWithText("Step name").performTextReplacement("Open one chapter")
            compose.onNodeWithText("Save step").performClick()
            expect("Open one chapter")
        }
    }
    @Test fun taskSpecificBreakdownPreservesNotesAndSkipCreatesNextOccurrence() = runBlocking<Unit> {
        app.repository.saveTask(Task(title = "Write assignment", description = "Keep my notes", repeatRule = "WEEKLY"))
        val task = app.database.dao().allTasks().single()
        launchTasks().use {
            expect("Your adventures")
            compose.onNodeWithText("Options for Write assignment").performScrollTo().performClick()
            compose.onNodeWithText("Make it smaller").performClick()
            compose.onNodeWithText("Too overwhelming").performClick()
            compose.onNodeWithText("Save steps to task").performScrollTo().performClick()
            compose.waitUntil(15_000) { runBlocking { app.database.dao().steps(task.id).isNotEmpty() } }
            assertEquals("Keep my notes", app.database.dao().task(task.id)!!.description)
            compose.onNodeWithText("Options for Write assignment").performScrollTo().performClick()
            compose.onNodeWithText("Skip this occurrence").performClick()
            compose.onNodeWithText("Skip occurrence").performClick()
            compose.waitUntil(15_000) { runBlocking { app.database.dao().task(task.id)!!.skipped } }
            assertEquals(2, app.database.dao().allTasks().size)
            compose.onNodeWithText("Completed").performScrollTo().performClick()
            expect("Skipped · no XP")
        }
    }
}
