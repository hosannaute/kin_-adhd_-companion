package app.kin.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.companionModeStore by preferencesDataStore("companion_mode")
data class CompanionModeOptions(
    val monitorUsage: Boolean = false,
    val selectedApps: Set<String> = emptySet(),
    val thresholdMinutes: Int = 30,
    val x: Int = 16, val y: Int = 160
)

class CompanionModePreferences(context: Context) {
    private val store = context.companionModeStore
    private val monitor = booleanPreferencesKey("monitor_usage")
    private val packages = stringSetPreferencesKey("selected_apps")
    private val threshold = intPreferencesKey("threshold_minutes")
    private val x = intPreferencesKey("overlay_x")
    private val y = intPreferencesKey("overlay_y")
    val flow = store.data.map {
        CompanionModeOptions(it[monitor] ?: false, it[packages] ?: emptySet(),
            (it[threshold] ?: 30).coerceIn(1,180), it[x] ?: 16, it[y] ?: 160)
    }
    suspend fun monitoring(enabled: Boolean) { store.edit { it[monitor] = enabled } }
    suspend fun select(packageName: String, selected: Boolean) { store.edit {
        val current = it[packages] ?: emptySet()
        it[packages] = if (selected) current + packageName else current - packageName
    } }
    suspend fun threshold(minutes: Int) { store.edit { it[threshold] = minutes.coerceIn(1,180) } }
    suspend fun position(left: Int, top: Int) { store.edit { it[x] = left; it[y] = top } }
    suspend fun restore(value: CompanionModeOptions) { store.edit {
        it[monitor] = false; it[packages] = value.selectedApps; it[threshold] = value.thresholdMinutes
        it[x] = value.x; it[y] = value.y
    } }
}
