package dev.hablock.app.data.db

import androidx.room.withTransaction
import dev.hablock.app.domain.archive.ArchiveDestination
import dev.hablock.app.domain.archive.ArchiveHistoryKey
import dev.hablock.app.domain.archive.ArchiveSettings
import dev.hablock.app.domain.archive.ArchiveStore
import dev.hablock.app.domain.archive.ArchiveStoreImportResult
import dev.hablock.app.domain.archive.ArchiveStoreSnapshot
import dev.hablock.app.domain.archive.toDomain
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.DailyBlockHistory
import dev.hablock.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/** Coordinates additive Room writes in one transaction; DataStore globals follow only after commit. */
class RoomArchiveStore(
    private val database: HablockDatabase,
    private val settingsRepository: SettingsRepository,
) : ArchiveStore {
    private val blocks = RoomBlockRepository(database.blockDao())
    private val history = RoomHistoryRepository(database.historyDao())

    override suspend fun snapshot(maxHistoryDaysPerBlock: Int): ArchiveStoreSnapshot {
        val room = database.withTransaction {
            RoomSnapshot(
                blocks = blocks.current(),
                history = latestHistory(maxHistoryDaysPerBlock),
            )
        }
        return ArchiveStoreSnapshot(
            blocks = room.blocks,
            history = room.history,
            overlapPolicy = settingsRepository.overlapPolicy.first(),
            dayBoundaryMinutes = settingsRepository.dayBoundaryMinutes.first(),
        )
    }

    override suspend fun destination(): ArchiveDestination = database.withTransaction {
        ArchiveDestination(
            blockIds = blocks.current().mapTo(mutableSetOf(), Block::id),
            historyKeys = database.archiveDao().getAllHistoryKeys().mapTo(mutableSetOf()) {
                ArchiveHistoryKey(it.blockId, it.dayKey)
            },
        )
    }

    override suspend fun importAdditive(
        blocks: List<Block>,
        history: List<DailyBlockHistory>,
        settings: ArchiveSettings,
    ): ArchiveStoreImportResult {
        val roomResult = database.withTransaction {
            val existingIds = this@RoomArchiveStore.blocks.current().mapTo(mutableSetOf(), Block::id)
            val destinationWasEmpty = existingIds.isEmpty()
            val missing = blocks.filter { it.id !in existingIds }
            missing.forEach { this@RoomArchiveStore.blocks.upsert(it) }
            val insertedHistory = this@RoomArchiveStore.history.importNew(history)
            RoomImportResult(missing.size, insertedHistory, destinationWasEmpty)
        }

        if (roomResult.destinationWasEmpty) {
            settingsRepository.setOverlapPolicy(settings.overlapPolicy.toDomain())
            settingsRepository.setDayBoundaryMinutes(settings.dayBoundaryMinutes)
        }
        return ArchiveStoreImportResult(
            insertedBlocks = roomResult.insertedBlocks,
            insertedHistory = roomResult.insertedHistory,
            restoredGlobalSettings = roomResult.destinationWasEmpty,
        )
    }

    private suspend fun latestHistory(limitPerBlock: Int): List<DailyBlockHistory> {
        require(limitPerBlock in 1..365) { "History limit must be from 1 to 365" }
        return database.archiveDao().getAllHistoryBlocks()
            .groupBy(DailyBlockHistoryEntity::blockId)
            .toSortedMap()
            .values
            .flatMap { rows -> rows.take(limitPerBlock) }
            .map { row ->
                row.toDomain(database.historyDao().conditionsForDay(row.blockId, row.dayKey))
            }
    }
}

private data class RoomImportResult(
    val insertedBlocks: Int,
    val insertedHistory: Int,
    val destinationWasEmpty: Boolean,
)

private data class RoomSnapshot(
    val blocks: List<Block>,
    val history: List<DailyBlockHistory>,
)
