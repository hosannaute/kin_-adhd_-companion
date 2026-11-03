package app.kin.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import app.kin.domain.*
import app.kin.services.BreakNotifications
import app.kin.services.Scheduler
import kotlinx.coroutines.flow.first

class BreakRepository(private val context: Context, private val db: KinDatabase, private val scheduler: Scheduler,
                      private val focus: KinRepository,
                      private val wall: () -> Long = System::currentTimeMillis,
                      private val elapsed: () -> Long = SystemClock::elapsedRealtime,
                      private val boot: Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)) {
    val dao = db.dao()
    fun remaining(value: BreakSession): Long = when (value.phase) {
        "RUNNING" -> BreakPolicy.remaining(value.remainingMillis, value.startedElapsed, value.bootCount,
            value.dueAtWall, elapsed(), wall(), boot)
        "PAUSED" -> value.remainingMillis
        else -> 0
    }
    suspend fun begin(taskId: Long, goalId: Long?, minutes: Int): BreakSession {
        require(minutes in 1..180) { "Choose a break from 1 to 180 minutes." }
        val value = db.withTransaction {
            require(dao.currentBreak() == null && dao.currentFocus() == null) { "Stop or finish the current timer first." }
            val task = dao.task(taskId)
            require(task != null && !task.resolved) { "Choose a pending task to return to." }
            val goal = goalId?.let { dao.goal(it) }
            require(goalId == null || (goal != null && !goal.achieved)) { "Choose a current personal goal." }
            val duration = minutes * 60_000L
            val initial = BreakSession(taskId = task.id, taskTitle = task.title, goalTitle = goal?.title.orEmpty(),
                durationMillis = duration, remainingMillis = duration, startedElapsed = elapsed(), bootCount = boot, dueAtWall = wall() + duration)
            initial.copy(id = dao.insertBreak(initial))
        }
        scheduler.breakTimer(value, remaining(value))
        return value
    }
    /** Resolve elapsed/reboot state and stop reminders for a deleted or finished task. */
    suspend fun refresh(id: Long): BreakSession? {
        val value = db.withTransaction {
            val old = dao.breakSession(id) ?: return@withTransaction null
            if (old.phase !in listOf("RUNNING", "PAUSED", "DUE")) return@withTransaction old
            val task = old.taskId?.let { dao.task(it) }
            val next = when {
                task == null || task.resolved -> old.copy(phase = "STOPPED", revision = old.revision + 1)
                old.phase == "RUNNING" && remaining(old) == 0L -> old.copy(phase = "DUE", remainingMillis = 0)
                old.phase == "RUNNING" && old.bootCount != boot -> {
                    val left = remaining(old)
                    old.copy(remainingMillis = left, startedElapsed = elapsed(), bootCount = boot, dueAtWall = wall() + left)
                }
                else -> old
            }
            if (next != old) dao.updateBreak(next)
            next
        }
        if (value?.phase == "STOPPED") scheduler.cancelBreak(id)
        return value
    }
    suspend fun pause(id: Long, revision: Int): Boolean {
        refresh(id)
        return mutate(id, revision, setOf("RUNNING")) { old -> old.copy(phase = "PAUSED", remainingMillis = remaining(old)) }
    }
    suspend fun resume(id: Long, revision: Int): Boolean = mutate(id, revision, setOf("PAUSED")) { old ->
        old.copy(phase = "RUNNING", startedElapsed = elapsed(), bootCount = boot, dueAtWall = wall() + old.remainingMillis)
    }
    suspend fun snooze(id: Long, revision: Int, minutes: Int? = null): Boolean {
        val pref = (context.applicationContext as app.kin.KinApplication).preferences.flow.first()
        val duration = minutes ?: pref.snoozeMinutes
        require(duration in 1..180)
        return mutate(id, revision, setOf("DUE"), allowed = { AlertPolicy.canSnooze(it.snoozeCount, pref.snoozeLimit) }) { old ->
            old.copy(phase = "RUNNING", remainingMillis = duration * 60_000L, startedElapsed = elapsed(),
                bootCount = boot, dueAtWall = wall() + duration * 60_000L, snoozeCount = old.snoozeCount + 1)
        }
    }
    suspend fun stop(id: Long, revision: Int): Boolean = mutate(id, revision, setOf("RUNNING", "PAUSED", "DUE")) { it.copy(phase = "STOPPED") }

    private suspend fun mutate(id: Long, revision: Int, phases: Set<String>, allowed: (BreakSession) -> Boolean = { true }, update: (BreakSession) -> BreakSession): Boolean {
        val value = db.withTransaction {
            val old = dao.breakSession(id) ?: return@withTransaction null
            if (old.revision != revision || old.phase !in phases || !allowed(old)) return@withTransaction null
            update(old).copy(revision = old.revision + 1).also { dao.updateBreak(it) }
        } ?: return false
        scheduler.cancelBreak(id)
        if (value.phase == "RUNNING") scheduler.breakTimer(value, remaining(value))
        return true
    }
    suspend fun startFocus(id: Long, revision: Int, minutes: Int): Long {
        require(minutes in 1..180) { "Choose a focus session from 1 to 180 minutes." }
        val sessionId = db.withTransaction {
            val old = dao.breakSession(id) ?: error("This break no longer exists.")
            require(old.revision == revision && old.phase in listOf("RUNNING", "PAUSED", "DUE")) { "This break changed. Reopen Start task to continue." }
            val task = old.taskId?.let { dao.task(it) }
            require(task != null && !task.resolved) { "This task is finished or was deleted. Stop this break and choose another task." }
            val newId = focus.insertFocus(minutes, task)
            dao.updateBreak(old.copy(phase = "FOCUSING", focusSessionId = newId, revision = old.revision + 1))
            newId
        }
        scheduler.cancelBreak(id)
        scheduler.focus(sessionId, minutes * 60_000L)
        return sessionId
    }
    fun cancelReminder(id: Long) = BreakNotifications.cancel(context, id)
    suspend fun claimReminder(id: Long, revision: Int): Boolean = db.withTransaction {
        val old = dao.breakSession(id) ?: return@withTransaction false
        if (old.phase != "DUE" || old.revision != revision || old.alertedRevision == revision) return@withTransaction false
        dao.updateBreak(old.copy(alertedRevision = revision))
        true
    }
}
