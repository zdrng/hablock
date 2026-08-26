package dev.hablock.app.enforcement

import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.repository.PermissionChecker

class DeviceOwnerEnforcementBackend(
    private val deviceOwner: DeviceOwnerController,
    private val permissionChecker: PermissionChecker,
) : EnforcementBackend {

    /** Packages suspended by the previous applyState, so removed blocks still get released. */
    private var lastSuspended: Set<String> = emptySet()

    override suspend fun applyState(blocks: List<Block>, states: Map<String, GateState>) {
        val suspendSet = blocks
            .filter { it.enabled && states[it.id] is GateState.Locked }
            .flatMapTo(mutableSetOf()) { it.blockedPackages }

        val allowSet = blocks
            .flatMapTo(mutableSetOf()) { it.blockedPackages }
            .plus(lastSuspended)
            .minus(suspendSet)

        if (suspendSet.isNotEmpty()) deviceOwner.setPackagesSuspended(suspendSet, true)
        if (allowSet.isNotEmpty()) deviceOwner.setPackagesSuspended(allowSet, false)
        lastSuspended = suspendSet
    }

    override suspend fun showBlocked(packageName: String, blockId: String) = Unit

    override fun reportsForegroundUse(): Boolean = permissionChecker.isAccessibilityServiceEnabled()
}
