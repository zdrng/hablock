package dev.hablock.app.domain.repository

import dev.hablock.app.domain.model.EnforcementBackendType
import dev.hablock.app.domain.model.EnforcementDiagnostics
import dev.hablock.app.domain.model.HcAvailability
import kotlinx.coroutines.flow.StateFlow

interface DiagnosticsRepository {
    val diagnostics: StateFlow<EnforcementDiagnostics>
}

/** Synchronous, non-throwing hooks suitable for hot enforcement paths. */
interface DiagnosticsRecorder {
    fun recordActiveBackend(backend: EnforcementBackendType)
    fun recordAccessibilityServiceConnected(connected: Boolean)
    fun recordAccessibilityEvent()
    fun recordEvaluation(succeeded: Boolean, evaluatedBlocks: Int = 0, blockedBlocks: Int = 0)
    fun recordEnforcement(succeeded: Boolean)
    fun recordSuspensionFailures(packages: Set<String>)
    fun recordEnvironment(
        accessibilityPermissionGranted: Boolean,
        usageAccessGranted: Boolean,
        exactAlarmPermissionGranted: Boolean,
        healthAvailability: HcAvailability,
        healthPermissionsGranted: Boolean,
    )
    fun recordNextReset(atMillis: Long?)
    fun recordNextTransition(atMillis: Long?)
}

object NoOpDiagnosticsRecorder : DiagnosticsRecorder {
    override fun recordActiveBackend(backend: EnforcementBackendType) = Unit
    override fun recordAccessibilityServiceConnected(connected: Boolean) = Unit
    override fun recordAccessibilityEvent() = Unit
    override fun recordEvaluation(succeeded: Boolean, evaluatedBlocks: Int, blockedBlocks: Int) = Unit
    override fun recordEnforcement(succeeded: Boolean) = Unit
    override fun recordSuspensionFailures(packages: Set<String>) = Unit
    override fun recordEnvironment(
        accessibilityPermissionGranted: Boolean,
        usageAccessGranted: Boolean,
        exactAlarmPermissionGranted: Boolean,
        healthAvailability: HcAvailability,
        healthPermissionsGranted: Boolean,
    ) = Unit
    override fun recordNextReset(atMillis: Long?) = Unit
    override fun recordNextTransition(atMillis: Long?) = Unit
}
