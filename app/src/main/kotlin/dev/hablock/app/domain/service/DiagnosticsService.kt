package dev.hablock.app.domain.service

import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.model.EnforcementBackendType
import dev.hablock.app.domain.model.EnforcementDiagnostics
import dev.hablock.app.domain.model.HcAvailability
import dev.hablock.app.domain.repository.DiagnosticsRecorder
import dev.hablock.app.domain.repository.DiagnosticsRepository
import dev.hablock.app.domain.repository.HealthRepository
import dev.hablock.app.domain.repository.PermissionChecker
import kotlinx.coroutines.flow.StateFlow

interface DiagnosticsService {
    val diagnostics: StateFlow<EnforcementDiagnostics>
    suspend fun refreshEnvironment()
}

class DefaultDiagnosticsService(
    repository: DiagnosticsRepository,
    private val recorder: DiagnosticsRecorder,
    private val permissionChecker: PermissionChecker,
    private val healthRepository: HealthRepository,
    private val deviceOwnerController: DeviceOwnerController,
) : DiagnosticsService {
    override val diagnostics: StateFlow<EnforcementDiagnostics> = repository.diagnostics

    override suspend fun refreshEnvironment() {
        recorder.recordActiveBackend(
            if (runCatching { deviceOwnerController.isDeviceOwner() }.getOrDefault(false)) {
                EnforcementBackendType.DEVICE_OWNER
            } else {
                EnforcementBackendType.ACCESSIBILITY
            },
        )
        recorder.recordEnvironment(
            accessibilityPermissionGranted = runCatching {
                permissionChecker.isAccessibilityServiceEnabled()
            }.getOrDefault(false),
            usageAccessGranted = runCatching { permissionChecker.hasUsageAccess() }.getOrDefault(false),
            exactAlarmPermissionGranted = runCatching {
                permissionChecker.canScheduleExactAlarms()
            }.getOrDefault(false),
            healthAvailability = runCatching { healthRepository.availability() }
                .getOrDefault(HcAvailability.UNAVAILABLE),
            healthPermissionsGranted = runCatching { healthRepository.hasAllPermissions() }.getOrDefault(false),
        )
    }
}
