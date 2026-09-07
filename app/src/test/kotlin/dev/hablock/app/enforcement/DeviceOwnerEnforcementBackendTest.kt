package dev.hablock.app.enforcement

import dev.hablock.app.domain.enforcement.EnforcementPlan
import dev.hablock.app.domain.service.FakeDeviceOwnerController
import dev.hablock.app.domain.service.FakeSuspensionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DeviceOwnerEnforcementBackendTest {

    private val deviceOwner = FakeDeviceOwnerController()
    private val store = FakeSuspensionStore()
    private val backend = DeviceOwnerEnforcementBackend(deviceOwner, store)

    private fun plan(vararg packages: String) = EnforcementPlan(packages.associateWith { "b1" })

    @Test
    fun `locked blocks get suspended and recorded`() = runTest {
        backend.applyState(plan("com.example.social", "com.example.video"))

        assertEquals(listOf(setOf("com.example.social", "com.example.video") to true), deviceOwner.suspensions)
        assertEquals(setOf("com.example.social", "com.example.video"), store.stored)
    }

    @Test
    fun `an unchanged state produces no IPC and no writes`() = runTest {
        backend.applyState(plan("com.example.social", "com.example.video"))
        deviceOwner.suspensions.clear()

        backend.applyState(plan("com.example.social", "com.example.video"))

        assertTrue(deviceOwner.suspensions.isEmpty())
    }

    @Test
    fun `opening a block releases only its packages`() = runTest {
        backend.applyState(plan("com.example.social", "com.example.video"))
        deviceOwner.suspensions.clear()

        backend.applyState(plan())

        assertEquals(listOf(setOf("com.example.social", "com.example.video") to false), deviceOwner.suspensions)
        assertTrue(store.stored.isEmpty())
    }

    @Test
    fun `orphans from a previous process life are released even without a matching block`() = runTest {
        store.setSuspended(setOf("com.example.orphan"))

        backend.applyState(plan())

        assertEquals(listOf(setOf("com.example.orphan") to false), deviceOwner.suspensions)
        assertTrue(store.stored.isEmpty())
    }

    @Test
    fun `failed suspends are not recorded and failed releases stay in the ledger`() = runTest {
        deviceOwner.failSuspension = setOf("com.example.video")
        backend.applyState(plan("com.example.social", "com.example.video"))
        assertEquals(setOf("com.example.social"), store.stored)

        deviceOwner.failSuspension = setOf("com.example.social")
        backend.applyState(plan())
        assertEquals(setOf("com.example.social"), store.stored)
    }

    @Test
    fun `the device owner backend cannot observe foreground use`() {
        assertFalse(backend.reportsForegroundUse())
    }
}
