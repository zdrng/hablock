package dev.hablock.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BlockDao {
    @Transaction
    @Query("SELECT * FROM blocks ORDER BY position, id")
    abstract fun observeAll(): Flow<List<BlockAggregate>>

    @Transaction
    @Query("SELECT * FROM blocks ORDER BY position, id")
    abstract suspend fun getAll(): List<BlockAggregate>

    @Query("SELECT position FROM blocks WHERE id = :blockId")
    protected abstract suspend fun positionOf(blockId: String): Long?

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM blocks")
    protected abstract suspend fun nextPosition(): Long

    @Upsert
    protected abstract suspend fun upsertBlock(block: BlockEntity)

    @Query("DELETE FROM block_apps WHERE blockId = :blockId")
    protected abstract suspend fun deleteApps(blockId: String)

    @Query("DELETE FROM conditions WHERE blockId = :blockId")
    protected abstract suspend fun deleteConditions(blockId: String)

    @Query("DELETE FROM block_schedules WHERE blockId = :blockId")
    protected abstract suspend fun deleteSchedule(blockId: String)

    @Insert
    protected abstract suspend fun insertApps(apps: List<BlockAppEntity>)

    @Insert
    protected abstract suspend fun insertConditions(conditions: List<ConditionEntity>)

    @Upsert
    protected abstract suspend fun upsertSchedule(schedule: ScheduleEntity)

    /** Replaces the owned collections without using SQLite REPLACE on the parent. */
    @Transaction
    open suspend fun upsert(aggregate: BlockAggregate) {
        val position = positionOf(aggregate.block.id) ?: nextPosition()
        upsertBlock(aggregate.block.copy(position = position))
        deleteApps(aggregate.block.id)
        deleteConditions(aggregate.block.id)
        deleteSchedule(aggregate.block.id)
        if (aggregate.apps.isNotEmpty()) insertApps(aggregate.apps)
        if (aggregate.conditions.isNotEmpty()) insertConditions(aggregate.conditions)
        require(aggregate.schedules.size <= 1) { "A block can have at most one schedule" }
        aggregate.schedules.singleOrNull()?.let { upsertSchedule(it) }
    }

    @Query("DELETE FROM blocks WHERE id = :blockId")
    abstract suspend fun delete(blockId: String)
}
