package dev.hablock.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HistoryDao {
    @Query(
        """
        SELECT * FROM daily_block_history
        WHERE blockId = :blockId
        ORDER BY dayKey DESC
        LIMIT :limit
        """,
    )
    abstract fun observeBlocks(blockId: String, limit: Int = 365): Flow<List<DailyBlockHistoryEntity>>

    @Query(
        """
        SELECT * FROM daily_block_history
        WHERE blockId = :blockId
        ORDER BY dayKey DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun getBlocks(blockId: String, limit: Int = 365): List<DailyBlockHistoryEntity>

    @Query(
        """
        SELECT * FROM daily_condition_history
        WHERE blockId = :blockId AND dayKey = :dayKey
        ORDER BY conditionId
        """,
    )
    abstract suspend fun conditionsForDay(
        blockId: String,
        dayKey: String,
    ): List<DailyConditionHistoryEntity>

    @Upsert
    protected abstract suspend fun upsertBlock(block: DailyBlockHistoryEntity)

    @Upsert
    protected abstract suspend fun upsertConditions(conditions: List<DailyConditionHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertBlockIfAbsent(block: DailyBlockHistoryEntity): Long

    @Insert
    protected abstract suspend fun insertConditions(conditions: List<DailyConditionHistoryEntity>)

    @Query("DELETE FROM daily_condition_history WHERE blockId = :blockId AND dayKey = :dayKey")
    protected abstract suspend fun deleteConditions(blockId: String, dayKey: String)

    @Transaction
    open suspend fun save(aggregate: DailyHistoryAggregate) {
        validate(aggregate)
        upsertBlock(aggregate.block)
        deleteConditions(aggregate.block.blockId, aggregate.block.dayKey)
        if (aggregate.conditions.isNotEmpty()) upsertConditions(aggregate.conditions)
    }

    /** Additive import: an existing block/day aggregate is left completely unchanged. */
    @Transaction
    open suspend fun importNew(aggregates: List<DailyHistoryAggregate>): Int {
        var inserted = 0
        aggregates.forEach { aggregate ->
            validate(aggregate)
            if (insertBlockIfAbsent(aggregate.block) != -1L) {
                if (aggregate.conditions.isNotEmpty()) insertConditions(aggregate.conditions)
                inserted++
            }
        }
        return inserted
    }

    /** Call after finalization with the oldest retained ISO day key. */
    @Query("DELETE FROM daily_block_history WHERE dayKey < :oldestDayKey")
    abstract suspend fun pruneBefore(oldestDayKey: String)

    private fun validate(aggregate: DailyHistoryAggregate) {
        require(aggregate.conditions.all {
            it.blockId == aggregate.block.blockId && it.dayKey == aggregate.block.dayKey
        }) { "History condition keys must match their block history row" }
    }
}
