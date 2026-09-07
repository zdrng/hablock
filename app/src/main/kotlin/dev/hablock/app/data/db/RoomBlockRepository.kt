package dev.hablock.app.data.db

import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.BlockSchedule
import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.LockType
import dev.hablock.app.domain.model.Weekday
import dev.hablock.app.domain.repository.BlockRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBlockRepository(
    private val dao: BlockDao,
) : BlockRepository {
    override val blocks: Flow<List<Block>> = dao.observeAll().map { rows -> rows.map(BlockAggregate::toDomain) }

    override suspend fun current(): List<Block> = dao.getAll().map(BlockAggregate::toDomain)

    override suspend fun upsert(block: Block) = dao.upsert(block.toAggregate())

    override suspend fun delete(blockId: String) = dao.delete(blockId)
}

private fun Block.toAggregate(): BlockAggregate {
    val appKeys = blockedPackages + blockedLabels.keys
    return BlockAggregate(
        block = BlockEntity(
            id = id,
            position = 0, // The DAO preserves an existing position or assigns the next one.
            name = name,
            thresholdN = thresholdN,
            incrementPct = incrementPct,
            unlockDurationMinutes = unlockDurationMinutes,
            enabled = enabled,
            blockedUntil = blockedUntil,
            lockType = lockType?.toStorageValue(),
            lockPasswordHash = lockPasswordHash,
        ),
        apps = appKeys.map { packageName ->
            BlockAppEntity(
                blockId = id,
                packageName = packageName,
                blocked = packageName in blockedPackages,
                label = blockedLabels[packageName],
            )
        },
        conditions = conditions.mapIndexed { position, condition -> condition.toEntity(id, position) },
        schedules = schedule?.toEntity(id)?.let(::listOf).orEmpty(),
    )
}

private fun BlockAggregate.toDomain(): Block = Block(
    id = block.id,
    name = block.name,
    blockedPackages = apps.asSequence().filter { it.blocked }.map { it.packageName }.toSet(),
    blockedLabels = apps.asSequence().mapNotNull { app -> app.label?.let { app.packageName to it } }.toMap(),
    conditions = conditions.sortedBy(ConditionEntity::position).map(ConditionEntity::toDomain),
    thresholdN = block.thresholdN,
    incrementPct = block.incrementPct,
    unlockDurationMinutes = block.unlockDurationMinutes,
    enabled = block.enabled,
    blockedUntil = block.blockedUntil,
    lockType = block.lockType?.toLockType(),
    lockPasswordHash = block.lockPasswordHash,
    schedule = schedules.singleOrNull()?.takeIf { it.enabled }?.toDomain(),
)

private fun BlockSchedule.toEntity(blockId: String): ScheduleEntity = ScheduleEntity(
    blockId = blockId,
    enabled = true,
    weekdays = weekdays.fold(0) { mask, day -> mask or (1 shl day.ordinal) },
    startMinute = startMinute,
    endMinute = endMinute,
)

private fun ScheduleEntity.toDomain(): BlockSchedule = BlockSchedule(
    weekdays = Weekday.entries.filterTo(mutableSetOf()) { day -> weekdays and (1 shl day.ordinal) != 0 },
    startMinute = startMinute,
    endMinute = endMinute,
)

private fun Condition.toEntity(blockId: String, position: Int): ConditionEntity = when (this) {
    is Condition.AppUsage -> ConditionEntity(
        blockId = blockId,
        conditionId = id,
        position = position,
        kind = ConditionKind.APP_USAGE,
        goal = goal,
        packageName = packageName,
        appLabel = appLabel,
    )
    is Condition.Steps -> simpleEntity(blockId, position, ConditionKind.STEPS)
    is Condition.Exercise -> simpleEntity(blockId, position, ConditionKind.EXERCISE)
    is Condition.Meditation -> simpleEntity(blockId, position, ConditionKind.MEDITATION)
}

private fun Condition.simpleEntity(blockId: String, position: Int, kind: String) = ConditionEntity(
    blockId = blockId,
    conditionId = id,
    position = position,
    kind = kind,
    goal = goal,
    packageName = null,
    appLabel = null,
)

private fun ConditionEntity.toDomain(): Condition = when (kind) {
    ConditionKind.APP_USAGE -> Condition.AppUsage(
        id = conditionId,
        goal = goal,
        packageName = requireNotNull(packageName) { "App-usage condition $conditionId has no package" },
        appLabel = requireNotNull(appLabel) { "App-usage condition $conditionId has no label" },
    )
    ConditionKind.STEPS -> Condition.Steps(conditionId, goal)
    ConditionKind.EXERCISE -> Condition.Exercise(conditionId, goal)
    ConditionKind.MEDITATION -> Condition.Meditation(conditionId, goal)
    else -> error("Unknown condition kind '$kind'")
}

private fun LockType.toStorageValue(): String = when (this) {
    LockType.DURATION -> "duration"
    LockType.PASSWORD -> "password"
}

private fun String.toLockType(): LockType = when (this) {
    "duration" -> LockType.DURATION
    "password" -> LockType.PASSWORD
    else -> error("Unknown lock type '$this'")
}
