package app.kin.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kin.KinApplication
import app.kin.MainActivity
import app.kin.data.*
import app.kin.domain.AlertMode
import app.kin.domain.ReminderStyle
import app.kin.features.KinViewModel
import app.kin.services.BreakTimerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OfflineReleaseUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val app=ApplicationProvider.getApplicationContext<KinApplication>()
    private var scenario: ActivityScenario<MainActivity>?=null
    private lateinit var vm: KinViewModel
    private val file get()=File(app.filesDir,"kin-backup-ui-test.json")
    @Before fun before()=runBlocking<Unit> {
        app.database.dao().currentFocus()?.let { app.repository.stopFocus(it.id) }
        app.database.dao().currentBreak()?.let { app.breaks.stop(it.id,it.revision) }
        app.stopService(Intent(app,BreakTimerService::class.java))
        app.preferences.markPermissionSetupReviewed()
        app.preferences.save(UserPreferences(onboarded=true,reducedMotion=true,quietStart=0,quietEnd=0))
        app.database.dao().allTasks().forEach { app.repository.deleteTask(it.id) }
        app.repository.saveTask(Task(title="Read a page"))
    }
    @After fun after()=runBlocking<Unit> {
        scenario?.close()
        app.database.dao().currentFocus()?.let { app.repository.stopFocus(it.id) }
        app.database.dao().currentBreak()?.let { app.breaks.stop(it.id,it.revision) }
        app.stopService(Intent(app,BreakTimerService::class.java));file.delete()
        app.preferences.save(UserPreferences(onboarded=true,reducedMotion=true,quietStart=0,quietEnd=0))
    }
    private fun launch() {
        scenario=ActivityScenario.launch(Intent(app,MainActivity::class.java).putExtra("route","more"))
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("settings_screen").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty() }
        scenario!!.onActivity { vm=ViewModelProvider(it)[KinViewModel::class.java] }
    }
    private fun reveal(matcher: SemanticsMatcher) { compose.onNodeWithTag("settings_screen").performScrollToNode(matcher) }
    private fun choose(label: String,id: String) {
        reveal(hasTestTag("choice:$label"));compose.onNodeWithTag("choice:$label").performClick()
        compose.onNodeWithTag("option:$label:$id").performClick()
    }
    @Test fun listChoicesPersistAcrossActivityRecreationWithoutTyping()=runBlocking<Unit> {
        launch();choose("Alert mode","SILENT");choose("Reminder wording","FIRM")
        choose("Break snooze length","10");choose("Break snooze limit","1")
        compose.waitUntil(15_000) { runBlocking { app.preferences.flow.first().let { it.alertMode==AlertMode.SILENT && it.reminderStyle==ReminderStyle.FIRM && it.snoozeMinutes==10 && it.snoozeLimit==1 } } }
        scenario!!.recreate()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("settings_screen").fetchSemanticsNodes(atLeastOneRootRequired=false).isNotEmpty() }
        reveal(hasTestTag("choice:Alert mode"));compose.onNodeWithTag("choice:Alert mode").assertTextContains("Silent")
    }
    private suspend fun prepareRestore() {
        file.writeBytes(app.backups.export())
        app.repository.saveTask(Task(title="Keep my current task"))
        launch();reveal(hasText("Backup and restore"))
        scenario!!.onActivity { vm.readBackup(Uri.fromFile(file)) }
        compose.waitUntil(30_000) { vm.backupDraft.value!=null && !vm.backupBusy.value }
        compose.onNodeWithText("Restore this backup?").assertIsDisplayed()
    }
    @Test fun cancellingRestorePreviewPreservesCurrentData()=runBlocking<Unit> {
        prepareRestore();val before=app.database.dao().allTasks()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(10_000) { vm.backupDraft.value==null }
        assertEquals(before,app.database.dao().allTasks())
    }
    @Test fun confirmedRestoreUsesTheActualFileReadAndReplacementFlow()=runBlocking<Unit> {
        prepareRestore();compose.onNodeWithText("Replace data and restore").performClick()
        compose.waitUntil(30_000) { !vm.backupBusy.value && vm.backupNotice.value?.startsWith("Backup restored")==true }
        assertEquals(listOf("Read a page"),app.database.dao().allTasks().map { it.title })
        assertNull(vm.backupDraft.value)
    }
    @Test fun phoneCheckStartsARealOneMinuteBreakWithNoTaskCompletion()=runBlocking<Unit> {
        launch();reveal(hasText("Check timer on this phone"));compose.onNodeWithText("Check timer on this phone").performClick()
        compose.onNodeWithText("Set up one-minute check").performScrollTo().performClick()
        compose.onNodeWithText("1 m").assertIsSelected()
        compose.onNodeWithText("Begin break").performClick()
        compose.waitUntil(20_000) { runBlocking { app.database.dao().currentBreak()!=null } }
        assertEquals(60_000L,app.database.dao().currentBreak()!!.durationMillis)
        assertFalse(app.database.dao().allTasks().single().completed)
    }
}
