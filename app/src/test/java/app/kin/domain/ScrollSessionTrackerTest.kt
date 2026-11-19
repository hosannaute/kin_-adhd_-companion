package app.kin.domain

import org.junit.Assert.*
import org.junit.Test

class ScrollSessionTrackerTest {
    private val tenMinutes = 10 * 60_000L
    private val ignored = setOf("app.kin.companion", "com.android.systemui")

    @Test fun triggersAlertAfterContinuousScrollingPastThreshold() {
        val tracker = ScrollSessionTracker(thresholdMillis = tenMinutes, minScrollEvents = 5)
        for (minute in 0 until 10) {
            assertNull(tracker.onScroll("com.instagram.android", minute * 60_000L, true, emptySet(), ignored))
        }
        assertNull(tracker.onScroll("com.instagram.android", tenMinutes - 1_000L, true, emptySet(), ignored))
        val alert = tracker.onScroll("com.instagram.android", tenMinutes, true, emptySet(), ignored)
        assertNotNull(alert)
        assertEquals("com.instagram.android", alert!!.packageName)
        assertEquals(10, alert.elapsedMinutes)
        assertTrue(alert.scrollCount >= 5)
    }

    @Test fun requiresMinimumScrollEventsSoStaticReadingDoesNotAlert() {
        val tracker = ScrollSessionTracker(thresholdMillis = tenMinutes, minScrollEvents = 5)
        assertNull(tracker.onScroll("com.example.reader", 0L, true, emptySet(), ignored))
        assertNull(tracker.onScroll("com.example.reader", 100_000L, true, emptySet(), ignored))
        assertNull(tracker.checkAlert(tenMinutes + 5_000L))
    }

    @Test fun respectsSelectedAppsModeWhenAllAppsIsOff() {
        val tracker = ScrollSessionTracker(thresholdMillis = 60_000L, minScrollEvents = 3)
        val selected = setOf("com.zhiliaoapp.musically")
        assertNull(tracker.onScroll("com.other.app", 0L, false, selected, ignored))
        assertNull(tracker.onScroll("com.other.app", 30_000L, false, selected, ignored))
        assertNull(tracker.onScroll("com.other.app", 70_000L, false, selected, ignored))

        assertNull(tracker.onScroll("com.zhiliaoapp.musically", 100_000L, false, selected, ignored))
        assertNull(tracker.onScroll("com.zhiliaoapp.musically", 130_000L, false, selected, ignored))
        val alert = tracker.onScroll("com.zhiliaoapp.musically", 160_000L, false, selected, ignored)
        assertNotNull(alert)
        assertEquals("com.zhiliaoapp.musically", alert!!.packageName)
    }

    @Test fun ignoresKinAndSystemPackages() {
        val tracker = ScrollSessionTracker(thresholdMillis = 60_000L, minScrollEvents = 2)
        assertNull(tracker.onScroll("app.kin.companion", 0L, true, emptySet(), ignored))
        assertNull(tracker.onScroll("app.kin.companion", 70_000L, true, emptySet(), ignored))
    }

    @Test fun switchingAppsOrInactivityResetsScrollTimer() {
        val tracker = ScrollSessionTracker(thresholdMillis = 120_000L, minScrollEvents = 3, inactivityResetMillis = 60_000L)
        tracker.onScroll("com.app.a", 0L, true, emptySet(), ignored)
        tracker.onScroll("com.app.a", 30_000L, true, emptySet(), ignored)
        tracker.onWindowChanged("com.app.b", 40_000L, ignored)
        assertEquals(0L, tracker.activeElapsedMillis(45_000L))

        tracker.onScroll("com.app.a", 100_000L, true, emptySet(), ignored)
        tracker.onScroll("com.app.a", 130_000L, true, emptySet(), ignored)
        // Gap > inactivityResetMillis (60s) resets the session.
        assertNull(tracker.onScroll("com.app.a", 250_000L, true, emptySet(), ignored))
        assertEquals(0L, tracker.activeElapsedMillis(250_000L))
    }

    @Test fun snoozeSuppressesAlertsForTenMinutes() {
        val tracker = ScrollSessionTracker(thresholdMillis = 60_000L, minScrollEvents = 2)
        tracker.onScroll("com.app.feed", 0L, true, emptySet(), ignored)
        assertNotNull(tracker.onScroll("com.app.feed", 60_000L, true, emptySet(), ignored))
        tracker.snooze(60_000L, 10)
        // Continuous scrolling throughout the 10-minute snooze stays suppressed until 660_000ms.
        for (sec in 120..600 step 60) {
            assertNull(tracker.onScroll("com.app.feed", sec * 1_000L, true, emptySet(), ignored))
        }
        assertNull(tracker.onScroll("com.app.feed", 659_000L, true, emptySet(), ignored))
        assertNotNull(tracker.onScroll("com.app.feed", 660_000L, true, emptySet(), ignored))
    }
}
