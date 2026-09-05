package dev.hablock.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.hablock.app.domain.model.EmergencyUnlockState
import dev.hablock.app.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")
private val RELINQUISH_DEADLINE_KEY = longPreferencesKey("relinquish_deadline")
private val EMERGENCY_UNLOCKS_KEY = stringPreferencesKey("emergency_unlocks")

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override val onboardingDone: Flow<Boolean> = dataStore.data
        .map { it[ONBOARDING_DONE_KEY] ?: false }
        .catch { cause ->
            if (cause is IOException) emit(false) else throw cause
        }

    override suspend fun setOnboardingDone() {
        dataStore.edit { prefs -> prefs[ONBOARDING_DONE_KEY] = true }
    }

    override val relinquishDeadlineMillis: Flow<Long?> = dataStore.data
        .map { it[RELINQUISH_DEADLINE_KEY] }
        .catch { cause ->
            if (cause is IOException) emit(null) else throw cause
        }

    override suspend fun setRelinquishDeadline(millis: Long?) {
        dataStore.edit { prefs ->
            if (millis == null) prefs.remove(RELINQUISH_DEADLINE_KEY) else prefs[RELINQUISH_DEADLINE_KEY] = millis
        }
    }

    override val emergencyUnlocks: Flow<EmergencyUnlockState> = dataStore.data
        .map { prefs ->
            prefs[EMERGENCY_UNLOCKS_KEY]?.let {
                runCatching { json.decodeFromString(EmergencyUnlockState.serializer(), it) }.getOrNull()
            } ?: EmergencyUnlockState()
        }
        .catch { cause ->
            if (cause is IOException) emit(EmergencyUnlockState()) else throw cause
        }

    override suspend fun setEmergencyUnlocks(state: EmergencyUnlockState) {
        dataStore.edit { prefs ->
            prefs[EMERGENCY_UNLOCKS_KEY] = json.encodeToString(EmergencyUnlockState.serializer(), state)
        }
    }
}
