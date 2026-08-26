package dev.hablock.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import dev.hablock.app.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")
private val RELINQUISH_DEADLINE_KEY = longPreferencesKey("relinquish_deadline")

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

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
}
