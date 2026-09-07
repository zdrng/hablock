package dev.hablock.app.enforcement

import dev.hablock.app.domain.enforcement.BlockedScreenLauncher
import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.enforcement.EnforcementPlan
import dev.hablock.app.domain.repository.PermissionChecker

class AccessibilityEnforcementBackend(
    private val launcher: BlockedScreenLauncher,
    private val permissionChecker: PermissionChecker,
) : EnforcementBackend {

    override suspend fun applyState(plan: EnforcementPlan) = Unit

    override suspend fun showBlocked(packageName: String, blockId: String) =
        launcher.show(packageName, blockId)

    override fun reportsForegroundUse(): Boolean = permissionChecker.isAccessibilityServiceEnabled()
}
