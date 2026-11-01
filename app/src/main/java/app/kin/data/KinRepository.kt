package app.kin.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.withTransaction
import app.kin.domain.*
import app.kin.services.Scheduler
import java.time.*

class KinRepository(private val context: Context, private val db: KinDatabase, private val scheduler: Scheduler,
                    private val nowMillis: () -> Long = { System.currentTimeMillis() }) {
    val dao = db.dao()
    private val conversation = CompanionConversation()
    // A process cannot survive a reboot, so avoid a settings-provider IPC on every timer tick.
    private val bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun boot() = bootCount

    suspend fun saveTask(value: Task) {
        require(value.title.isNotBlank()) { "Give this adventure a name." }
        val rule = RepeatRule.valueOf(value.repeatRule)
        val zone = value.repeatZone.takeIf { it.isNotBlank() }?.let(ZoneId::of) ?: ZoneId.systemDefault()
        val local = Instant.ofEpochMilli(value.scheduledAt).atZone(zone)
        val time = value.repeatTime.takeIf { it.isNotBlank() }?.let(LocalTime::parse) ?: local.toLocalTime()
        val clean = value.copy(title = value.title.trim().take(200), minutes = value.minutes.coerceIn(1, 1440),
            repeatZone = if (rule == RepeatRule.NONE) "" else zone.id,
            repeatTime = if (rule == RepeatRule.NONE) "" else time.toString())
        val task = db.withTransaction {
            if (value.id == 0L) {
                val initial = clean.copy(repeatAnchorAt = clean.scheduledAt, repeatAnchorDay = local.dayOfMonth)
                initial.copy(id = dao.insertTask(initial))
            } else {
                val existing = dao.task(value.id) ?: error("This task was deleted.")
                val reanchor = existing.repeatRule != clean.repeatRule || existing.scheduledAt != clean.scheduledAt ||
                    existing.repeatZone != clean.repeatZone || existing.repeatTime != clean.repeatTime || existing.repeatAnchorAt == 0L
                clean.copy(completed = existing.completed, rewarded = existing.rewarded,
                    skipped = existing.skipped, nextOccurrenceId = existing.nextOccurrenceId,
                    repeatAnchorAt = if (reanchor) clean.scheduledAt else existing.repeatAnchorAt,
                    repeatAnchorDay = if (reanchor) local.dayOfMonth else existing.repeatAnchorDay,
                    snoozeCount = existing.snoozeCount).also { dao.updateTask(it) }
            }
        }
        scheduler.task(task)
    }

    suspend fun completeTask(id: Long) {
        closeTask(id, skipped = false)
    }
    suspend fun skipTask(id: Long) { closeTask(id, skipped = true) }
    private suspend fun closeTask(id: Long, skipped: Boolean) {
        val next = db.withTransaction {
            val task = dao.task(id) ?: return@withTransaction null
            if (task.resolved) return@withTransaction null
            val following = createNextOccurrence(task)
            dao.updateTask(task.copy(completed = !skipped, skipped = skipped,
                rewarded = task.rewarded || !skipped, nextOccurrenceId = following?.id))
            if (!skipped) {
                if (!task.rewarded) award(Rewards.task(task.minutes))
                dao.insertAchievement(Achievement("task:$id", "Completed ${task.title}"))
            }
            following
        }
        scheduler.cancelTask(id)
        next?.let(scheduler::task)
    }
    private suspend fun createNextOccurrence(task: Task): Task? {
        val rule = RepeatRule.valueOf(task.repeatRule)
        if (rule == RepeatRule.NONE || task.nextOccurrenceId != null) return null
        val zone = task.repeatZone.takeIf { it.isNotBlank() }?.let(ZoneId::of) ?: ZoneId.systemDefault()
        val time = task.repeatTime.takeIf { it.isNotBlank() }?.let(LocalTime::parse)
            ?: Instant.ofEpochMilli(task.scheduledAt).atZone(zone).toLocalTime()
        val nextAt = Recurrence.next(task.repeatAnchorAt.takeIf { it > 0 } ?: task.scheduledAt,
            maxOf(nowMillis(), task.scheduledAt), rule, task.repeatAnchorDay, time, zone) ?: return null
        val value = task.copy(id = 0, scheduledAt = nextAt, completed = false, skipped = false,
            rewarded = false, snoozeCount = 0, nextOccurrenceId = null)
        val next = value.copy(id = dao.insertTask(value))
        dao.steps(task.id).forEach { dao.insertStep(it.copy(id = 0, taskId = next.id, completed = false)) }
        return next
    }

    suspend fun deleteTask(id: Long) { dao.deleteTask(id); scheduler.cancelTask(id) }
    suspend fun snooze(id: Long, minutes: Int = 10) {
        val task = db.withTransaction {
            val old = dao.task(id) ?: return@withTransaction null
            if (old.resolved) return@withTransaction null
            old.copy(scheduledAt = ReminderPolicy.snooze(nowMillis(), minutes),
                snoozeCount = old.snoozeCount + 1).also { dao.updateTask(it) }
        }
        if (task != null) scheduler.task(task) else scheduler.cancelTask(id)
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
            if (task != null) require(dao.task(task.id)?.resolved == false) { "This adventure is already finished or was deleted." }
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

    suspend fun breakDown(task: Task, steps: List<String>) = addSteps(task.id, steps, distinct = true)
    suspend fun addSteps(taskId: Long, titles: List<String>, distinct: Boolean = false) = db.withTransaction {
        val parent = dao.task(taskId) ?: error("This task was deleted.")
        require(!parent.resolved) { "This adventure is already finished." }
        val existing = dao.steps(taskId)
        val known = existing.map { it.title.lowercase() }.toMutableSet()
        val clean = titles.map { it.trim().take(300) }.filter { it.isNotBlank() }
            .filter { !distinct || known.add(it.lowercase()) }
        require(existing.size + clean.size <= 100) { "Keep each adventure to 100 small steps or fewer." }
        val start = (existing.maxOfOrNull { it.position } ?: -1) + 1
        clean.forEachIndexed { index, title -> dao.insertStep(Subtask(taskId = taskId, title = title, position = start + index)) }
    }
    suspend fun updateStep(id: Long, title: String? = null, completed: Boolean? = null) = db.withTransaction {
        val step = dao.step(id) ?: return@withTransaction
        val parent = dao.task(step.taskId) ?: return@withTransaction
        require(!parent.resolved) { "This adventure is already finished." }
        val cleanTitle = title?.trim()?.take(300) ?: step.title
        require(cleanTitle.isNotBlank()) { "Give the step a name." }
        dao.updateStep(step.copy(title = cleanTitle, completed = completed ?: step.completed))
    }
    suspend fun deleteStep(id: Long) = db.withTransaction {
        val step = dao.step(id) ?: return@withTransaction
        val parent = dao.task(step.taskId) ?: return@withTransaction
        require(!parent.resolved) { "This adventure is already finished." }
        dao.deleteStep(id)
    }
}
