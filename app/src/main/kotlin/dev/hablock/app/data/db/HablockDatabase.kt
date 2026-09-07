package dev.hablock.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BlockEntity::class,
        BlockAppEntity::class,
        ConditionEntity::class,
        ScheduleEntity::class,
        BlockDayStateEntity::class,
        RequirementEntity::class,
        DailyBlockHistoryEntity::class,
        DailyConditionHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HablockDatabase : RoomDatabase() {
    abstract fun blockDao(): BlockDao
    abstract fun gateStateDao(): GateStateDao
    abstract fun historyDao(): HistoryDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun archiveDao(): ArchiveDao
}
