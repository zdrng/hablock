package dev.hablock.app.domain.enforcement

import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.GateState

interface EnforcementBackend {
    suspend fun applyState(blocks: List<Block>, states: Map<String, GateState>)
    suspend fun showBlocked(packageName: String, blockId: String)

    /** True when first use of an unlocked app will reach GateEngine.onAppForegrounded. */
    fun reportsForegroundUse(): Boolean
}

interface DeviceOwnerController {
    fun isDeviceOwner(): Boolean
    fun setPackagesSuspended(packages: Set<String>, suspended: Boolean)
    fun applyRestrictions()
    fun relinquishOwnership()
}

/** Routes to the device-owner backend when ownership is granted, otherwise to accessibility. */
class EnforcementCoordinator(
    private val accessibilityBackend: EnforcementBackend,
    private val deviceOwnerBackend: EnforcementBackend,
    private val deviceOwner: DeviceOwnerController,
) : EnforcementBackend {

    private fun active(): EnforcementBackend =
        if (deviceOwner.isDeviceOwner()) deviceOwnerBackend else accessibilityBackend

    override suspend fun applyState(blocks: List<Block>, states: Map<String, GateState>) =
        active().applyState(blocks, states)

    override suspend fun showBlocked(packageName: String, blockId: String) =
        active().showBlocked(packageName, blockId)

    override fun reportsForegroundUse(): Boolean = active().reportsForegroundUse()
}
