package app.kin.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import app.kin.domain.AlertMode
import app.kin.domain.ReminderStyle

private val Context.kinPreferences by preferencesDataStore("kin_preferences")
data class UserPreferences(
    val onboarded: Boolean = false, val name: String = "Lumi", val reducedMotion: Boolean = false,
    val darkMode: Boolean = false, val reminders: Boolean = true,
    val quietStart: Int = 22, val quietEnd: Int = 8,
    val alertMode: AlertMode = AlertMode.SOUND_VIBRATION,
    val reminderStyle: ReminderStyle = ReminderStyle.GENTLE,
    val snoozeMinutes: Int = 5, val snoozeLimit: Int = 0
)

class Preferences(context: Context) {
    private val store = context.kinPreferences
    private val onboarded = booleanPreferencesKey("onboarded")
    private val name = stringPreferencesKey("name")
    private val reduced = booleanPreferencesKey("reduced_motion")
    private val dark = booleanPreferencesKey("dark")
    private val reminders = booleanPreferencesKey("reminders")
    private val quietStart = intPreferencesKey("quiet_start")
    private val quietEnd = intPreferencesKey("quiet_end")
    private val permissionSetup = booleanPreferencesKey("permission_setup_v1")
    private val notificationAsked = booleanPreferencesKey("notification_asked")
    private val alertMode = stringPreferencesKey("timer_alert_mode")
    private val reminderStyle = stringPreferencesKey("reminder_style")
    private val snoozeMinutes = intPreferencesKey("break_snooze_minutes")
    private val snoozeLimit = intPreferencesKey("break_snooze_limit")
    val permissionSetupReviewed = store.data.map { it[permissionSetup] ?: false }
    val notificationPromptAttempted = store.data.map { it[notificationAsked] ?: false }
    suspend fun markPermissionSetupReviewed(reviewed: Boolean = true) { store.edit { it[permissionSetup] = reviewed } }
    suspend fun markNotificationPromptAttempted() { store.edit { it[notificationAsked] = true } }
    suspend fun setAlertMode(value: AlertMode) { store.edit { it[alertMode] = value.name } }
    suspend fun setReminderStyle(value: ReminderStyle) { store.edit { it[reminderStyle] = value.name } }
    suspend fun setSnoozeMinutes(value: Int) { require(value in listOf(5,10,15)); store.edit { it[snoozeMinutes] = value } }
    suspend fun setSnoozeLimit(value: Int) { require(value in listOf(0,1,3)); store.edit { it[snoozeLimit] = value } }
    val flow = store.data.map {
        UserPreferences(it[onboarded] ?: false, it[name] ?: "Lumi", it[reduced] ?: false,
            it[dark] ?: false, it[reminders] ?: true, it[quietStart] ?: 22, it[quietEnd] ?: 8,
            AlertMode.parse(it[alertMode]), ReminderStyle.parse(it[reminderStyle]),
            (it[snoozeMinutes] ?: 5).takeIf { n -> n in listOf(5,10,15) } ?: 5,
            (it[snoozeLimit] ?: 0).takeIf { n -> n in listOf(0,1,3) } ?: 0)
    }
    suspend fun save(value: UserPreferences) {
        store.edit {
            it[onboarded] = value.onboarded; it[name] = value.name.trim().take(32).ifBlank { "Lumi" }
            it[reduced] = value.reducedMotion; it[dark] = value.darkMode; it[reminders] = value.reminders
            it[quietStart] = value.quietStart.coerceIn(0,23); it[quietEnd] = value.quietEnd.coerceIn(0,23)
            it[alertMode] = value.alertMode.name; it[reminderStyle] = value.reminderStyle.name
            it[snoozeMinutes] = value.snoozeMinutes.takeIf { n -> n in listOf(5,10,15) } ?: 5
            it[snoozeLimit] = value.snoozeLimit.takeIf { n -> n in listOf(0,1,3) } ?: 0
        }
    }
}
