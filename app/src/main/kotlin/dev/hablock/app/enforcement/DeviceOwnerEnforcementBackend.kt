package dev.hablock.app.enforcement

import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.enforcement.EnforcementPlan
import dev.hablock.app.domain.enforcement.SuspensionStore
import dev.hablock.app.domain.repository.DiagnosticsRecorder
import dev.hablock.app.domain.repository.NoOpDiagnosticsRecorder

class DeviceOwnerEnforcementBackend(
    private val deviceOwner: DeviceOwnerController,
    private val store: SuspensionStore,
    private val diagnostics: DiagnosticsRecorder = NoOpDiagnosticsRecorder,
) : EnforcementBackend {

    override suspend fun applyState(plan: EnforcementPlan) {
        val desired = plan.blockedPackages

        val previous = store.suspended()
        val toSuspend = desired - previous
        val toRelease = previous - desired
        if (toSuspend.isEmpty() && toRelease.isEmpty()) return

        val failedSuspend: Set<String>
        val failedRelease: Set<String>
        try {
            failedSuspend = if (toSuspend.isNotEmpty()) deviceOwner.setPackagesSuspended(toSuspend, true) else emptySet()
            failedRelease = if (toRelease.isNotEmpty()) deviceOwner.setPackagesSuspended(toRelease, false) else emptySet()
        } catch (cause: Throwable) {
            diagnostics.recordSuspensionFailures(toSuspend + toRelease)
            throw cause
        }
        diagnostics.recordSuspensionFailures(failedSuspend + failedRelease)
        store.setSuspended(desired - failedSuspend + failedRelease)
    }

    override suspend fun showBlocked(packageName: String, blockId: String) = Unit

    override fun reportsForegroundUse(): Boolean = false
}
