package dev.hablock.app.domain.archive

import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.BlockSchedule
import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.DailyBlockHistory
import dev.hablock.app.domain.model.DailyConditionHistory
import dev.hablock.app.domain.model.HistoryConditionKind
import dev.hablock.app.domain.model.LockType
import dev.hablock.app.domain.model.OverlapPolicy
import dev.hablock.app.domain.model.Weekday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ArchiveServiceTest {
    private val block = Block(
        id = "evening",
        name = "Evening",
        blockedPackages = setOf("social.app"),
        blockedLabels = mapOf("social.app" to "Social"),
        conditions = listOf(Condition.Steps("steps", 10_000.0)),
        thresholdN = 1,
        incrementPct = 10f,
        unlockDurationMinutes = 30,
        enabled = false,
        blockedUntil = 99_999L,
        lockType = LockType.PASSWORD,
        lockPasswordHash = "secret-hash",
        schedule = BlockSchedule(setOf(Weekday.MONDAY), 18 * 60, 23 * 60),
    )
    private val history = DailyBlockHistory(
        blockId = block.id,
        dayKey = "2026-09-06",
        blockName = block.name,
        conditions = listOf(
            DailyConditionHistory("steps", HistoryConditionKind.STEPS, "Steps", 10_000.0, 11_000.0, 12_000.0, true),
        ),
        thresholdN = 1,
        unlockCount = 2,
        emergencyUnlockUsed = false,
        scheduledActive = true,
        finalizedAtMillis = 1_788_649_200_000L,
    )

    @Test
    fun `export is versioned and excludes every lock field`() = runTest {
        val store = FakeArchiveStore(
            snapshot = ArchiveStoreSnapshot(listOf(block), listOf(history), OverlapPolicy.ANY_BLOCK, 240),
        )
        val result = ArchiveService(store, nowMillis = { 123L }).export()

        assertEquals(1, result.blockCount)
        assertEquals(1, result.historyCount)
        assertTrue(result.json.contains("\"version\": 1"))
        assertTrue(result.json.contains("\"enabled\": false"))
        assertFalse(result.json.contains("lockType"))
        assertFalse(result.json.contains("lockPasswordHash"))
        assertFalse(result.json.contains("blockedUntil"))
        assertFalse(result.json.contains("secret-hash"))
    }

    @Test
    fun `preview reports additive conflicts without writing`() = runTest {
        val store = FakeArchiveStore(
            snapshot = ArchiveStoreSnapshot(listOf(block), listOf(history), OverlapPolicy.ALL_BLOCKS, 0),
            destination = ArchiveDestination(
                blockIds = setOf(block.id),
                historyKeys = setOf(ArchiveHistoryKey(block.id, history.dayKey)),
            ),
        )
        val service = ArchiveService(store)
        val raw = service.export().json

        val preview = service.preview(raw)

        assertEquals(0, preview.newBlockCount)
        assertEquals(1, preview.skippedBlockCount)
        assertEquals(0, preview.newHistoryCount)
        assertEquals(1, preview.skippedHistoryCount)
        assertFalse(preview.willRestoreGlobalSettings)
        assertEquals(0, store.importCalls)
    }

    @Test
    fun `import always hands editable blocks to the store`() = runTest {
        val store = FakeArchiveStore(
            snapshot = ArchiveStoreSnapshot(listOf(block), listOf(history), OverlapPolicy.ANY_BLOCK, 300),
            importResult = ArchiveStoreImportResult(1, 1, true),
        )
        val service = ArchiveService(store)
        val raw = service.export().json

        val result = service.import(raw)

        val imported = store.importedBlocks.single()
        assertNull(imported.lockType)
        assertNull(imported.lockPasswordHash)
        assertNull(imported.blockedUntil)
        assertFalse(imported.enabled)
        assertEquals(block.schedule, imported.schedule)
        assertEquals(1, result.insertedBlocks)
        assertEquals(1, result.insertedHistory)
        assertTrue(result.restoredGlobalSettings)
    }

    @Test
    fun `import removes forbidden blocked packages while preserving enabled state`() = runTest {
        val enabledBlock = block.copy(
            enabled = true,
            blockedPackages = setOf("social.app", "dev.hablock.app", "launcher.app"),
            blockedLabels = mapOf(
                "social.app" to "Social",
                "dev.hablock.app" to "Hablock",
                "launcher.app" to "Launcher",
            ),
        )
        val store = FakeArchiveStore(
            snapshot = ArchiveStoreSnapshot(listOf(enabledBlock), emptyList(), OverlapPolicy.ALL_BLOCKS, 0),
            importResult = ArchiveStoreImportResult(1, 0, true),
        )
        val service = ArchiveService(
            store = store,
            forbiddenBlockedPackages = { setOf("dev.hablock.app", "launcher.app") },
        )

        service.import(service.export().json)

        val imported = store.importedBlocks.single()
        assertTrue(imported.enabled)
        assertEquals(setOf("social.app"), imported.blockedPackages)
        assertEquals(mapOf("social.app" to "Social"), imported.blockedLabels)
    }

    @Test
    fun `unsupported version and invalid invariants are rejected before writes`() = runTest {
        val store = FakeArchiveStore()
        val invalidVersion = validArchive().copy(version = 2)
        val duplicateBlocks = validArchive().copy(blocks = listOf(validArchive().blocks.single(), validArchive().blocks.single()))

        assertFailsWith<ArchiveValidationException> { ArchiveService(store).import(json.encodeToString(invalidVersion)) }
        assertFailsWith<ArchiveValidationException> { ArchiveService(store).import(json.encodeToString(duplicateBlocks)) }

        assertEquals(0, store.importCalls)
    }

    private fun validArchive() = HablockArchive(
        version = CURRENT_ARCHIVE_VERSION,
        exportedAtMillis = 1L,
        settings = ArchiveSettings(ArchiveOverlapPolicy.ALL_BLOCKS, 0),
        blocks = listOf(
            ArchiveBlock(
                id = block.id,
                name = block.name,
                blockedPackages = block.blockedPackages.toList(),
                blockedLabels = block.blockedLabels,
                conditions = block.conditions,
                thresholdN = block.thresholdN,
                incrementPct = block.incrementPct,
                unlockDurationMinutes = block.unlockDurationMinutes,
                enabled = block.enabled,
                schedule = ArchiveSchedule(listOf(Weekday.MONDAY), 1, 2),
            ),
        ),
        history = listOf(history),
    )

    private companion object {
        val json = Json { encodeDefaults = true }
    }
}

private class FakeArchiveStore(
    var snapshot: ArchiveStoreSnapshot = ArchiveStoreSnapshot(emptyList(), emptyList(), OverlapPolicy.ALL_BLOCKS, 0),
    var destination: ArchiveDestination = ArchiveDestination(emptySet(), emptySet()),
    var importResult: ArchiveStoreImportResult = ArchiveStoreImportResult(0, 0, false),
) : ArchiveStore {
    var importCalls = 0
    var importedBlocks: List<Block> = emptyList()

    override suspend fun snapshot(maxHistoryDaysPerBlock: Int) = snapshot
    override suspend fun destination() = destination

    override suspend fun importAdditive(
        blocks: List<Block>,
        history: List<DailyBlockHistory>,
        settings: ArchiveSettings,
    ): ArchiveStoreImportResult {
        importCalls++
        importedBlocks = blocks
        return importResult
    }
}
