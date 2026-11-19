package app.kin.domain

import java.time.*
import java.time.temporal.ChronoUnit

enum class RepeatRule(val label: String) { NONE("Does not repeat"), DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly") }

object Recurrence {
    /** First future occurrence, preserving local wall time and the original monthly day.
     * Missed dates are not materialized as a backlog. DST gaps follow java.time's forward shift.
     */
    fun next(scheduledAt: Long, after: Long, rule: RepeatRule, anchorDay: Int,
             time: LocalTime, zone: ZoneId): Long? {
        if (rule == RepeatRule.NONE) return null
        val scheduled = Instant.ofEpochMilli(scheduledAt).atZone(zone).toLocalDate()
        val cutoff = maxOf(scheduledAt, after)
        val today = Instant.ofEpochMilli(cutoff).atZone(zone).toLocalDate()
        fun instant(date: LocalDate) = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        return when (rule) {
            RepeatRule.DAILY -> {
                var date = scheduled.plusDays(ChronoUnit.DAYS.between(scheduled, today).coerceAtLeast(1))
                if (instant(date) <= cutoff) date = date.plusDays(1)
                instant(date)
            }
            RepeatRule.WEEKLY -> {
                var date = scheduled.plusWeeks((ChronoUnit.DAYS.between(scheduled, today) / 7).coerceAtLeast(1))
                if (instant(date) <= cutoff) date = date.plusWeeks(1)
                instant(date)
            }
            RepeatRule.MONTHLY -> {
                val month = YearMonth.from(scheduled)
                var target = month.plusMonths(ChronoUnit.MONTHS.between(month, YearMonth.from(today)).coerceAtLeast(1))
                val day = anchorDay.takeIf { it in 1..31 } ?: scheduled.dayOfMonth
                fun date() = target.atDay(minOf(day, target.lengthOfMonth()))
                if (instant(date()) <= cutoff) target = target.plusMonths(1)
                instant(date())
            }
            RepeatRule.NONE -> null
        }
    }
}
