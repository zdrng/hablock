package dev.hablock.app.enforcement

import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.service.FakeDeviceOwnerController
import dev.hablock.app.domain.service.FakeSuspensionStore
import dev.hablock.app.domain.service.testBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private val LOCKED = GateState.Locked(emptyList(), 1)
private val OPEN = GateState.Open(emptyList(), 1)

class DeviceOwnerEnforcementBackendTest {

    private val deviceOwner = FakeDeviceOwnerController()
    private val store = FakeSuspensionStore()
    private val backend = DeviceOwnerEnforcementBackend(deviceOwner, store)

    private val block = testBlock(id = "b1", packages = setOf("com.example.social", "com.example.video"))

    @Test
    fun `locked blocks get suspended and recorded`() = runTest {
        backend.applyState(listOf(block), mapOf("b1" to LOCKED))

        assertEquals(listOf(setOf("com.example.social", "com.example.video") to true), deviceOwner.suspensions)
        assertEquals(setOf("com.example.social", "com.example.video"), store.stored)
    }

    @Test
    fun `an unchanged state produces no IPC and no writes`() = runTest {
        backend.applyState(listOf(block), mapOf("b1" to LOCKED))
        deviceOwner.suspensions.clear()

        backend.applyState(listOf(block), mapOf("b1" to LOCKED))

        assertTrue(deviceOwner.suspensions.isEmpty())
    }

    @Test
    fun `opening a block releases only its packages`() = runTest {
        backend.applyState(listOf(block), mapOf("b1" to LOCKED))
        deviceOwner.suspensions.clear()

        backend.applyState(listOf(block), mapOf("b1" to OPEN))

        assertEquals(listOf(setOf("com.example.social", "com.example.video") to false), deviceOwner.suspensions)
        assertTrue(store.stored.isEmpty())
    }

    @Test
    fun `orphans from a previous process life are released even without a matching block`() = runTest {
        store.setSuspended(setOf("com.example.orphan"))

        backend.applyState(listOf(block), mapOf("b1" to OPEN))

        assertEquals(listOf(setOf("com.example.orphan") to false), deviceOwner.suspensions)
        assertTrue(store.stored.isEmpty())
    }

    @Test
    fun `failed suspends are not recorded and failed releases stay in the ledger`() = runTest {
        deviceOwner.failSuspension = setOf("com.example.video")
        backend.applyState(listOf(block), mapOf("b1" to LOCKED))
        assertEquals(setOf("com.example.social"), store.stored)

        deviceOwner.failSuspension = setOf("com.example.social")
        backend.applyState(listOf(block), mapOf("b1" to OPEN))
        assertEquals(setOf("com.example.social"), store.stored)
    }

    @Test
    fun `the device owner backend cannot observe foreground use`() {
        assertFalse(backend.reportsForegroundUse())
    }
}
