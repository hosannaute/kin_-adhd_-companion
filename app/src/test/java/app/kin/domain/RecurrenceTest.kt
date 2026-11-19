package app.kin.domain

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class RecurrenceTest {
    private val utc = ZoneId.of("UTC")
    private fun at(value: String, zone: ZoneId = utc) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    private fun next(anchor: String, after: String, rule: RepeatRule, day: Int = 31,
                     time: String = "09:00", zone: ZoneId = utc) =
        Recurrence.next(at(anchor, zone), at(after, zone), rule, day, LocalTime.parse(time), zone)

    @Test fun noRepeatHasNoNextOccurrence() {
        assertNull(next("2026-01-31T09:00", "2026-01-31T10:00", RepeatRule.NONE))
    }
    @Test fun earlyCompletionStillUsesTheFollowingDate() {
        assertEquals(at("2026-02-01T09:00"), next("2026-01-31T09:00", "2026-01-30T08:00", RepeatRule.DAILY))
    }
    @Test fun missedDailyDatesJumpToTodayIfItsTimeIsStillAhead() {
        assertEquals(at("2026-11-01T09:00"), next("2026-01-31T09:00", "2026-11-01T08:00", RepeatRule.DAILY))
    }
    @Test fun exactDueTimeMovesToTomorrow() {
        assertEquals(at("2026-11-02T09:00"), next("2026-01-31T09:00", "2026-11-01T09:00", RepeatRule.DAILY))
    }
    @Test fun weeklyKeepsOriginalWeekdayAfterSnoozeOrMissedWeeks() {
        assertEquals(at("2026-11-02T09:00"), next("2026-10-05T09:00", "2026-10-27T00:10", RepeatRule.WEEKLY))
    }
    @Test fun monthlyClampsFebruaryAndRestoresThirtyFirst() {
        assertEquals(at("2026-02-28T09:00"), next("2026-01-31T09:00", "2026-01-31T10:00", RepeatRule.MONTHLY))
        assertEquals(at("2026-03-31T09:00"), next("2026-01-31T09:00", "2026-02-28T10:00", RepeatRule.MONTHLY))
    }
    @Test fun monthlyUsesLeapDayAndSkipsMissedMonths() {
        assertEquals(at("2028-02-29T09:00"), next("2028-01-31T09:00", "2028-01-31T09:00", RepeatRule.MONTHLY))
        assertEquals(at("2028-04-30T09:00"), next("2028-01-31T09:00", "2028-04-01T12:00", RepeatRule.MONTHLY))
    }
    @Test fun dstGapShiftsOnlyThatOccurrenceThenRestoresLocalTime() {
        val zone = ZoneId.of("America/New_York")
        assertEquals(at("2026-03-08T03:30", zone), next("2026-03-07T02:30", "2026-03-07T03:00", RepeatRule.DAILY, time = "02:30", zone = zone))
        assertEquals(at("2026-03-09T02:30", zone), next("2026-03-07T02:30", "2026-03-08T04:00", RepeatRule.DAILY, time = "02:30", zone = zone))
    }
    @Test fun dstOverlapUsesOneOccurrenceAtTheEarlierOffset() {
        val zone = ZoneId.of("America/New_York")
        val first = next("2026-10-31T01:30", "2026-10-31T02:00", RepeatRule.DAILY, time = "01:30", zone = zone)!!
        assertEquals(Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(), first)
        val second = Recurrence.next(at("2026-10-31T01:30", zone), first, RepeatRule.DAILY, 31, LocalTime.of(1, 30), zone)
        assertEquals(at("2026-11-02T01:30", zone), second)
    }
}
