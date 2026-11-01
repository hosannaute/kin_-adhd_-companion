package app.kin.domain

import org.junit.Assert.*
import org.junit.Test

class ProgressionTest {
    @Test fun evolutionChangesOnlyAtItsThresholds() {
        Evolution.entries.forEachIndexed { index, stage ->
            assertEquals(stage, Evolution.at(stage.threshold))
            if (index > 0) assertEquals(Evolution.entries[index - 1], Evolution.at(stage.threshold - 1))
        }
        assertEquals(Evolution.EVOLVED, Evolution.at(Int.MAX_VALUE))
    }
    @Test fun taskRewardsMatchEffortBands() {
        assertEquals(10, Rewards.task(1)); assertEquals(10, Rewards.task(15))
        assertEquals(20, Rewards.task(16)); assertEquals(20, Rewards.task(45))
        assertEquals(30, Rewards.task(46))
    }
    @Test fun countdownUsesElapsedTimeOnly() {
        assertEquals(40_000L, FocusClock.remaining(60_000L, 100_000L, 120_000L))
    }
    @Test fun countdownNeverGoesNegative() { assertEquals(0L, FocusClock.remaining(60_000, 0, 100_000)) }
    @Test fun pausedDurationCanBeRebasedOnResume() {
        val paused = FocusClock.remaining(60_000L, 10_000L, 30_000L)
        assertEquals(35_000L, FocusClock.remaining(paused, 90_000L, 95_000L))
    }
    @Test fun snoozeHasMinimumAndMaximumBounds() {
        assertEquals(300_100L, ReminderPolicy.snooze(100, -1))
        assertEquals(600_100L, ReminderPolicy.snooze(100, 10))
        assertEquals(86_400_100L, ReminderPolicy.snooze(100, Int.MAX_VALUE))
    }
    @Test fun overnightQuietHoursHandleMidnightAndEdges() {
        assertTrue(ReminderPolicy.isQuiet(22,22,8)); assertTrue(ReminderPolicy.isQuiet(0,22,8))
        assertTrue(ReminderPolicy.isQuiet(7,22,8)); assertFalse(ReminderPolicy.isQuiet(8,22,8))
        assertFalse(ReminderPolicy.isQuiet(21,22,8))
    }
    @Test fun daytimeQuietHoursAreSupported() {
        assertTrue(ReminderPolicy.isQuiet(13,12,15)); assertFalse(ReminderPolicy.isQuiet(15,12,15))
    }
    @Test fun equalQuietHoursDisableQuietTime() {
        (0..23).forEach { assertFalse(ReminderPolicy.isQuiet(it,8,8)) }
    }
}
