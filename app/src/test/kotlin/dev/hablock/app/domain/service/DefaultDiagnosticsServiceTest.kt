package dev.hablock.app.domain.service

import dev.hablock.app.data.InMemoryDiagnosticsRepository
import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.model.EnforcementBackendType
import dev.hablock.app.domain.model.HcAvailability
import dev.hablock.app.domain.repository.HealthRepository
import dev.hablock.app.domain.repository.PermissionChecker
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DefaultDiagnosticsServiceTest {
    @Test
    fun `refresh captures backend permissions and health state`() = runTest {
        val repository = InMemoryDiagnosticsRepository()
        val service = DefaultDiagnosticsService(
            repository = repository,
            recorder = repository,
            permissionChecker = TestPermissions,
            healthRepository = TestHealth,
            deviceOwnerController = TestDeviceOwner,
        )

        service.refreshEnvironment()

        val snapshot = service.diagnostics.value
        assertEquals(EnforcementBackendType.DEVICE_OWNER, snapshot.activeBackend)
        assertTrue(snapshot.accessibilityPermissionGranted == true)
        assertFalse(snapshot.usageAccessGranted == true)
        assertTrue(snapshot.exactAlarmPermissionGranted == true)
        assertEquals(HcAvailability.FULL, snapshot.healthAvailability)
        assertTrue(snapshot.healthPermissionsGranted == true)
    }

    private object TestPermissions : PermissionChecker {
        override fun isAccessibilityServiceEnabled() = true
        override fun hasUsageAccess() = false
        override fun hasNotificationPermission() = true
        override fun canScheduleExactAlarms() = true
    }

    private object TestHealth : HealthRepository {
        override suspend fun availability() = HcAvailability.FULL
        override suspend fun hasAllPermissions() = true
        override fun requiredPermissions() = emptySet<String>()
        override suspend fun steps(from: Instant, to: Instant) = 0.0
        override suspend fun exerciseMinutes(from: Instant, to: Instant) = 0.0
        override suspend fun mindfulMinutes(from: Instant, to: Instant) = 0.0
    }

    private object TestDeviceOwner : DeviceOwnerController {
        override fun isDeviceOwner() = true
        override fun setPackagesSuspended(packages: Set<String>, suspended: Boolean) = emptySet<String>()
        override fun applyRestrictions() = Unit
        override fun relinquishOwnership() = true
    }
}
