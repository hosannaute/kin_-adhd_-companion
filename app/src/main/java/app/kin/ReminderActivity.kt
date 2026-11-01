package app.kin

/** Only app-owned PendingIntents/same-UID callers can enter here. Exported launcher intents
 * may open task options, but cannot start a timer merely by supplying an extra. */
class ReminderActivity : MainActivity() {
    override val acceptsReminderStart = true
}
