package app.kin.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.companionModeStore by preferencesDataStore("companion_mode")
data class CompanionModeOptions(
    val monitorUsage: Boolean = false,
    val selectedApps: Set<String> = emptySet(),
    val thresholdMinutes: Int = 10,
    val x: Int = 16, val y: Int = 160,
    val scrollDetectionEnabled: Boolean = true,
    val scrollThresholdMinutes: Int = 10,
    val scrollAllApps: Boolean = true
)

class CompanionModePreferences(context: Context) {
    private val store = context.companionModeStore
    private val monitor = booleanPreferencesKey("monitor_usage")
    private val packages = stringSetPreferencesKey("selected_apps")
    private val threshold = intPreferencesKey("threshold_minutes")
    private val x = intPreferencesKey("overlay_x")
    private val y = intPreferencesKey("overlay_y")
    private val scrollDetection = booleanPreferencesKey("scroll_detection_enabled")
    private val scrollThreshold = intPreferencesKey("scroll_threshold_minutes")
    private val scrollAllApps = booleanPreferencesKey("scroll_all_apps")
    val flow = store.data.map {
        CompanionModeOptions(
            monitorUsage = it[monitor] ?: false,
            selectedApps = it[packages] ?: emptySet(),
            thresholdMinutes = (it[threshold] ?: 10).coerceIn(1, 180),
            x = it[x] ?: 16,
            y = it[y] ?: 160,
            scrollDetectionEnabled = it[scrollDetection] ?: true,
            scrollThresholdMinutes = (it[scrollThreshold] ?: 10).coerceIn(1, 180),
            scrollAllApps = it[scrollAllApps] ?: true
        )
    }
    suspend fun monitoring(enabled: Boolean) { store.edit { it[monitor] = enabled } }
    suspend fun select(packageName: String, selected: Boolean) { store.edit {
        val current = it[packages] ?: emptySet()
        it[packages] = if (selected) current + packageName else current - packageName
    } }
    suspend fun threshold(minutes: Int) { store.edit { it[threshold] = minutes.coerceIn(1, 180) } }
    suspend fun position(left: Int, top: Int) { store.edit { it[x] = left; it[y] = top } }
    suspend fun scrollDetection(enabled: Boolean) { store.edit { it[scrollDetection] = enabled } }
    suspend fun scrollThreshold(minutes: Int) { store.edit { it[scrollThreshold] = minutes.coerceIn(1, 180) } }
    suspend fun scrollAllApps(all: Boolean) { store.edit { it[scrollAllApps] = all } }
    suspend fun restore(value: CompanionModeOptions) { store.edit {
        it[monitor] = false; it[packages] = value.selectedApps; it[threshold] = value.thresholdMinutes
        it[x] = value.x; it[y] = value.y
        it[scrollDetection] = value.scrollDetectionEnabled
        it[scrollThreshold] = value.scrollThresholdMinutes
        it[scrollAllApps] = value.scrollAllApps
    } }
}
