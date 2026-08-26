package dev.hablock.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.hablock.app.domain.enforcement.SuspensionStore
import kotlinx.coroutines.flow.first

private val SUSPENDED_PACKAGES_KEY = stringSetPreferencesKey("suspended_packages")

class DataStoreSuspensionStore(private val dataStore: DataStore<Preferences>) : SuspensionStore {

    override suspend fun suspended(): Set<String> =
        dataStore.data.first()[SUSPENDED_PACKAGES_KEY] ?: emptySet()

    override suspend fun setSuspended(packages: Set<String>) {
        dataStore.edit { prefs ->
            if (packages.isEmpty()) prefs.remove(SUSPENDED_PACKAGES_KEY) else prefs[SUSPENDED_PACKAGES_KEY] = packages
        }
    }
}
