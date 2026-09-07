package dev.hablock.app.data.db

import dev.hablock.app.domain.model.DailyBlockHistory
import dev.hablock.app.domain.model.DailyConditionHistory
import dev.hablock.app.domain.model.HistoryConditionKind
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest

class RoomHistoryRepositoryTest {

    private val dao = FakeHistoryDao()
    private val repository = RoomHistoryRepository(dao)

    @Test
    fun `save is idempotent and replaces one block day as an aggregate`() = runTest {
        val first = summary(dayKey = "2026-08-20")

        repository.save(first)
        repository.save(first)

        assertEquals(listOf(first), repository.latest("b1"))

        val replacement = summary(
            dayKey = first.dayKey,
            blockName = "Renamed snapshot",
            conditions = listOf(condition("exercise", HistoryConditionKind.EXERCISE, met = false)),
        )
        repository.save(replacement)

        assertEquals(listOf(replacement), repository.latest("b1"))
    }

    @Test
    fun `additive import skips an existing aggregate and inserts a new one`() = runTest {
        val existing = summary(dayKey = "2026-08-20", blockName = "Local snapshot")
        repository.save(existing)
        val importedReplacement = summary(dayKey = existing.dayKey, blockName = "Imported snapshot")
        val importedNew = summary(dayKey = "2026-08-21", blockName = "Imported new day")

        assertEquals(1, repository.importNew(listOf(importedReplacement, importedNew)))
        assertEquals(
            listOf(importedNew, existing),
            repository.latest("b1"),
        )
    }

    @Test
    fun `query and observation return at most the latest 365 days per block`() = runTest {
        val start = LocalDate.parse("2025-01-01")
        repeat(370) { offset ->
            repository.save(summary(dayKey = start.plusDays(offset.toLong()).toString()))
        }
        repository.save(summary(blockId = "b2", dayKey = "2026-01-01"))

        val queried = repository.latest("b1")
        val observed = repository.observe("b1").first()

        assertEquals(365, queried.size)
        assertEquals(start.plusDays(369).toString(), queried.first().dayKey)
        assertEquals(start.plusDays(5).toString(), queried.last().dayKey)
        assertEquals(queried, observed)
        assertFailsWith<IllegalArgumentException> { repository.latest("b1", 366) }
    }

    @Test
    fun `pruning uses the Hablock day key across blocks`() = runTest {
        repository.save(summary(blockId = "b1", dayKey = "2026-08-19"))
        repository.save(summary(blockId = "b2", dayKey = "2026-08-19"))
        val boundary = summary(blockId = "b1", dayKey = "2026-08-20")
        val newer = summary(blockId = "b2", dayKey = "2026-08-21")
        repository.save(boundary)
        repository.save(newer)

        repository.pruneBefore("2026-08-20")

        assertEquals(listOf(boundary), repository.latest("b1"))
        assertEquals(listOf(newer), repository.latest("b2"))
    }
}

private fun summary(
    blockId: String = "b1",
    dayKey: String,
    blockName: String = "Focus",
    conditions: List<DailyConditionHistory> = listOf(condition("steps", HistoryConditionKind.STEPS)),
) = DailyBlockHistory(
    blockId = blockId,
    dayKey = dayKey,
    blockName = blockName,
    conditions = conditions,
    thresholdN = 1,
    unlockCount = 2,
    emergencyUnlockUsed = false,
    scheduledActive = true,
    finalizedAtMillis = 1_777_000_000_000,
)

private fun condition(
    id: String,
    kind: HistoryConditionKind,
    met: Boolean = true,
) = DailyConditionHistory(
    conditionId = id,
    kind = kind,
    label = id,
    goal = 100.0,
    required = 110.0,
    progress = if (met) 120.0 else 50.0,
    met = met,
)

private class FakeHistoryDao : HistoryDao() {
    private val blocks = linkedMapOf<Pair<String, String>, DailyBlockHistoryEntity>()
    private val conditions = linkedMapOf<Triple<String, String, String>, DailyConditionHistoryEntity>()
    private val version = MutableStateFlow(0)

    override fun observeBlocks(blockId: String, limit: Int): Flow<List<DailyBlockHistoryEntity>> =
        version.map { selectBlocks(blockId, limit) }

    override suspend fun getBlocks(blockId: String, limit: Int): List<DailyBlockHistoryEntity> =
        selectBlocks(blockId, limit)

    override suspend fun conditionsForDay(
        blockId: String,
        dayKey: String,
    ): List<DailyConditionHistoryEntity> = conditions.values
        .filter { it.blockId == blockId && it.dayKey == dayKey }
        .sortedBy(DailyConditionHistoryEntity::conditionId)

    protected override suspend fun upsertBlock(block: DailyBlockHistoryEntity) {
        blocks[block.key] = block
        changed()
    }

    protected override suspend fun upsertConditions(conditions: List<DailyConditionHistoryEntity>) {
        conditions.forEach { condition -> this.conditions[condition.key] = condition }
        changed()
    }

    protected override suspend fun insertBlockIfAbsent(block: DailyBlockHistoryEntity): Long {
        if (blocks.containsKey(block.key)) return -1
        blocks[block.key] = block
        changed()
        return blocks.size.toLong()
    }

    protected override suspend fun insertConditions(conditions: List<DailyConditionHistoryEntity>) {
        conditions.forEach { condition ->
            check(this.conditions.putIfAbsent(condition.key, condition) == null)
        }
        changed()
    }

    protected override suspend fun deleteConditions(blockId: String, dayKey: String) {
        conditions.keys.removeAll { it.first == blockId && it.second == dayKey }
        changed()
    }

    override suspend fun pruneBefore(oldestDayKey: String) {
        val removed = blocks.keys.filter { it.second < oldestDayKey }
        removed.forEach { key ->
            blocks.remove(key)
            conditions.keys.removeAll { it.first == key.first && it.second == key.second }
        }
        changed()
    }

    private fun selectBlocks(blockId: String, limit: Int): List<DailyBlockHistoryEntity> = blocks.values
        .filter { it.blockId == blockId }
        .sortedByDescending(DailyBlockHistoryEntity::dayKey)
        .take(limit)

    private fun changed() {
        version.value++
    }
}

private val DailyBlockHistoryEntity.key get() = blockId to dayKey
private val DailyConditionHistoryEntity.key get() = Triple(blockId, dayKey, conditionId)
