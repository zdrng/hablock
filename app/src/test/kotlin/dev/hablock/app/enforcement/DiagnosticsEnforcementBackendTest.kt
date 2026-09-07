package dev.hablock.app.enforcement

import dev.hablock.app.data.InMemoryDiagnosticsRepository
import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.enforcement.EnforcementPlan
import dev.hablock.app.domain.model.EnforcementBackendType
import dev.hablock.app.domain.service.FakeDeviceOwnerController
import dev.hablock.app.domain.service.FakeSuspensionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DiagnosticsEnforcementBackendTest {
    @Test
    fun `records selected backend and successful enforcement`() = runTest {
        val diagnostics = InMemoryDiagnosticsRepository { 123L }
        val backend = DiagnosticsEnforcementBackend(PassBackend, Owner(true), diagnostics)

        backend.applyState(EnforcementPlan(emptyMap()))

        assertEquals(EnforcementBackendType.DEVICE_OWNER, diagnostics.diagnostics.value.activeBackend)
        assertTrue(diagnostics.diagnostics.value.lastEnforcement?.succeeded == true)
        assertEquals(123L, diagnostics.diagnostics.value.lastEnforcement?.atMillis)
    }

    @Test
    fun `records accessibility backend and rethrows enforcement failure`() = runTest {
        val diagnostics = InMemoryDiagnosticsRepository()
        val backend = DiagnosticsEnforcementBackend(FailBackend, Owner(false), diagnostics)

        assertFailsWith<IllegalStateException> { backend.applyState(EnforcementPlan(emptyMap())) }

        assertEquals(EnforcementBackendType.ACCESSIBILITY, diagnostics.diagnostics.value.activeBackend)
        assertFalse(diagnostics.diagnostics.value.lastEnforcement?.succeeded == true)
    }

    @Test
    fun `device owner backend reports package suspension failures`() = runTest {
        val diagnostics = InMemoryDiagnosticsRepository()
        val owner = FakeDeviceOwnerController().apply { failSuspension = setOf("failed.app") }
        val backend = DeviceOwnerEnforcementBackend(owner, FakeSuspensionStore(), diagnostics)

        backend.applyState(
            EnforcementPlan(mapOf("ok.app" to "block", "failed.app" to "block")),
        )

        assertEquals(setOf("failed.app"), diagnostics.diagnostics.value.suspensionFailures)
    }

    private class Owner(private val active: Boolean) : DeviceOwnerController {
        override fun isDeviceOwner() = active
        override fun setPackagesSuspended(packages: Set<String>, suspended: Boolean) = emptySet<String>()
        override fun applyRestrictions() = Unit
        override fun relinquishOwnership() = true
    }

    private object PassBackend : EnforcementBackend {
        override suspend fun applyState(plan: EnforcementPlan) = Unit
        override suspend fun showBlocked(packageName: String, blockId: String) = Unit
        override fun reportsForegroundUse() = true
    }

    private object FailBackend : EnforcementBackend {
        override suspend fun applyState(plan: EnforcementPlan): Unit = error("IPC failed")
        override suspend fun showBlocked(packageName: String, blockId: String) = Unit
        override fun reportsForegroundUse() = true
    }
}
