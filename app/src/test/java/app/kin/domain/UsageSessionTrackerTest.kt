package app.kin.domain

import org.junit.Assert.*
import org.junit.Test

class UsageSessionTrackerTest {
    private val selected = setOf("selected")
    @Test fun onlySelectedForegroundTimeTriggersACheckIn() {
        val t = UsageSessionTracker(60_000)
        t.foreground("other", 0)
        assertNull(t.prompt(120_000, selected))
        t.foreground("selected", 120_000)
        assertNull(t.prompt(179_999, selected))
        assertEquals("selected", t.prompt(180_000, selected))
    }
    @Test fun switchingAppsStartsANewSession() {
        val t = UsageSessionTracker(60_000)
        t.foreground("selected", 0); t.foreground("other", 50_000)
        t.foreground("selected", 100_000)
        assertNull(t.prompt(150_000, selected))
        assertEquals("selected", t.prompt(160_000, selected))
    }
    @Test fun sameAppActivityTransitionDoesNotResetProgress() {
        val t = UsageSessionTracker(60_000)
        t.foreground("selected", 0); t.background("selected", 40_000)
        t.foreground("selected", 40_100)
        assertNull(t.prompt(60_000, selected))
        assertEquals("selected", t.prompt(60_100, selected))
    }
    @Test fun backgroundTimeIsNotCounted() {
        val t = UsageSessionTracker(60_000)
        t.foreground("selected", 0); t.background("selected", 30_000)
        assertNull(t.prompt(3_600_000, selected))
        t.foreground("selected", 3_600_000)
        assertNull(t.prompt(3_630_000, selected))
    }
    @Test fun stalePauseFromAnotherAppDoesNotClearTheCurrentApp() {
        val t = UsageSessionTracker(60_000)
        t.foreground("selected", 0); t.background("previous", 1000)
        assertEquals("selected", t.prompt(60_000, selected))
    }
    @Test fun checkInsCannotRepeatDuringTheCooldown() {
        val t = UsageSessionTracker(60_000)
        t.foreground("selected", 0)
        assertNotNull(t.prompt(60_000, selected))
        assertNull(t.prompt(120_000, selected))
        assertNull(t.prompt(659_999, selected))
        assertNotNull(t.prompt(660_000, selected))
    }
    @Test fun takingABreakSnoozesButKeepsTrackingTheCurrentApp() {
        val t = UsageSessionTracker(60_000)
        t.foreground("selected", 0); t.prompt(60_000, selected)
        t.takeBreak(60_000, 10)
        assertNull(t.prompt(659_999, selected))
        assertEquals("selected", t.prompt(660_000, selected))
    }
    @Test fun screenOffAndQuietHourResetDiscardUnobservedTime() {
        val t = UsageSessionTracker(60_000)
        t.foreground("selected", 0); t.reset(30_000)
        assertNull(t.prompt(3_600_000, selected))
        t.foreground("selected", 3_600_000)
        assertNull(t.prompt(3_659_999, selected))
    }
    @Test fun aShorterSnoozeCannotShortenTheGlobalCooldown() {
        val t = UsageSessionTracker(60_000)
        t.foreground("selected", 0); t.prompt(60_000, selected)
        t.takeBreak(60_000, 5)
        assertNull(t.prompt(360_000, selected))
        assertNotNull(t.prompt(660_000, selected))
    }
    @Test fun monitoringRequiresAPositiveThreshold() {
        assertThrows(IllegalArgumentException::class.java) { UsageSessionTracker(0) }
    }
}
