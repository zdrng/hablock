package dev.hablock.app.domain.model

enum class EnforcementBackendType {
    ACCESSIBILITY,
    DEVICE_OWNER,
}

data class DiagnosticRun(
    val atMillis: Long,
    val succeeded: Boolean,
)

/** Passive observations only; diagnostics never trigger an enforcement self-test. */
data class EnforcementDiagnostics(
    val activeBackend: EnforcementBackendType? = null,
    val accessibilityServiceConnected: Boolean = false,
    val lastAccessibilityEventAtMillis: Long? = null,
    val lastEvaluation: DiagnosticRun? = null,
    val evaluatedBlockCount: Int = 0,
    val blockedBlockCount: Int = 0,
    val lastEnforcement: DiagnosticRun? = null,
    val suspensionFailures: Set<String> = emptySet(),
    val accessibilityPermissionGranted: Boolean? = null,
    val usageAccessGranted: Boolean? = null,
    val exactAlarmPermissionGranted: Boolean? = null,
    val healthAvailability: HcAvailability? = null,
    val healthPermissionsGranted: Boolean? = null,
    val nextResetAtMillis: Long? = null,
    /** Reserved for the schedule engine's next active/inactive boundary. */
    val nextTransitionAtMillis: Long? = null,
)
