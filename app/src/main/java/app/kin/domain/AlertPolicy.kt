package app.kin.domain

enum class AlertMode(val label: String) {
    SOUND_VIBRATION("Sound + vibration"), VIBRATION_ONLY("Vibration only"), SILENT("Silent");
    companion object { fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: SOUND_VIBRATION }
}

enum class ReminderStyle(val label: String) {
    GENTLE("Gentle"), FIRM("Firm");
    companion object { fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: GENTLE }
}

object AlertPolicy {
    fun silent(mode: AlertMode, hour: Int, start: Int, end: Int) =
        mode == AlertMode.SILENT || ReminderPolicy.isQuiet(hour, start, end)
    fun canSnooze(count: Int, limit: Int) = limit == 0 || count < limit
}
