package dev.hablock.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.repository.BlockRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val BLOCKS_KEY = stringPreferencesKey("blocks")
private val BLOCK_LIST_SERIALIZER = ListSerializer(Block.serializer())

class DataStoreBlockRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : BlockRepository {

    override val blocks: Flow<List<Block>> = dataStore.data
        .map { decode(it[BLOCKS_KEY]) }
        .catch { cause ->
            if (cause is IOException) emit(emptyList()) else throw cause
        }

    override suspend fun current(): List<Block> = blocks.first()

    override suspend fun upsert(block: Block) = update { blocks ->
        if (blocks.any { it.id == block.id }) {
            blocks.map { if (it.id == block.id) block else it }
        } else {
            blocks + block
        }
    }

    override suspend fun delete(blockId: String) = update { blocks ->
        blocks.filterNot { it.id == blockId }
    }

    private suspend fun update(transform: (List<Block>) -> List<Block>) {
        dataStore.edit { prefs ->
            val updated = transform(decode(prefs[BLOCKS_KEY]))
            prefs[BLOCKS_KEY] = json.encodeToString(BLOCK_LIST_SERIALIZER, updated)
        }
    }

    private fun decode(raw: String?): List<Block> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(BLOCK_LIST_SERIALIZER, raw) }.getOrDefault(emptyList())
    }
}
