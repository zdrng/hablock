package dev.hablock.app.domain.service

import dev.hablock.app.domain.model.LockType
import dev.hablock.app.domain.model.OverlapPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class BehaviorSettingsServiceTest {

    @Test
    fun `unlocked configuration permits global behavior changes`() = runTest {
        val settings = FakeSettingsRepository()
        var policyChanges = 0
        var boundaryChanges = 0
        val service = BehaviorSettingsService(
            settingsRepository = settings,
            blockRepository = FakeBlockRepository(listOf(testBlock())),
            onOverlapPolicyChanged = { policyChanges++ },
            onDayBoundaryChanged = { boundaryChanges++ },
        )

        assertTrue(service.setOverlapPolicy(OverlapPolicy.ANY_BLOCK))
        assertTrue(service.setDayBoundaryMinutes(180))

        assertEquals(OverlapPolicy.ANY_BLOCK, settings.overlapPolicy.first())
        assertEquals(180, settings.dayBoundaryMinutes.first())
        assertEquals(1, policyChanges)
        assertEquals(1, boundaryChanges)
    }

    @Test
    fun `active duration lock protects global behavior`() = runTest {
        val now = 1_000L
        val locked = testBlock().copy(
            lockType = LockType.DURATION,
            blockedUntil = now + 1,
        )
        val settings = FakeSettingsRepository()
        val service = BehaviorSettingsService(settings, FakeBlockRepository(listOf(locked)), { now })

        assertFalse(service.setOverlapPolicy(OverlapPolicy.ANY_BLOCK))
        assertFalse(service.setDayBoundaryMinutes(180))
        assertEquals(OverlapPolicy.ALL_BLOCKS, settings.overlapPolicy.first())
        assertEquals(0, settings.dayBoundaryMinutes.first())
    }

    @Test
    fun `expired duration lock no longer protects global behavior`() = runTest {
        val now = 1_000L
        val expired = testBlock().copy(
            lockType = LockType.DURATION,
            blockedUntil = now,
        )
        val service = BehaviorSettingsService(
            FakeSettingsRepository(),
            FakeBlockRepository(listOf(expired)),
            { now },
        )

        assertTrue(service.setOverlapPolicy(OverlapPolicy.ANY_BLOCK))
    }

    @Test
    fun `password lock protects global behavior`() = runTest {
        val locked = testBlock().copy(
            lockType = LockType.PASSWORD,
            blockedUntil = Long.MAX_VALUE,
            lockPasswordHash = "hash",
        )
        val service = BehaviorSettingsService(
            FakeSettingsRepository(),
            FakeBlockRepository(listOf(locked)),
        )

        assertFalse(service.setDayBoundaryMinutes(180))
    }
}
