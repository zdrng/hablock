package dev.hablock.app.data.db

import androidx.room.Dao
import androidx.room.Query

/** Archive-only whole-database reads, kept out of operational repository contracts. */
@Dao
interface ArchiveDao {
    @Query("SELECT * FROM daily_block_history ORDER BY blockId, dayKey DESC")
    suspend fun getAllHistoryBlocks(): List<DailyBlockHistoryEntity>

    @Query("SELECT blockId, dayKey FROM daily_block_history")
    suspend fun getAllHistoryKeys(): List<HistoryKeyRow>
}
