package app.kin.data

import android.app.NotificationManager
import android.content.Intent
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import app.kin.KinApplication
import app.kin.domain.RepeatRule
import app.kin.domain.AlertMode
import app.kin.domain.ReminderStyle
import app.kin.services.BreakTimerService
import app.kin.services.FloatingCompanionService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val app=ApplicationProvider.getApplicationContext<KinApplication>()
    private val dao get()=app.database.dao()
    private lateinit var previous: UserPreferences
    @Before fun before() = runBlocking<Unit> {
        previous=app.preferences.flow.first()
        app.stopService(Intent(app,BreakTimerService::class.java));FloatingCompanionService.stop(app)
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.database.withTransaction {
            dao.clearBreaks();dao.clearSteps();dao.clearTasks();dao.clearGoals();dao.clearSessions();dao.clearMessages();dao.clearAchievements();dao.clearCompanion()
        }
        app.preferences.save(UserPreferences(onboarded=true,reducedMotion=true,quietStart=0,quietEnd=0))
    }
    @After fun after() = runBlocking<Unit> {
        dao.currentFocus()?.let { app.repository.stopFocus(it.id) }
        dao.currentBreak()?.let { app.breaks.stop(it.id,it.revision) }
        app.stopService(Intent(app,BreakTimerService::class.java))
        WorkManager.getInstance(app).cancelAllWork().result.get()
        app.getSystemService(NotificationManager::class.java).cancelAll()
        app.preferences.save(previous)
    }
    private suspend fun seed(): BackupSnapshot {
        app.repository.saveGoal(PersonalGoal(title="Graduate",why="A future I choose"))
        val goal=dao.allGoals().single()
        app.repository.saveTask(Task(title="Read notes",description="Keep this note",goalId=goal.id,minutes=15,reminder=true,
            scheduledAt=System.currentTimeMillis()+86_400_000,repeatRule=RepeatRule.DAILY.name,repeatZone="UTC",repeatTime="09:00"))
        val task=dao.allTasks().single()
        app.repository.addSteps(task.id,listOf("Open notes","Read one page"))
        app.repository.updateStep(dao.steps(task.id).first().id,completed=true)
        app.repository.completeTask(task.id)
        app.repository.achieveGoal(goal.id)
        dao.insertMessage(ConversationMessage(text="A private message",fromUser=true))
        dao.insertAchievement(Achievement("task:99999999","A deleted task memory"))
        app.preferences.save(UserPreferences(onboarded=true,name="Pip",reducedMotion=true,darkMode=true,quietStart=21,quietEnd=7,
            alertMode=AlertMode.VIBRATION_ONLY,reminderStyle=ReminderStyle.FIRM,snoozeMinutes=10,snoozeLimit=3))
        app.timerPreferences.breakChoice(10,task.id,goal.id)
        return BackupCodec.decode(app.backups.export())
    }
    @Test fun roundTripPreservesRelationsProgressHistoryAndSettingsWithoutRewardingAgain() = runBlocking<Unit> {
        val source=seed();val oldIds=source.tasks.map { it.id }.toSet()
        val notice=app.backups.restore(source)
        assertTrue(notice.startsWith("Backup restored"))
        val restored=dao.allTasks()
        assertEquals(source.tasks.size,restored.size)
        assertTrue(restored.none { it.id in oldIds })
        val completed=restored.single { it.completed };val pending=restored.single { !it.resolved }
        assertEquals(pending.id,completed.nextOccurrenceId)
        assertEquals("Keep this note",pending.description)
        assertEquals(dao.allGoals().single().id,pending.goalId)
        assertEquals(2,dao.steps(pending.id).size)
        assertTrue(dao.steps(completed.id).first().completed)
        assertEquals(source.companion.xp,dao.companionSnapshot()!!.xp)
        app.repository.completeTask(completed.id);app.repository.achieveGoal(dao.allGoals().single().id)
        assertEquals(source.companion.xp,dao.companionSnapshot()!!.xp)
        assertEquals("A private message",dao.allMessages().single().text)
        assertEquals(source.achievements.size,dao.allAchievements().size)
        val pref=app.preferences.flow.first();assertEquals(source.preferences,pref)
        assertEquals(completed.id,app.timerPreferences.flow.first().taskId)
        assertFalse(app.companionMode.flow.first().monitorUsage)
    }
    @Test fun truncatedOrUnrelatedFilesLeaveExistingDataUntouched() = runBlocking<Unit> {
        seed();val before=dao.allTasks()
        for (bytes in listOf("{\"format\":\"KIN\"".toByteArray(),"Not a backup".toByteArray())) {
            assertTrue(runCatching { app.backups.read(ByteArrayInputStream(bytes)) }.isFailure)
            assertEquals(before,dao.allTasks())
        }
    }
    @Test fun futureBackupVersionIsRejectedBeforeDataChanges() = runBlocking<Unit> {
        seed();val before=dao.allTasks()
        val json=JSONObject(app.backups.export().toString(Charsets.UTF_8)).put("version",999)
        assertTrue(runCatching { app.backups.read(ByteArrayInputStream(json.toString().toByteArray())) }.isFailure)
        assertEquals(before,dao.allTasks())
    }
    @Test fun invalidReferencesAndDuplicateIdsCannotPartiallyRestore() = runBlocking<Unit> {
        val source=seed();val before=dao.allTasks()
        val bad=source.copy(steps=source.steps.map { it.copy(taskId=Long.MAX_VALUE) })
        assertTrue(runCatching { app.backups.restore(bad) }.isFailure)
        assertEquals(before,dao.allTasks())
        assertTrue(runCatching { app.backups.restore(source.copy(tasks=source.tasks+source.tasks.first())) }.isFailure)
        assertEquals(before,dao.allTasks())
    }
    @Test fun oversizedInputIsRejectedWithoutReplacingData() = runBlocking<Unit> {
        seed();val before=dao.allTasks()
        assertTrue(runCatching { app.backups.read(ByteArrayInputStream(ByteArray(BackupCodec.MAX_BYTES+1))) }.isFailure)
        assertEquals(before,dao.allTasks())
    }
    @Test fun restoreRefusesToReplaceACurrentTimer() = runBlocking<Unit> {
        val source=seed();val task=dao.allTasks().single { !it.resolved }
        val id=app.repository.startFocus(25,task)
        assertTrue(runCatching { app.backups.restore(source) }.isFailure)
        assertEquals(id,dao.currentFocus()!!.id)
        assertEquals(source.tasks.size,dao.allTasks().size)
    }
    @Test fun importedRunningFocusBecomesStoppedHistoryWithoutExtraXp() = runBlocking<Unit> {
        seed();val task=dao.allTasks().single { !it.resolved };val id=app.repository.startFocus(25,task)
        val source=BackupCodec.decode(app.backups.export());app.repository.stopFocus(id)
        app.backups.restore(source)
        assertNull(dao.currentFocus())
        val session=dao.allSessions().single();assertTrue(session.ended);assertFalse(session.running);assertFalse(session.completed)
        assertFalse(app.repository.refreshFocus(session.id))
        assertEquals(source.companion.xp,dao.companionSnapshot()!!.xp)
    }
    @Test fun importedRunningBreakCannotResumeAnOldClock() = runBlocking<Unit> {
        seed();val task=dao.allTasks().single { !it.resolved };val value=app.breaks.begin(task.id,null,5)
        val source=BackupCodec.decode(app.backups.export());app.breaks.stop(value.id,value.revision)
        app.backups.restore(source)
        assertNull(dao.currentBreak())
        assertEquals("STOPPED",dao.allBreaks().single().phase)
        assertEquals(source.companion.xp,dao.companionSnapshot()!!.xp)
    }
    @Test fun restoreSchedulesFutureRemindersAndLeavesOldOverdueTasksQuiet() = runBlocking<Unit> {
        val source=seed()
        val completed=source.tasks.single { it.completed }
        val pending=source.tasks.single { !it.resolved }
        val past=completed.copy(completed=false,rewarded=false,repeatRule="NONE",reminder=true,scheduledAt=System.currentTimeMillis()-86_400_000,nextOccurrenceId=null)
        app.backups.restore(source.copy(tasks=listOf(past,pending)))
        val future=dao.allTasks().single { it.scheduledAt>System.currentTimeMillis() }
        val overdue=dao.allTasks().single { it.scheduledAt<System.currentTimeMillis() }
        val wm=WorkManager.getInstance(app)
        assertTrue(wm.getWorkInfosForUniqueWork("task:${future.id}").get().any { !it.state.isFinished })
        assertTrue(wm.getWorkInfosForUniqueWork("task:${overdue.id}").get().none { !it.state.isFinished })
    }
}
