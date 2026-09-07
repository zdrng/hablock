package dev.hablock.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GateStateDao {
    @Transaction
    @Query("SELECT * FROM block_day_states ORDER BY blockId")
    abstract fun observeAll(): Flow<List<BlockDayStateAggregate>>

    @Transaction
    @Query("SELECT * FROM block_day_states WHERE blockId = :blockId")
    abstract suspend fun get(blockId: String): BlockDayStateAggregate?

    @Upsert
    protected abstract suspend fun upsertState(state: BlockDayStateEntity)

    @Query("DELETE FROM day_state_requirements WHERE blockId = :blockId")
    protected abstract suspend fun deleteRequirements(blockId: String)

    @Insert
    protected abstract suspend fun insertRequirements(requirements: List<RequirementEntity>)

    @Transaction
    open suspend fun save(aggregate: BlockDayStateAggregate) {
        upsertState(aggregate.state)
        deleteRequirements(aggregate.state.blockId)
        if (aggregate.requirements.isNotEmpty()) insertRequirements(aggregate.requirements)
    }

    @Query("DELETE FROM block_day_states WHERE blockId = :blockId")
    abstract suspend fun delete(blockId: String)

    /** Clears only live gate state. Finalized history is intentionally independent. */
    @Query("DELETE FROM block_day_states")
    abstract suspend fun clearAll()
}
