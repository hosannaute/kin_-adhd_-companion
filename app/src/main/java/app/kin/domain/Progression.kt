package app.kin.domain

enum class Evolution(val threshold: Int, val label: String) {
    EGG(0, "Egg"), BABY(50, "Baby"), CURIOUS(150, "Curious companion"),
    YOUNG(350, "Young companion"), MATURE(700, "Mature companion"), EVOLVED(1200, "Evolved companion");
    companion object {
        fun at(xp: Int): Evolution = entries.last { xp >= it.threshold }
    }
}

object Rewards {
    const val FOCUS = 15
    const val MILESTONE = 50
    fun task(minutes: Int) = when { minutes <= 15 -> 10; minutes <= 45 -> 20; else -> 30 }
}

object FocusClock {
    /** elapsedRealtime survives process death and ignores wall-clock adjustments. */
    fun remaining(durationMillis: Long, startedElapsed: Long, nowElapsed: Long): Long =
        (durationMillis - (nowElapsed - startedElapsed).coerceAtLeast(0)).coerceAtLeast(0)
}

object ReminderPolicy {
    const val MIN_SNOOZE_MINUTES = 5
    fun snooze(now: Long, minutes: Int): Long =
        now + minutes.coerceIn(MIN_SNOOZE_MINUTES, 1440) * 60_000L
    fun isQuiet(hour: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> hour in start until end
        else -> hour >= start || hour < end
    }
}
