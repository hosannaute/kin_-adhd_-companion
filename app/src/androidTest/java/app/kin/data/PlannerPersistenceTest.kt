package app.kin.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kin.services.Scheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PlannerPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "kin-planner-test.db"
    private lateinit var db: KinDatabase
    private lateinit var repository: KinRepository
    private var now = Instant.parse("2026-01-31T10:00:00Z").toEpochMilli()
    private fun open() {
        db = Room.databaseBuilder(context, KinDatabase::class.java, name).addMigrations(KinDatabase.MIGRATION_1_2).build()
        repository = KinRepository(context, db, Scheduler(context)) { now }
    }
    @Before fun setup() { context.deleteDatabase(name); open() }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    private suspend fun create(rule: String = "MONTHLY"): Task {
        repository.saveTask(Task(title = "A regular adventure", description = "Keep these notes", minutes = 10,
            scheduledAt = Instant.parse("2026-01-31T09:00:00Z").toEpochMilli(), repeatRule = rule, repeatZone = "UTC", repeatTime = "09:00"))
        return db.dao().allTasks().single()
    }
    @Test fun concurrentCompletionCreatesOneNextDateAndResetsOnlyItsSteps() = runBlocking {
        val task = create()
        repository.addSteps(task.id, listOf("Open notes", "Read a page"))
        repository.updateStep(db.dao().steps(task.id).first().id, completed = true)
        coroutineScope { List(8) { async { repository.completeTask(task.id) } }.awaitAll() }
        val old = db.dao().task(task.id)!!
        val next = db.dao().task(old.nextOccurrenceId!!)!!
        assertEquals(2, db.dao().allTasks().size)
        assertTrue(old.completed); assertTrue(old.rewarded)
        assertEquals(Instant.parse("2026-02-28T09:00:00Z").toEpochMilli(), next.scheduledAt)
        assertFalse(next.resolved); assertFalse(next.rewarded)
        assertEquals("Keep these notes", next.description)
        assertTrue(db.dao().steps(task.id).first().completed)
        assertTrue(db.dao().steps(next.id).none { it.completed })
        assertEquals(10, db.dao().companion().first()!!.xp)
        now = Instant.parse("2026-02-28T10:00:00Z").toEpochMilli()
        repository.completeTask(next.id)
        val third = db.dao().task(db.dao().task(next.id)!!.nextOccurrenceId!!)!!
        assertEquals(Instant.parse("2026-03-31T09:00:00Z").toEpochMilli(), third.scheduledAt)
    }
    @Test fun skipSurvivesReopenCreatesOneNextAndAwardsNothing() = runBlocking {
        val task = create()
        coroutineScope { List(8) { async { repository.skipTask(task.id) } }.awaitAll() }
        repository.completeTask(task.id)
        db.close(); open()
        val old = db.dao().task(task.id)!!
        assertTrue(old.skipped); assertFalse(old.completed); assertFalse(old.rewarded)
        assertEquals(2, db.dao().allTasks().size)
        assertNotNull(old.nextOccurrenceId)
        assertNull(db.dao().companion().first())
        assertTrue(db.dao().achievements().first().isEmpty())
    }
    @Test fun snoozeAcrossMidnightKeepsWeeklyAnchorAndStopsAfterResolved() = runBlocking {
        val task = create("WEEKLY")
        now = Instant.parse("2026-01-31T23:55:00Z").toEpochMilli()
        repository.snooze(task.id)
        assertEquals(task.repeatAnchorAt, db.dao().task(task.id)!!.repeatAnchorAt)
        repository.completeTask(task.id)
        val old = db.dao().task(task.id)!!
        assertEquals(Instant.parse("2026-02-07T09:00:00Z").toEpochMilli(), db.dao().task(old.nextOccurrenceId!!)!!.scheduledAt)
        repository.snooze(task.id)
        assertEquals(old, db.dao().task(task.id))
    }
    @Test fun stepsAreActionablePersistedDeduplicatedAndCascadeOnDelete() = runBlocking {
        val task = create("NONE")
        repository.breakDown(task, listOf(" Open notes ", "Open notes", ""))
        repository.breakDown(task, listOf("open NOTES", "Read"))
        val first = db.dao().steps(task.id).first()
        repository.updateStep(first.id, title = "Open one page", completed = true)
        db.close(); open()
        assertEquals(2, db.dao().steps(task.id).size)
        assertEquals("Open one page", db.dao().step(first.id)!!.title)
        assertTrue(db.dao().step(first.id)!!.completed)
        assertEquals("Keep these notes", db.dao().task(task.id)!!.description)
        assertNull(db.dao().companion().first())
        repository.deleteStep(first.id)
        assertEquals(1, db.dao().steps(task.id).size)
        repository.deleteTask(task.id)
        assertTrue(db.dao().steps(task.id).isEmpty())
    }
    @Test fun stoppingRepeatDoesNotCreateAFutureTask() = runBlocking {
        val task = create()
        repository.saveTask(task.copy(repeatRule = "NONE"))
        repository.completeTask(task.id)
        assertEquals(1, db.dao().allTasks().size)
        assertNull(db.dao().task(task.id)!!.nextOccurrenceId)
    }
    @Test fun lateCompletionCreatesOnlyOneFutureOccurrenceAndStaleStartIsRejected() = runBlocking {
        val task = create("DAILY")
        now = Instant.parse("2026-11-01T10:00:00Z").toEpochMilli()
        repository.skipTask(task.id)
        assertEquals(2, db.dao().allTasks().size)
        val next = db.dao().allTasks().single { !it.resolved }
        assertEquals(Instant.parse("2026-11-02T09:00:00Z").toEpochMilli(), next.scheduledAt)
        try { repository.startFocus(5, task); fail("Closed tasks must not start") } catch (_: IllegalArgumentException) { }
        assertNull(db.dao().currentFocus())
    }
}
