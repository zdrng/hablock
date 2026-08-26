package dev.hablock.app.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.hablock.app.domain.service.testBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

private val BLOCKS_KEY = stringPreferencesKey("blocks")
private val QUARANTINE_KEY = stringPreferencesKey("blocks_quarantine")

class DataStoreBlockRepositoryTest {

    private val dataStore = FakePreferencesDataStore()
    private val repository = DataStoreBlockRepository(dataStore, Json { ignoreUnknownKeys = true })

    @Test
    fun `upsert and delete round-trip`() = runTest {
        val a = testBlock(id = "a")
        val b = testBlock(id = "b")
        repository.upsert(a)
        repository.upsert(b)
        assertEquals(listOf(a, b), repository.current())

        repository.upsert(a.copy(name = "renamed"))
        assertEquals("renamed", repository.current().first { it.id == "a" }.name)

        repository.delete("a")
        assertEquals(listOf(b), repository.current())
    }

    @Test
    fun `corrupt json reads as empty without crashing`() = runTest {
        dataStore.edit { it[BLOCKS_KEY] = "{not json" }
        assertEquals(emptyList(), repository.current())
    }

    @Test
    fun `write over corrupt json quarantines the original`() = runTest {
        dataStore.edit { it[BLOCKS_KEY] = "{not json" }
        val block = testBlock(id = "fresh")
        repository.upsert(block)

        val prefs = dataStore.data.first()
        assertEquals("{not json", prefs[QUARANTINE_KEY])
        assertEquals(listOf(block), repository.current())
    }

    @Test
    fun `write over blank store does not quarantine`() = runTest {
        repository.upsert(testBlock())
        assertNull(dataStore.data.first()[QUARANTINE_KEY])
    }
}
