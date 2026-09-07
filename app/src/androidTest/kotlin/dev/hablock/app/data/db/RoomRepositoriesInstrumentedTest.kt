package dev.hablock.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.BlockDayState
import dev.hablock.app.domain.model.BlockSchedule
import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.LockType
import dev.hablock.app.domain.model.Session
import dev.hablock.app.domain.model.Weekday
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRepositoriesInstrumentedTest {
    private lateinit var database: HablockDatabase
    private lateinit var blocks: RoomBlockRepository
    private lateinit var states: RoomGateStateRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, HablockDatabase::class.java).build()
        blocks = RoomBlockRepository(database.blockDao())
        states = RoomGateStateRepository(database.gateStateDao())
    }

    @After
    @Throws(IOException::class)
    fun tearDown() {
        database.close()
    }

    @Test
    fun blockRepository_roundTripsEveryConditionAndLockField() = runBlocking {
        val original = testBlock()

        blocks.upsert(original)

        assertEquals(original, blocks.current().single())
        assertEquals(listOf(original), blocks.blocks.first())

        val reordered = original.copy(
            name = "Updated",
            blockedPackages = setOf("app.two"),
            blockedLabels = mapOf("app.two" to "Two", "remembered.app" to "Remembered"),
            conditions = listOf(original.conditions[3], original.conditions[0]),
            lockType = LockType.DURATION,
            lockPasswordHash = null,
        )
        blocks.upsert(reordered)

        assertEquals(reordered, blocks.current().single())
    }

    @Test
    fun blockUpsert_rollsBackParentAndChildrenWhenAChildConstraintFails() = runBlocking {
        val original = testBlock().copy(conditions = listOf(Condition.Steps("steps", 5_000.0)))
        blocks.upsert(original)
        val stored = database.blockDao().getAll().single()
        val invalid = BlockAggregate(
            block = stored.block.copy(name = "Must roll back"),
            apps = emptyList(),
            conditions = listOf(
                ConditionEntity(original.id, "one", 0, ConditionKind.STEPS, 1.0, null, null),
                ConditionEntity(original.id, "two", 0, ConditionKind.EXERCISE, 2.0, null, null),
            ),
        )

        var failed = false
        try {
            database.blockDao().upsert(invalid)
        } catch (_: SQLiteConstraintException) {
            failed = true
        }

        assertTrue("The duplicate child position must violate the unique index", failed)
        assertEquals(original, blocks.current().single())
    }

    @Test
    fun gateStateRepository_replacesRequirementsAndClearAllLeavesHistory() = runBlocking {
        val block = testBlock()
        blocks.upsert(block)
        val initial = BlockDayState(
            blockId = block.id,
            dayKey = "2026-09-05",
            requiredNow = mapOf("usage" to 45.0, "steps" to 12_000.0),
            activeSession = Session(block.id, 1_000L, 61_000L),
            unlockCount = 2,
        )
        states.save(initial)
        assertEquals(initial, states.get(block.id))

        val replacement = initial.copy(
            requiredNow = mapOf("steps" to 13_000.0),
            activeSession = null,
            unlockCount = 3,
        )
        states.save(replacement)
        assertEquals(replacement, states.get(block.id))

        val history = history(block.id, "2026-09-05")
        database.historyDao().save(history)
        states.clearAll()

        assertNull(states.get(block.id))
        assertTrue(states.dayStates.first().isEmpty())
        assertEquals(listOf(history.block), database.historyDao().observeBlocks(block.id).first())
    }

    @Test
    fun blockDelete_cascadesLiveRowsButRetainsFinalizedHistory() = runBlocking {
        val block = testBlock()
        blocks.upsert(block)
        database.scheduleDao().upsert(ScheduleEntity(block.id, true, 0b0011111, 18 * 60, 23 * 60))
        states.save(BlockDayState(block.id, "2026-09-05", mapOf("steps" to 10_000.0)))
        val history = history(block.id, "2026-09-05")
        database.historyDao().save(history)

        blocks.delete(block.id)

        assertNull(database.scheduleDao().get(block.id))
        assertNull(states.get(block.id))
        assertEquals(listOf(history.block), database.historyDao().observeBlocks(block.id).first())
        assertEquals(
            history.conditions.sortedBy { it.conditionId },
            database.historyDao().conditionsForDay(block.id, history.block.dayKey),
        )
    }

    @Test
    fun historySave_replacesConditionSnapshotAndPrunesByIsoDay() = runBlocking {
        val block = testBlock()
        blocks.upsert(block)
        val old = history(block.id, "2025-09-05")
        val recent = history(block.id, "2026-09-05")
        database.historyDao().save(old)
        database.historyDao().save(recent)

        val replacement = recent.copy(conditions = recent.conditions.take(1).map { it.copy(progress = 99.0) })
        database.historyDao().save(replacement)
        assertEquals(
            replacement.conditions,
            database.historyDao().conditionsForDay(block.id, recent.block.dayKey),
        )

        database.historyDao().pruneBefore("2025-09-06")
        assertEquals(listOf(recent.block), database.historyDao().observeBlocks(block.id).first())
        assertTrue(database.historyDao().conditionsForDay(block.id, old.block.dayKey).isEmpty())
    }

    private fun testBlock() = Block(
        id = "block-1",
        name = "Evening",
        blockedPackages = setOf("app.one", "app.two"),
        blockedLabels = mapOf("app.one" to "One", "remembered.app" to "Remembered"),
        conditions = listOf(
            Condition.AppUsage("usage", 30.0, "helper.app", "Helper"),
            Condition.Steps("steps", 10_000.0),
            Condition.Exercise("exercise", 20.0),
            Condition.Meditation("meditation", 10.0),
        ),
        thresholdN = 3,
        incrementPct = 12.5f,
        unlockDurationMinutes = 45,
        enabled = true,
        blockedUntil = 1_800_000L,
        lockType = LockType.PASSWORD,
        lockPasswordHash = "scrypt:hash",
        schedule = BlockSchedule(
            weekdays = setOf(Weekday.MONDAY, Weekday.WEDNESDAY, Weekday.FRIDAY),
            startMinute = 22 * 60,
            endMinute = 6 * 60,
        ),
    )

    private fun history(blockId: String, dayKey: String) = DailyHistoryAggregate(
        block = DailyBlockHistoryEntity(
            blockId = blockId,
            dayKey = dayKey,
            blockName = "Evening",
            conditionCount = 2,
            metCount = 1,
            thresholdN = 2,
            unlockCount = 3,
            emergencyUnlockUsed = false,
            scheduledActive = true,
            finalizedAt = 1_757_102_400_000L,
        ),
        conditions = listOf(
            DailyConditionHistoryEntity(
                blockId,
                dayKey,
                "usage",
                ConditionKind.APP_USAGE,
                "Helper",
                30.0,
                45.0,
                50.0,
                true,
            ),
            DailyConditionHistoryEntity(
                blockId,
                dayKey,
                "steps",
                ConditionKind.STEPS,
                "Steps",
                10_000.0,
                12_000.0,
                11_000.0,
                false,
            ),
        ),
    )
}
