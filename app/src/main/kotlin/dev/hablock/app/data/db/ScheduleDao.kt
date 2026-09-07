package dev.hablock.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM block_schedules ORDER BY blockId")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM block_schedules WHERE blockId = :blockId")
    suspend fun get(blockId: String): ScheduleEntity?

    @Upsert
    suspend fun upsert(schedule: ScheduleEntity)

    @Query("DELETE FROM block_schedules WHERE blockId = :blockId")
    suspend fun delete(blockId: String)
}
