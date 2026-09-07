package dev.hablock.app.data.db

import dev.hablock.app.domain.model.DailyBlockHistory
import dev.hablock.app.domain.model.DailyConditionHistory
import dev.hablock.app.domain.model.HistoryConditionKind
import dev.hablock.app.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val MAX_HISTORY_DAYS = 365

class RoomHistoryRepository(
    private val dao: HistoryDao,
) : HistoryRepository {
    override fun observe(blockId: String, limit: Int): Flow<List<DailyBlockHistory>> =
        dao.observeBlocks(blockId, checkedLimit(limit)).map(::withConditions)

    override suspend fun latest(blockId: String, limit: Int): List<DailyBlockHistory> =
        withConditions(dao.getBlocks(blockId, checkedLimit(limit)))

    override suspend fun save(summary: DailyBlockHistory) = dao.save(summary.toAggregate())

    override suspend fun importNew(summaries: List<DailyBlockHistory>): Int =
        dao.importNew(summaries.map(DailyBlockHistory::toAggregate))

    override suspend fun pruneBefore(oldestDayKey: String) = dao.pruneBefore(oldestDayKey)

    private suspend fun withConditions(blocks: List<DailyBlockHistoryEntity>): List<DailyBlockHistory> =
        blocks.map { block ->
            val conditions = dao.conditionsForDay(block.blockId, block.dayKey)
            block.toDomain(conditions)
        }

    private fun checkedLimit(limit: Int): Int {
        require(limit in 1..MAX_HISTORY_DAYS) { "History limit must be from 1 to $MAX_HISTORY_DAYS" }
        return limit
    }
}

private fun DailyBlockHistory.toAggregate(): DailyHistoryAggregate = DailyHistoryAggregate(
    block = DailyBlockHistoryEntity(
        blockId = blockId,
        dayKey = dayKey,
        blockName = blockName,
        conditionCount = conditionCount,
        metCount = metCount,
        thresholdN = thresholdN,
        unlockCount = unlockCount,
        emergencyUnlockUsed = emergencyUnlockUsed,
        scheduledActive = scheduledActive,
        finalizedAt = finalizedAtMillis,
    ),
    conditions = conditions.map { condition -> condition.toEntity(blockId, dayKey) },
)

private fun DailyConditionHistory.toEntity(blockId: String, dayKey: String) = DailyConditionHistoryEntity(
    blockId = blockId,
    dayKey = dayKey,
    conditionId = conditionId,
    kind = kind.toStorageValue(),
    label = label,
    goal = goal,
    required = required,
    progress = progress,
    met = met,
)

internal fun DailyBlockHistoryEntity.toDomain(
    conditions: List<DailyConditionHistoryEntity>,
): DailyBlockHistory = DailyBlockHistory(
    blockId = blockId,
    dayKey = dayKey,
    blockName = blockName,
    conditions = conditions.map(DailyConditionHistoryEntity::toDomain),
    conditionCount = conditionCount,
    metCount = metCount,
    thresholdN = thresholdN,
    unlockCount = unlockCount,
    emergencyUnlockUsed = emergencyUnlockUsed,
    scheduledActive = scheduledActive,
    finalizedAtMillis = finalizedAt,
)

private fun DailyConditionHistoryEntity.toDomain() = DailyConditionHistory(
    conditionId = conditionId,
    kind = kind.toDomainKind(),
    label = label,
    goal = goal,
    required = required,
    progress = progress,
    met = met,
)

private fun HistoryConditionKind.toStorageValue(): String = when (this) {
    HistoryConditionKind.APP_USAGE -> ConditionKind.APP_USAGE
    HistoryConditionKind.STEPS -> ConditionKind.STEPS
    HistoryConditionKind.EXERCISE -> ConditionKind.EXERCISE
    HistoryConditionKind.MEDITATION -> ConditionKind.MEDITATION
}

private fun String.toDomainKind(): HistoryConditionKind = when (this) {
    ConditionKind.APP_USAGE -> HistoryConditionKind.APP_USAGE
    ConditionKind.STEPS -> HistoryConditionKind.STEPS
    ConditionKind.EXERCISE -> HistoryConditionKind.EXERCISE
    ConditionKind.MEDITATION -> HistoryConditionKind.MEDITATION
    else -> error("Unknown history condition kind '$this'")
}
