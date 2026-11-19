package app.kin.domain

import org.junit.Test
import org.junit.Assert.*

class BreakPolicyTest {
    @Test fun sameBootUsesElapsedTimeInsteadOfWallClock() {
        assertEquals(40_000L, BreakPolicy.remaining(60_000, 10_000, 2, 100_000, 30_000, 900_000, 2))
    }
    @Test fun elapsedExpiryClampsToZero() {
        assertEquals(0L, BreakPolicy.remaining(60_000, 10_000, 2, 100_000, 90_000, 1, 2))
    }
    @Test fun rebootRestoresTimeFromPersistedDeadline() {
        assertEquals(25_000L, BreakPolicy.remaining(60_000, 900_000, 2, 100_000, 1_000, 75_000, 3))
    }
    @Test fun rebootCannotExtendBreakPastItsSavedRemainingTime() {
        assertEquals(60_000L, BreakPolicy.remaining(60_000, 900_000, 2, 100_000, 1_000, 1, 3))
        assertEquals(0L, BreakPolicy.remaining(60_000, 900_000, 2, 100_000, 1_000, 120_000, 3))
    }
    @Test fun firstReminderNamesTheTaskAndSnoozingNamesTheGoal() {
        assertTrue(BreakPolicy.message("Open notes", "Graduate", 0).contains("Open notes"))
        assertTrue(BreakPolicy.message("Open notes", "Graduate", 1).contains("Graduate"))
        assertTrue(BreakPolicy.message("Open notes", "Graduate", 2).contains("Still want to achieve Graduate"))
        assertNotEquals(BreakPolicy.message("Open notes", "Graduate", 0), BreakPolicy.message("Open notes", "Graduate", 1))
    }
    @Test fun remindersStillWorkWithoutAPersonalGoal() {
        val text = BreakPolicy.message("Read one page", "", 3)
        assertTrue(text.contains("Read one page")); assertFalse(text.contains("null")); assertFalse(text.contains("achieve ?"))
    }
}
