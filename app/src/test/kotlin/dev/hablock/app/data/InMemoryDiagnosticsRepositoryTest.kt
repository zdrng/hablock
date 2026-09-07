package dev.hablock.app.data

import dev.hablock.app.domain.model.DiagnosticRun
import dev.hablock.app.domain.model.EnforcementBackendType
import dev.hablock.app.domain.model.HcAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryDiagnosticsRepositoryTest {
    private var now = 100L
    private val repository = InMemoryDiagnosticsRepository { now }

    @Test
    fun `recording hooks update one observable snapshot`() {
        repository.recordActiveBackend(EnforcementBackendType.DEVICE_OWNER)
        repository.recordAccessibilityServiceConnected(true)
        repository.recordAccessibilityEvent()
        now = 200L
        repository.recordEvaluation(true, evaluatedBlocks = 4, blockedBlocks = 3)
        now = 300L
        repository.recordEnforcement(false)
        repository.recordSuspensionFailures(setOf("app.failed"))
        repository.recordEnvironment(
            accessibilityPermissionGranted = true,
            usageAccessGranted = false,
            exactAlarmPermissionGranted = true,
            healthAvailability = HcAvailability.NO_MINDFULNESS,
            healthPermissionsGranted = true,
        )
        repository.recordNextReset(1_000L)

        val snapshot = repository.diagnostics.value
        assertEquals(EnforcementBackendType.DEVICE_OWNER, snapshot.activeBackend)
        assertTrue(snapshot.accessibilityServiceConnected)
        assertEquals(100L, snapshot.lastAccessibilityEventAtMillis)
        assertEquals(DiagnosticRun(200L, true), snapshot.lastEvaluation)
        assertEquals(4, snapshot.evaluatedBlockCount)
        assertEquals(3, snapshot.blockedBlockCount)
        assertEquals(DiagnosticRun(300L, false), snapshot.lastEnforcement)
        assertEquals(setOf("app.failed"), snapshot.suspensionFailures)
        assertTrue(snapshot.accessibilityPermissionGranted == true)
        assertFalse(snapshot.usageAccessGranted == true)
        assertTrue(snapshot.exactAlarmPermissionGranted == true)
        assertEquals(HcAvailability.NO_MINDFULNESS, snapshot.healthAvailability)
        assertTrue(snapshot.healthPermissionsGranted == true)
        assertEquals(1_000L, snapshot.nextResetAtMillis)
        assertNull(snapshot.nextTransitionAtMillis)
    }

    @Test
    fun `recorded failure sets are immutable snapshots and can be cleared`() {
        val mutable = mutableSetOf("one")
        repository.recordSuspensionFailures(mutable)
        mutable += "two"
        assertEquals(setOf("one"), repository.diagnostics.value.suspensionFailures)

        repository.recordSuspensionFailures(emptySet())
        assertTrue(repository.diagnostics.value.suspensionFailures.isEmpty())
    }
}
