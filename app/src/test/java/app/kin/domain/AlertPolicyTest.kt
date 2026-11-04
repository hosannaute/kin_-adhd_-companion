package app.kin.domain

import org.junit.Assert.*
import org.junit.Test

class AlertPolicyTest {
    @Test fun soundAndVibrationModesCanAlertOutsideQuietHours() {
        assertFalse(AlertPolicy.silent(AlertMode.SOUND_VIBRATION,12,22,8))
        assertFalse(AlertPolicy.silent(AlertMode.VIBRATION_ONLY,12,22,8))
    }
    @Test fun silentModeNeverAlerts() {
        for (hour in 0..23) assertTrue(AlertPolicy.silent(AlertMode.SILENT,hour,0,0))
    }
    @Test fun bothAudibleModesBecomeSilentDuringOvernightQuietHours() {
        for (mode in listOf(AlertMode.SOUND_VIBRATION,AlertMode.VIBRATION_ONLY)) {
            assertTrue(AlertPolicy.silent(mode,23,22,8));assertTrue(AlertPolicy.silent(mode,7,22,8))
            assertFalse(AlertPolicy.silent(mode,8,22,8))
        }
    }
    @Test fun equalQuietHoursAllowAnAlert() { assertFalse(AlertPolicy.silent(AlertMode.SOUND_VIBRATION,22,22,22)) }
    @Test fun unknownStoredValuesHaveSafeDefaults() {
        assertEquals(AlertMode.SOUND_VIBRATION,AlertMode.parse("future"))
        assertEquals(ReminderStyle.GENTLE,ReminderStyle.parse(null))
    }
    @Test fun unlimitedSnoozesRemainAvailable() { assertTrue(AlertPolicy.canSnooze(100,0)) }
    @Test fun finiteSnoozeLimitStopsAtItsBoundary() {
        assertTrue(AlertPolicy.canSnooze(0,1));assertFalse(AlertPolicy.canSnooze(1,1));assertFalse(AlertPolicy.canSnooze(2,1))
        assertTrue(AlertPolicy.canSnooze(2,3));assertFalse(AlertPolicy.canSnooze(3,3))
    }
    @Test fun gentleAndFirmWordingBothNameTaskAndGoal() {
        for (style in ReminderStyle.entries) {
            val text=BreakPolicy.message("Read one page","Graduate",2,style)
            assertTrue(text.contains("Read one page"));assertTrue(text.contains("Graduate"))
        }
        assertNotEquals(BreakPolicy.message("Read","Graduate",2,ReminderStyle.GENTLE),BreakPolicy.message("Read","Graduate",2,ReminderStyle.FIRM))
    }
}
