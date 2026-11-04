package app.kin.data

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kin.services.Scheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BreakPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "kin-break-persistence.db"
    private lateinit var db: KinDatabase
    private lateinit var repo: KinRepository
    private lateinit var breaks: BreakRepository
    private val scheduler = Scheduler(context)
    private var wall = 2_000_000L
    private var elapsed = 10_000L
    private var boot = 7
    private val ids = mutableSetOf<Long>()
    private val focusIds = mutableSetOf<Long>()
    private fun open() {
        db = Room.databaseBuilder(context, KinDatabase::class.java, name).build()
        repo = KinRepository(context, db, scheduler)
        breaks = BreakRepository(context, db, scheduler, repo, { wall }, { elapsed }, boot)
    }
    @Before fun setup() { context.deleteDatabase(name); open() }
    @After fun cleanup() = runBlocking<Unit> {
        ids.forEach(scheduler::cancelBreak)
        focusIds.forEach(scheduler::cancelFocus)
        db.dao().currentFocus()?.let { scheduler.cancelFocus(it.id) }
        db.close(); context.deleteDatabase(name)
    }
    private suspend fun begin(): BreakSession {
        repo.saveGoal(PersonalGoal(title = "Graduate", why = "A future I choose"))
        val goal = db.dao().goals().first().single()
        repo.saveTask(Task(title = "Open notes", goalId = goal.id))
        val task = db.dao().allTasks().single()
        return breaks.begin(task.id, goal.id, 120).also { ids += it.id }
    }
    @Test fun pausedBreakAndGoalSurviveDatabaseReopen() = runBlocking {
        val value = begin(); elapsed += 12_000; wall += 12_000
        assertTrue(breaks.pause(value.id, value.revision))
        val paused = db.dao().breakSession(value.id)!!
        db.close(); open()
        val restored = db.dao().currentBreak()!!
        assertEquals("Graduate", restored.goalTitle); assertEquals("Open notes", restored.taskTitle)
        assertEquals("PAUSED", restored.phase); assertEquals(paused.remainingMillis, breaks.remaining(restored))
        elapsed += 300_000; wall += 300_000
        assertEquals(paused.remainingMillis, breaks.remaining(restored))
        assertTrue(breaks.resume(restored.id, restored.revision))
        assertEquals(paused.remainingMillis, breaks.remaining(db.dao().currentBreak()!!))
    }
    @Test fun expiryAndRepeatedSnoozeActionsCannotExtendTheBreakTwice() = runBlocking {
        val value = begin(); elapsed += value.durationMillis; wall += value.durationMillis
        val due = breaks.refresh(value.id)!!
        assertEquals("DUE", due.phase)
        val results = coroutineScope { List(8) { async { breaks.snooze(due.id, due.revision) } }.awaitAll() }
        assertEquals(1, results.count { it })
        val snoozed = db.dao().currentBreak()!!
        assertEquals(1, snoozed.snoozeCount); assertEquals(300_000L, breaks.remaining(snoozed))
        assertFalse(breaks.stop(due.id, due.revision))
        assertNull(db.dao().companion().first())
    }
    @Test fun concurrentStartsCreateOnlyOneBreak() = runBlocking {
        repo.saveTask(Task(title = "Read")); val task = db.dao().allTasks().single()
        val results = coroutineScope { List(8) { async { runCatching { breaks.begin(task.id, null, 120) } } }.awaitAll() }
        assertEquals(1, results.count { it.isSuccess })
        results.mapNotNull { it.getOrNull() }.forEach { ids += it.id }
        assertNotNull(db.dao().currentBreak())
    }
    @Test fun chosenFocusDurationTransitionsAtomicallyAndRewardsOnlyOnce() = runBlocking {
        val value = begin()
        val results = coroutineScope { List(8) { async { runCatching { breaks.startFocus(value.id, value.revision, 5) } } }.awaitAll() }
        assertEquals(1, results.count { it.isSuccess })
        val id = results.first { it.isSuccess }.getOrThrow()
        focusIds += id
        val session = db.dao().session(id)!!
        assertEquals(300_000L, session.durationMillis)
        assertEquals("FOCUSING", db.dao().breakSession(value.id)!!.phase)
        assertEquals(id, db.dao().breakSession(value.id)!!.focusSessionId)
        assertFalse(breaks.snooze(value.id, value.revision))
        db.dao().updateSession(session.copy(remainingMillis = 1, startedElapsed = SystemClock.elapsedRealtime() - 100))
        coroutineScope { List(8) { async { repo.refreshFocus(id) } }.awaitAll() }
        assertEquals(15, db.dao().companion().first()!!.xp)
        assertEquals(1, db.dao().sessionCount().first())
        assertEquals("COMPLETED", db.dao().breakSession(value.id)!!.phase)
        assertFalse(db.dao().task(value.taskId!!)!!.completed)
    }
    @Test fun earlyFinishAndStopNeverRewardTheBreak() = runBlocking {
        val value = begin(); val id = breaks.startFocus(value.id, value.revision, 5); focusIds += id
        repo.stopFocus(id)
        assertEquals("STOPPED", db.dao().breakSession(value.id)!!.phase)
        assertNull(db.dao().companion().first())
        assertFalse(db.dao().session(id)!!.completed)
    }
    @Test fun rebootRestoresRemainingAndThenBecomesDue() = runBlocking {
        val value = begin(); wall += value.durationMillis / 2; elapsed = 1_000; boot = 8
        db.close(); open()
        val restored = breaks.refresh(value.id)!!
        assertEquals(8, restored.bootCount)
        assertEquals(value.durationMillis / 2, breaks.remaining(restored))
        wall += value.durationMillis; elapsed += value.durationMillis
        assertEquals("DUE", breaks.refresh(value.id)!!.phase)
    }
    @Test fun deletedTaskStopsItsBreakAndCannotStartFocus() = runBlocking {
        val value = begin()
        repo.deleteTask(value.taskId!!)
        assertEquals("STOPPED", breaks.refresh(value.id)!!.phase)
        assertTrue(runCatching { breaks.startFocus(value.id, value.revision, 5) }.isFailure)
        assertNull(db.dao().currentFocus())
    }
    @Test fun aReminderIsClaimedOnlyOncePerSnoozeRevision() = runBlocking {
        val value = begin(); elapsed += value.durationMillis; wall += value.durationMillis
        val due = breaks.refresh(value.id)!!
        val claims = coroutineScope { List(8) { async { breaks.claimReminder(due.id, due.revision) } }.awaitAll() }
        assertEquals(1, claims.count { it })
        assertTrue(breaks.snooze(due.id, due.revision))
        elapsed += 300_000; wall += 300_000
        val next = breaks.refresh(value.id)!!
        assertTrue(breaks.claimReminder(next.id, next.revision))
    }
}
