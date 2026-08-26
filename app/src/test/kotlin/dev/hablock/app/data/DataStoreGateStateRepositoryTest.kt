package dev.hablock.app.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.hablock.app.domain.model.BlockDayState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

private val DAY_STATES_KEY = stringPreferencesKey("day_states")
private val QUARANTINE_KEY = stringPreferencesKey("day_states_quarantine")

private fun dayState(blockId: String) = BlockDayState(
    blockId = blockId,
    dayKey = "2026-08-26",
    requiredNow = mapOf("c1" to 10.0),
)

class DataStoreGateStateRepositoryTest {

    private val dataStore = FakePreferencesDataStore()
    private val repository = DataStoreGateStateRepository(dataStore, Json { ignoreUnknownKeys = true })

    @Test
    fun `save get delete clearAll round-trip`() = runTest {
        val a = dayState("a")
        val b = dayState("b")
        repository.save(a)
        repository.save(b)
        assertEquals(a, repository.get("a"))

        repository.delete("a")
        assertNull(repository.get("a"))
        assertEquals(b, repository.get("b"))

        repository.clearAll()
        assertNull(repository.get("b"))
    }

    @Test
    fun `corrupt json reads as empty without crashing`() = runTest {
        dataStore.edit { it[DAY_STATES_KEY] = "[oops" }
        assertNull(repository.get("a"))
        assertEquals(emptyMap(), repository.dayStates.first())
    }

    @Test
    fun `write over corrupt json quarantines the original`() = runTest {
        dataStore.edit { it[DAY_STATES_KEY] = "[oops" }
        val state = dayState("fresh")
        repository.save(state)

        val prefs = dataStore.data.first()
        assertEquals("[oops", prefs[QUARANTINE_KEY])
        assertEquals(state, repository.get("fresh"))
    }
}
