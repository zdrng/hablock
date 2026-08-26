package dev.hablock.app.enforcement

import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.enforcement.SuspensionStore
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.GateState

class DeviceOwnerEnforcementBackend(
    private val deviceOwner: DeviceOwnerController,
    private val store: SuspensionStore,
) : EnforcementBackend {

    override suspend fun applyState(blocks: List<Block>, states: Map<String, GateState>) {
        val desired = blocks
            .filter { it.enabled && states[it.id] is GateState.Locked }
            .flatMapTo(mutableSetOf()) { it.blockedPackages }

        val previous = store.suspended()
        val toSuspend = desired - previous
        val toRelease = previous - desired
        if (toSuspend.isEmpty() && toRelease.isEmpty()) return

        val failedSuspend = if (toSuspend.isNotEmpty()) deviceOwner.setPackagesSuspended(toSuspend, true) else emptySet()
        val failedRelease = if (toRelease.isNotEmpty()) deviceOwner.setPackagesSuspended(toRelease, false) else emptySet()
        store.setSuspended(desired - failedSuspend + failedRelease)
    }

    override suspend fun showBlocked(packageName: String, blockId: String) = Unit

    override fun reportsForegroundUse(): Boolean = false
}
