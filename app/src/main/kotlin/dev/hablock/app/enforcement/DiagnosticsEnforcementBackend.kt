package dev.hablock.app.enforcement

import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.enforcement.EnforcementPlan
import dev.hablock.app.domain.model.EnforcementBackendType
import dev.hablock.app.domain.repository.DiagnosticsRecorder

/** Adds passive observations without changing backend routing or policy. */
class DiagnosticsEnforcementBackend(
    private val delegate: EnforcementBackend,
    private val deviceOwner: DeviceOwnerController,
    private val diagnostics: DiagnosticsRecorder,
) : EnforcementBackend {
    override suspend fun applyState(plan: EnforcementPlan) {
        diagnostics.recordActiveBackend(activeBackend())
        try {
            delegate.applyState(plan)
            diagnostics.recordEnforcement(true)
        } catch (cause: Throwable) {
            diagnostics.recordEnforcement(false)
            throw cause
        }
    }

    override suspend fun showBlocked(packageName: String, blockId: String) =
        delegate.showBlocked(packageName, blockId)

    override fun reportsForegroundUse(): Boolean = delegate.reportsForegroundUse()

    private fun activeBackend(): EnforcementBackendType =
        if (deviceOwner.isDeviceOwner()) {
            EnforcementBackendType.DEVICE_OWNER
        } else {
            EnforcementBackendType.ACCESSIBILITY
        }
}
