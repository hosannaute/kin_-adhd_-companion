package app.kin.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kin.services.Scheduler
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "kin-persistence-test.db"
    private lateinit var db: KinDatabase
    private lateinit var repository: KinRepository
    private fun open() {
        db = Room.databaseBuilder(context, KinDatabase::class.java, name).build()
        repository = KinRepository(context, db, Scheduler(context))
    }
    @Before fun setup() { context.deleteDatabase(name); open() }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun taskSurvivesDatabaseReopen() = runBlocking {
        repository.saveTask(Task(title = "Open my notes", description = "Only one page", minutes = 5))
        db.close(); open()
        val task = db.dao().tasks().first().single()
        assertEquals("Open my notes", task.title); assertEquals("Only one page", task.description)
    }
    @Test fun concurrentCompletionAwardsOnlyOnce() = runBlocking {
        repository.saveTask(Task(title = "First step", minutes = 10))
        val id = db.dao().tasks().first().single().id
        coroutineScope { List(8) { async { repository.completeTask(id) } }.forEach { it.await() } }
        assertTrue(db.dao().task(id)!!.completed)
        assertEquals(10, db.dao().companion().first()!!.xp)
        assertEquals(1, db.dao().achievements().first().size)
        db.close(); open()
        assertEquals(10, db.dao().companion().first()!!.xp)
    }
    @Test fun snoozePersistsAndIncrementsCount() = runBlocking {
        repository.saveTask(Task(title = "Try later"))
        val id = db.dao().tasks().first().single().id
        val before = System.currentTimeMillis()
        repository.snooze(id, 10)
        val task = db.dao().task(id)!!
        assertEquals(1, task.snoozeCount)
        assertTrue(task.scheduledAt >= before + 600_000L)
    }
    @Test fun goalDeletionPreservesItsTasksAndXp() = runBlocking {
        repository.saveGoal(PersonalGoal(title = "Learn", why = "Curiosity"))
        val goal = db.dao().goals().first().single()
        repository.saveTask(Task(title = "Read", goalId = goal.id))
        repository.achieveGoal(goal.id); repository.achieveGoal(goal.id)
        repository.deleteGoal(goal.id)
        assertNull(db.dao().tasks().first().single().goalId)
        assertTrue(db.dao().goals().first().isEmpty())
        assertEquals(50, db.dao().companion().first()!!.xp)
    }

    @Test fun expiredFocusRecoversAndRewardsOnlyOnceAfterReopen() = runBlocking {
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        val id = db.dao().insertSession(FocusSession(durationMillis = 1000, remainingMillis = 1000,
            startedElapsed = SystemClock.elapsedRealtime() - 2000, bootCount = boot))
        db.close(); open()
        assertTrue(repository.refreshFocus(id))
        assertFalse(repository.refreshFocus(id))
        assertEquals(15, db.dao().companion().first()!!.xp)
        assertEquals(1, db.dao().sessionCount().first())
        assertTrue(db.dao().session(id)!!.completed)
    }

    @Test fun rebootInterruptedSessionDoesNotAwardXp() = runBlocking {
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        val id = db.dao().insertSession(FocusSession(durationMillis = 1000, remainingMillis = 1000,
            startedElapsed = 0, bootCount = boot + 1))
        assertFalse(repository.refreshFocus(id))
        assertTrue(db.dao().session(id)!!.ended)
        assertFalse(db.dao().session(id)!!.completed)
        assertEquals(0, db.dao().sessionCount().first())
        assertNull(db.dao().companion().first())
    }
}
