package dev.hablock.app.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class BlockAggregate(
    @Embedded val block: BlockEntity,
    @Relation(parentColumn = "id", entityColumn = "blockId")
    val apps: List<BlockAppEntity>,
    @Relation(parentColumn = "id", entityColumn = "blockId")
    val conditions: List<ConditionEntity>,
    @Relation(parentColumn = "id", entityColumn = "blockId")
    val schedules: List<ScheduleEntity> = emptyList(),
)

data class BlockDayStateAggregate(
    @Embedded val state: BlockDayStateEntity,
    @Relation(parentColumn = "blockId", entityColumn = "blockId")
    val requirements: List<RequirementEntity>,
)

data class DailyHistoryAggregate(
    val block: DailyBlockHistoryEntity,
    val conditions: List<DailyConditionHistoryEntity>,
)

data class HistoryKeyRow(
    val blockId: String,
    val dayKey: String,
)
