package dev.hablock.app.data

import android.util.Log
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
private val BLOCKS_QUARANTINE_KEY = stringPreferencesKey("blocks_quarantine")
private val BLOCK_LIST_SERIALIZER = ListSerializer(Block.serializer())
private const val TAG = "Hablock"

class DataStoreBlockRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : BlockRepository {

    override val blocks: Flow<List<Block>> = dataStore.data
        .map { decodeOrNull(it[BLOCKS_KEY]) ?: emptyList() }
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
            val raw = prefs[BLOCKS_KEY]
            val current = decodeOrNull(raw)
            // A corrupt store must never be silently replaced by empty — keep the bytes recoverable.
            if (current == null && raw != null) prefs[BLOCKS_QUARANTINE_KEY] = raw
            val updated = transform(current ?: emptyList())
            prefs[BLOCKS_KEY] = json.encodeToString(BLOCK_LIST_SERIALIZER, updated)
        }
    }

    private fun decodeOrNull(raw: String?): List<Block>? {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(BLOCK_LIST_SERIALIZER, raw) }
            .onFailure { Log.e(TAG, "blocks decode failed", it) }
            .getOrNull()
    }
}
