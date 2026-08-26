package dev.hablock.app.ui.blocked

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.ui.theme.HablockTheme

private data class BlockedTarget(val blockId: String, val packageName: String?)

class BlockedActivity : ComponentActivity() {

    private var target by mutableStateOf<BlockedTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        target = intent.toTarget()
        if (target == null) {
            finish()
            return
        }
        setContent {
            HablockTheme {
                target?.let {
                    BlockedScreen(
                        blockId = it.blockId,
                        packageName = it.packageName,
                        onClose = { finish() },
                        onOpenApp = ::launchBlockedApp,
                    )
                }
            }
        }
    }

    /** A second sealed app arrives here, not through onCreate — retarget the live composition. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val next = intent.toTarget()
        if (next == null) finish() else target = next
    }

    private fun launchBlockedApp(target: String) {
        packageManager.getLaunchIntentForPackage(target)?.let { startActivity(it) }
        finish()
    }
}

private fun Intent.toTarget(): BlockedTarget? =
    getStringExtra(GateConstants.EXTRA_BLOCK_ID)?.let {
        BlockedTarget(it, getStringExtra(GateConstants.EXTRA_PACKAGE_NAME))
    }
