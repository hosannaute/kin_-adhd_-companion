package app.kin.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.kinPreferences by preferencesDataStore("kin_preferences")
data class UserPreferences(
    val onboarded: Boolean = false, val name: String = "Lumi", val reducedMotion: Boolean = false,
    val darkMode: Boolean = false, val reminders: Boolean = true,
    val quietStart: Int = 22, val quietEnd: Int = 8
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
    val permissionSetupReviewed = store.data.map { it[permissionSetup] ?: false }
    val notificationPromptAttempted = store.data.map { it[notificationAsked] ?: false }
    suspend fun markPermissionSetupReviewed(reviewed: Boolean = true) { store.edit { it[permissionSetup] = reviewed } }
    suspend fun markNotificationPromptAttempted() { store.edit { it[notificationAsked] = true } }
    val flow = store.data.map {
        UserPreferences(it[onboarded] ?: false, it[name] ?: "Lumi", it[reduced] ?: false,
            it[dark] ?: false, it[reminders] ?: true, it[quietStart] ?: 22, it[quietEnd] ?: 8)
    }
    suspend fun save(value: UserPreferences) {
        store.edit {
            it[onboarded] = value.onboarded; it[name] = value.name.trim().take(32).ifBlank { "Lumi" }
            it[reduced] = value.reducedMotion; it[dark] = value.darkMode; it[reminders] = value.reminders
            it[quietStart] = value.quietStart.coerceIn(0,23); it[quietEnd] = value.quietEnd.coerceIn(0,23)
        }
    }
}
