package dev.hablock.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hablock.app.domain.archive.ArchiveOverlapPolicy
import dev.hablock.app.domain.archive.ArchiveSettings
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.BlockSchedule
import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.DailyBlockHistory
import dev.hablock.app.domain.model.DailyConditionHistory
import dev.hablock.app.domain.model.EmergencyUnlockState
import dev.hablock.app.domain.model.HistoryConditionKind
import dev.hablock.app.domain.model.LockType
import dev.hablock.app.domain.model.OverlapPolicy
import dev.hablock.app.domain.model.Weekday
import dev.hablock.app.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomArchiveStoreInstrumentedTest {
    private lateinit var database: HablockDatabase
    private lateinit var settings: TestSettingsRepository
    private lateinit var store: RoomArchiveStore
    private lateinit var blocks: RoomBlockRepository
    private lateinit var history: RoomHistoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, HablockDatabase::class.java).build()
        settings = TestSettingsRepository()
        store = RoomArchiveStore(database, settings)
        blocks = RoomBlockRepository(database.blockDao())
        history = RoomHistoryRepository(database.historyDao())
    }

    @After
    @Throws(IOException::class)
    fun tearDown() = database.close()

    @Test
    fun import_isAdditiveAndDoesNotRestoreGlobalsIntoNonEmptyDestination() = runBlocking {
        val existing = block("existing", "Original").copy(
            lockType = LockType.PASSWORD,
            lockPasswordHash = "keep-me",
        )
        blocks.upsert(existing)
        val existingHistory = history("existing", "2026-09-05", unlockCount = 1)
        history.save(existingHistory)
        val newHistory = history("deleted-block", "2026-09-06", unlockCount = 2)

        val result = store.importAdditive(
            blocks = listOf(
                existing.copy(name = "Must not overwrite", lockType = null, lockPasswordHash = null),
                block("new", "New"),
            ),
            history = listOf(existingHistory.copy(unlockCount = 99), newHistory),
            settings = ArchiveSettings(ArchiveOverlapPolicy.ANY_BLOCK, 240),
        )

        assertEquals(1, result.insertedBlocks)
        assertEquals(1, result.insertedHistory)
        assertTrue(!result.restoredGlobalSettings)
        assertEquals(existing, blocks.current().first { it.id == existing.id })
        assertEquals("New", blocks.current().first { it.id == "new" }.name)
        assertEquals(1, history.latest("existing").single().unlockCount)
        assertEquals(newHistory, history.latest("deleted-block").single())
        assertEquals(OverlapPolicy.ALL_BLOCKS, settings.overlapPolicy.value)
        assertEquals(0, settings.dayBoundaryMinutes.value)
    }

    @Test
    fun import_restoresGlobalsOnlyWhenDestinationStartedEmpty() = runBlocking {
        val result = store.importAdditive(
            blocks = listOf(block("new", "New")),
            history = emptyList(),
            settings = ArchiveSettings(ArchiveOverlapPolicy.ANY_BLOCK, 300),
        )

        assertTrue(result.restoredGlobalSettings)
        assertEquals(OverlapPolicy.ANY_BLOCK, settings.overlapPolicy.value)
        assertEquals(300, settings.dayBoundaryMinutes.value)
        val imported = blocks.current().single()
        assertNull(imported.lockType)
        assertEquals(BlockSchedule(setOf(Weekday.MONDAY), 600, 720), imported.schedule)
    }

    @Test
    fun allMissingBlocksRollBackWhenAnyChildInsertFails() = runBlocking {
        val duplicateConditionIds = block("invalid", "Invalid").copy(
            conditions = listOf(
                Condition.Steps("duplicate", 1.0),
                Condition.Exercise("duplicate", 2.0),
            ),
            thresholdN = 1,
        )

        var failed = false
        try {
            store.importAdditive(
                blocks = listOf(block("valid", "Valid"), duplicateConditionIds),
                history = emptyList(),
                settings = ArchiveSettings(ArchiveOverlapPolicy.ANY_BLOCK, 300),
            )
        } catch (_: SQLiteConstraintException) {
            failed = true
        }

        assertTrue("A duplicate condition ID must violate the primary key", failed)
        assertTrue(blocks.current().isEmpty())
        assertEquals(OverlapPolicy.ALL_BLOCKS, settings.overlapPolicy.value)
        assertEquals(0, settings.dayBoundaryMinutes.value)
    }

    private fun block(id: String, name: String) = Block(
        id = id,
        name = name,
        blockedPackages = setOf("app.$id"),
        blockedLabels = mapOf("app.$id" to name),
        conditions = listOf(Condition.Steps("steps", 10_000.0)),
        thresholdN = 1,
        incrementPct = 10f,
        unlockDurationMinutes = 30,
        schedule = BlockSchedule(setOf(Weekday.MONDAY), 600, 720),
    )

    private fun history(blockId: String, dayKey: String, unlockCount: Int) = DailyBlockHistory(
        blockId = blockId,
        dayKey = dayKey,
        blockName = "Snapshot",
        conditions = listOf(
            DailyConditionHistory("steps", HistoryConditionKind.STEPS, "Steps", 10_000.0, 10_000.0, 11_000.0, true),
        ),
        thresholdN = 1,
        unlockCount = unlockCount,
        emergencyUnlockUsed = false,
        scheduledActive = true,
        finalizedAtMillis = 1L,
    )
}

private class TestSettingsRepository : SettingsRepository {
    override val onboardingDone = MutableStateFlow(false)
    override val relinquishDeadlineMillis = MutableStateFlow<Long?>(null)
    override val emergencyUnlocks = MutableStateFlow(EmergencyUnlockState())
    override val overlapPolicy = MutableStateFlow(OverlapPolicy.ALL_BLOCKS)
    override val dayBoundaryMinutes = MutableStateFlow(0)

    override suspend fun setOnboardingDone() {
        onboardingDone.value = true
    }

    override suspend fun setRelinquishDeadline(millis: Long?) {
        relinquishDeadlineMillis.value = millis
    }

    override suspend fun setEmergencyUnlocks(state: EmergencyUnlockState) {
        emergencyUnlocks.value = state
    }

    override suspend fun setOverlapPolicy(policy: OverlapPolicy) {
        overlapPolicy.value = policy
    }

    override suspend fun setDayBoundaryMinutes(minutes: Int) {
        dayBoundaryMinutes.value = minutes
    }
}
