package app.kin.domain

enum class BreakPhase { RUNNING, PAUSED, DUE, FOCUSING, COMPLETED, STOPPED }

object BreakPolicy {
    fun remaining(remaining: Long, startedElapsed: Long, boot: Int, deadline: Long,
                  nowElapsed: Long, nowWall: Long, currentBoot: Int): Long =
        if (boot == currentBoot) FocusClock.remaining(remaining, startedElapsed, nowElapsed)
        else (deadline - nowWall).coerceIn(0, remaining.coerceAtLeast(0))

    fun message(task: String, goal: String, snoozes: Int, style: ReminderStyle = ReminderStyle.FIRM): String = when {
        snoozes > 0 && style == ReminderStyle.GENTLE -> if (goal.isBlank())
            "A small step on $task is still available. Take a breath and begin when you’re ready."
            else "Your goal, $goal, still matters. Return to $task with one small step when you’re ready."
        snoozes == 0 -> "Your break is over. Let's return to $task, one small step at a time."
        goal.isBlank() -> "Still want to make progress on $task? Let's give it five focused minutes when this break ends."
        snoozes == 1 -> "You said $goal matters to you. One small step on $task can move you closer."
        else -> "Still want to achieve $goal? Another snooze delays that next step. Let's start small with $task."
    }
}
