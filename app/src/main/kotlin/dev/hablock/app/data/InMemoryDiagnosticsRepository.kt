package dev.hablock.app.data

import dev.hablock.app.domain.model.DiagnosticRun
import dev.hablock.app.domain.model.EnforcementBackendType
import dev.hablock.app.domain.model.EnforcementDiagnostics
import dev.hablock.app.domain.model.HcAvailability
import dev.hablock.app.domain.repository.DiagnosticsRecorder
import dev.hablock.app.domain.repository.DiagnosticsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryDiagnosticsRepository(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : DiagnosticsRepository, DiagnosticsRecorder {
    private val state = MutableStateFlow(EnforcementDiagnostics())
    override val diagnostics: StateFlow<EnforcementDiagnostics> = state.asStateFlow()

    override fun recordActiveBackend(backend: EnforcementBackendType) = update { copy(activeBackend = backend) }

    override fun recordAccessibilityServiceConnected(connected: Boolean) =
        update { copy(accessibilityServiceConnected = connected) }

    override fun recordAccessibilityEvent() = update { copy(lastAccessibilityEventAtMillis = nowMillis()) }

    override fun recordEvaluation(succeeded: Boolean, evaluatedBlocks: Int, blockedBlocks: Int) = update {
        copy(
            lastEvaluation = DiagnosticRun(nowMillis(), succeeded),
            evaluatedBlockCount = evaluatedBlocks,
            blockedBlockCount = blockedBlocks,
        )
    }

    override fun recordEnforcement(succeeded: Boolean) = update {
        copy(lastEnforcement = DiagnosticRun(nowMillis(), succeeded))
    }

    override fun recordSuspensionFailures(packages: Set<String>) = update {
        copy(suspensionFailures = packages.toSet())
    }

    override fun recordEnvironment(
        accessibilityPermissionGranted: Boolean,
        usageAccessGranted: Boolean,
        exactAlarmPermissionGranted: Boolean,
        healthAvailability: HcAvailability,
        healthPermissionsGranted: Boolean,
    ) = update {
        copy(
            accessibilityPermissionGranted = accessibilityPermissionGranted,
            usageAccessGranted = usageAccessGranted,
            exactAlarmPermissionGranted = exactAlarmPermissionGranted,
            healthAvailability = healthAvailability,
            healthPermissionsGranted = healthPermissionsGranted,
        )
    }

    override fun recordNextReset(atMillis: Long?) = update { copy(nextResetAtMillis = atMillis) }

    override fun recordNextTransition(atMillis: Long?) = update { copy(nextTransitionAtMillis = atMillis) }

    private inline fun update(transform: EnforcementDiagnostics.() -> EnforcementDiagnostics) {
        state.update { current -> current.transform() }
    }
}

/** Process-local bridge for Android components, such as AccessibilityService, that the OS constructs. */
object DiagnosticsRuntime {
    val repository = InMemoryDiagnosticsRepository()
}
