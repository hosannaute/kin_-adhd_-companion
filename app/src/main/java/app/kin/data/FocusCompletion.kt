package app.kin.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.completionStore by preferencesDataStore("focus_completion")
class FocusCompletion(context: Context) {
    private val store = context.completionStore
    private val pending = longPreferencesKey("pending_session")
    val flow = store.data.map { it[pending] ?: 0 }
    suspend fun register(id: Long) { store.edit { it[pending] = id } }
    suspend fun dismiss(id: Long) { store.edit { if (it[pending] == id) it.remove(pending) } }
    suspend fun clear() { store.edit { it.remove(pending) } }
}
