package dev.hablock.app.ui.blocked

import android.content.Context
import android.content.Intent
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.enforcement.BlockedScreenLauncher

class AndroidBlockedScreenLauncher(private val context: Context) : BlockedScreenLauncher {

    override fun show(packageName: String, blockId: String) {
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
}
