package app.kin.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.timerStore by preferencesDataStore("timer_options")
data class TimerOptions(val dockRight: Boolean = true, val verticalFraction: Float = .25f,
                        val breakMinutes: Int = 5, val focusMinutes: Int = 25, val taskId: Long = 0, val goalId: Long = 0)

class TimerPreferences(context: Context) {
    private val store = context.timerStore
    private val right = booleanPreferencesKey("dock_right")
    private val fraction = floatPreferencesKey("vertical_fraction")
    private val rest = intPreferencesKey("break_minutes")
    private val focus = intPreferencesKey("focus_minutes")
    private val task = longPreferencesKey("recent_task")
    private val goal = longPreferencesKey("recent_goal")
    val flow = store.data.map { TimerOptions(it[right] ?: true, (it[fraction] ?: .25f).coerceIn(0f,1f),
        (it[rest] ?: 5).coerceIn(1,180), (it[focus] ?: 25).coerceIn(1,180), it[task] ?: 0, it[goal] ?: 0) }
    suspend fun position(dockRight: Boolean, verticalFraction: Float) { store.edit {
        it[right] = dockRight; it[fraction] = verticalFraction.coerceIn(0f,1f)
    } }
    suspend fun breakChoice(minutes: Int, taskId: Long, goalId: Long?) { store.edit {
        it[rest] = minutes.coerceIn(1,180); it[task] = taskId; it[goal] = goalId ?: 0
    } }
    suspend fun focusChoice(minutes: Int, taskId: Long?) { store.edit {
        it[focus] = minutes.coerceIn(1,180); if (taskId != null) it[task] = taskId
    } }
}
