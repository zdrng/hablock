package dev.hablock.app.enforcement

import android.content.Context
import android.content.Intent
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.repository.PermissionChecker
import dev.hablock.app.ui.blocked.BlockedActivity

class AccessibilityEnforcementBackend(
    private val context: Context,
    private val permissionChecker: PermissionChecker,
) : EnforcementBackend {

    override suspend fun applyState(blocks: List<Block>, states: Map<String, GateState>) = Unit

    override suspend fun showBlocked(packageName: String, blockId: String) {
        val intent = Intent(context, BlockedActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            )
            putExtra(GateConstants.EXTRA_BLOCK_ID, blockId)
            putExtra(GateConstants.EXTRA_PACKAGE_NAME, packageName)
        }
        context.startActivity(intent)
    }

    override fun reportsForegroundUse(): Boolean = permissionChecker.isAccessibilityServiceEnabled()
}
