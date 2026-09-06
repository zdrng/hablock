package dev.hablock.app.enforcement

import dev.hablock.app.domain.enforcement.EnforcementCoordinator
import dev.hablock.app.domain.service.FakeDeviceOwnerController
import dev.hablock.app.domain.service.FakeEnforcement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class EnforcementCoordinatorTest {
    @Test
    fun `device ownership uses accessibility for the unlock interstitial`() = runTest {
        val accessibility = FakeEnforcement()
        val owner = FakeEnforcement(false)
        val coordinator = EnforcementCoordinator(accessibility, owner, FakeDeviceOwnerController())
        coordinator.applyState(emptyList(), emptyMap())
        coordinator.showBlocked("com.example.social", "b1")
        assertEquals(1, owner.applied.size)
        assertTrue(accessibility.applied.isEmpty())
        assertEquals(listOf("com.example.social" to "b1"), accessibility.blocked)
        assertTrue(owner.blocked.isEmpty())
        assertTrue(coordinator.reportsForegroundUse())
    }
}
