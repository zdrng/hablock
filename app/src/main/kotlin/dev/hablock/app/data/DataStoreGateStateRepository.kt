package dev.hablock.app.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.hablock.app.domain.model.BlockDayState
import dev.hablock.app.domain.repository.GateStateRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val DAY_STATES_KEY = stringPreferencesKey("day_states")
private val DAY_STATES_QUARANTINE_KEY = stringPreferencesKey("day_states_quarantine")
private val DAY_STATE_MAP_SERIALIZER = MapSerializer(String.serializer(), BlockDayState.serializer())
private const val TAG = "Hablock"

class DataStoreGateStateRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : GateStateRepository {

    override val dayStates: Flow<Map<String, BlockDayState>> = dataStore.data
        .map { decodeOrNull(it[DAY_STATES_KEY]) ?: emptyMap() }
        .catch { cause ->
            if (cause is IOException) emit(emptyMap()) else throw cause
        }

    override suspend fun get(blockId: String): BlockDayState? = dayStates.first()[blockId]

    override suspend fun save(state: BlockDayState) = update { states ->
        states + (state.blockId to state)
    }

    override suspend fun delete(blockId: String) = update { states -> states - blockId }

    override suspend fun clearAll() {
        dataStore.edit { prefs -> prefs.remove(DAY_STATES_KEY) }
    }

    private suspend fun update(transform: (Map<String, BlockDayState>) -> Map<String, BlockDayState>) {
        dataStore.edit { prefs ->
            val raw = prefs[DAY_STATES_KEY]
            val current = decodeOrNull(raw)
            // A corrupt store must never be silently replaced by empty — keep the bytes recoverable.
            if (current == null && raw != null) prefs[DAY_STATES_QUARANTINE_KEY] = raw
            val updated = transform(current ?: emptyMap())
            prefs[DAY_STATES_KEY] = json.encodeToString(DAY_STATE_MAP_SERIALIZER, updated)
        }
    }

    private fun decodeOrNull(raw: String?): Map<String, BlockDayState>? {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(DAY_STATE_MAP_SERIALIZER, raw) }
            .onFailure { Log.e(TAG, "day states decode failed", it) }
            .getOrNull()
    }
}
