package app.kin.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import app.kin.domain.*
import app.kin.services.Scheduler

class KinRepository(private val context: Context, private val db: KinDatabase, private val scheduler: Scheduler) {
    val dao = db.dao()
    private val conversation = CompanionConversation()
    // A process cannot survive a reboot, so avoid a settings-provider IPC on every timer tick.
    private val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun boot() = bootCount

    suspend fun saveTask(value: Task) {
        require(value.title.isNotBlank()) { "Give this adventure a name." }
        val clean = value.copy(title = value.title.trim().take(200), minutes = value.minutes.coerceIn(1, 1440))
        val task = db.withTransaction {
            if (value.id == 0L) clean.copy(id = dao.insertTask(clean)) else {
                val existing = dao.task(value.id) ?: error("This task was deleted.")
                clean.copy(completed = existing.completed, rewarded = existing.rewarded,
                    snoozeCount = existing.snoozeCount).also { dao.updateTask(it) }
            }
        }
        scheduler.task(task)
    }

    suspend fun completeTask(id: Long) {
        db.withTransaction {
            val task = dao.task(id) ?: return@withTransaction
            if (!task.completed) {
                dao.updateTask(task.copy(completed = true, rewarded = true))
                if (!task.rewarded) award(Rewards.task(task.minutes))
                dao.insertAchievement(Achievement("task:$id", "Completed ${task.title}"))
            }
        }
        scheduler.cancelTask(id)
    }

    suspend fun deleteTask(id: Long) { dao.deleteTask(id); scheduler.cancelTask(id) }
    suspend fun snooze(id: Long, minutes: Int = 10) {
        val task = db.withTransaction {
            val old = dao.task(id) ?: return@withTransaction null
            if (old.completed) return@withTransaction null
            old.copy(scheduledAt = ReminderPolicy.snooze(System.currentTimeMillis(), minutes),
                snoozeCount = old.snoozeCount + 1).also { dao.updateTask(it) }
        }
        task?.let(scheduler::task)
    }
    suspend fun rescheduleAll() { dao.allTasks().forEach(scheduler::task) }

    suspend fun saveGoal(goal: PersonalGoal) {
        require(goal.title.isNotBlank()) { "Give your goal a name." }
        if (goal.id == 0L) dao.insertGoal(goal.copy(title = goal.title.trim().take(200)))
        else db.withTransaction {
            val old = dao.goal(goal.id) ?: return@withTransaction
            dao.updateGoal(old.copy(title = goal.title.trim().take(200), why = goal.why))
        }
    }
    suspend fun achieveGoal(id: Long) = db.withTransaction {
        val goal = dao.goal(id) ?: return@withTransaction
        if (!goal.achieved) {
            dao.updateGoal(goal.copy(achieved = true, rewarded = true))
            if (!goal.rewarded) award(Rewards.MILESTONE)
            dao.insertAchievement(Achievement("goal:$id", "Reached ${goal.title}"))
        }
    }
    suspend fun deleteGoal(id: Long) = db.withTransaction { dao.unlinkGoal(id); dao.deleteGoal(id) }
    private suspend fun award(xp: Int) { dao.initCompanion(CompanionState()); dao.award(xp) }

    fun remaining(session: FocusSession): Long = when {
        !session.running -> session.remainingMillis
        session.bootCount != boot() -> 0L
        else -> FocusClock.remaining(session.remainingMillis, session.startedElapsed, SystemClock.elapsedRealtime())
    }

    suspend fun startFocus(minutes: Int, task: Task?): Long {
        val id = db.withTransaction {
            require(dao.currentFocus() == null) { "Finish or stop the current session first." }
            val duration = minutes.coerceIn(1, 180) * 60_000L
            dao.insertSession(FocusSession(taskId = task?.id, taskTitle = task?.title ?: "A little time for you",
                durationMillis = duration, remainingMillis = duration, startedElapsed = SystemClock.elapsedRealtime(), bootCount = boot()))
        }
        scheduler.focus(id, minutes.coerceIn(1, 180) * 60_000L)
        return id
    }

    suspend fun pauseFocus(id: Long) {
        refreshFocus(id)
        db.withTransaction {
            val session = dao.session(id) ?: return@withTransaction
            if (!session.ended && session.running) dao.updateSession(session.copy(remainingMillis = remaining(session), running = false))
        }
        scheduler.cancelFocus(id)
    }

    suspend fun resumeFocus(id: Long) {
        val delay = db.withTransaction {
            val session = dao.session(id) ?: return@withTransaction null
            if (session.ended || session.running) return@withTransaction null
            dao.updateSession(session.copy(startedElapsed = SystemClock.elapsedRealtime(), bootCount = boot(), running = true))
            session.remainingMillis
        }
        delay?.let { scheduler.focus(id, it) }
    }

    suspend fun stopFocus(id: Long) {
        refreshFocus(id)
        db.withTransaction {
            val session = dao.session(id) ?: return@withTransaction
            if (!session.ended) dao.updateSession(session.copy(ended = true, running = false, remainingMillis = remaining(session)))
        }
        scheduler.cancelFocus(id)
    }

    /** Exactly-once completion under a DB transaction. Reboot interrupts, rather than rewards, an active session. */
    suspend fun refreshFocus(id: Long): Boolean = db.withTransaction {
        val session = dao.session(id) ?: return@withTransaction false
        if (session.ended || !session.running) return@withTransaction false
        if (session.bootCount != boot()) {
            dao.updateSession(session.copy(ended = true, running = false))
            return@withTransaction false
        }
        if (remaining(session) > 0) return@withTransaction false
        dao.updateSession(session.copy(ended = true, completed = true, running = false, remainingMillis = 0))
        award(Rewards.FOCUS)
        dao.insertAchievement(Achievement("focus:$id", "Completed a ${session.durationMillis / 60_000} minute focus session"))
        true
    }

    suspend fun send(message: String, context: ConversationContext) {
        require(message.isNotBlank())
        val previous = dao.lastMessage()?.text
        db.withTransaction {
            dao.insertMessage(ConversationMessage(text = message.trim().take(2000), fromUser = true))
            dao.insertMessage(ConversationMessage(text = conversation.reply(message, context, previous), fromUser = false))
        }
    }

    suspend fun breakDown(task: Task, steps: List<String>) = db.withTransaction {
        // Keep the original task; accepted steps are explicitly stored in its editable description.
        val old = dao.task(task.id) ?: return@withTransaction
        val plan = steps.filter { it.isNotBlank() }.joinToString("\n") { "• ${it.trim()}" }
        dao.updateTask(old.copy(description = listOf(old.description, plan).filter { it.isNotBlank() }.joinToString("\n\n")))
    }
}
