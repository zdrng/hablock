package dev.hablock.app.enforcement

import dev.hablock.app.domain.enforcement.BlockedScreenLauncher
import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.repository.PermissionChecker

class AccessibilityEnforcementBackend(
    private val launcher: BlockedScreenLauncher,
    private val permissionChecker: PermissionChecker,
) : EnforcementBackend {

    override suspend fun applyState(blocks: List<Block>, states: Map<String, GateState>) = Unit

    override suspend fun showBlocked(packageName: String, blockId: String) =
        launcher.show(packageName, blockId)

    override fun reportsForegroundUse(): Boolean = permissionChecker.isAccessibilityServiceEnabled()
}
