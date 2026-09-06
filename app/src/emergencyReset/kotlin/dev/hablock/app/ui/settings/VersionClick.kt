package dev.hablock.app.ui.settings

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dev.hablock.app.domain.model.EmergencyUnlockState
import dev.hablock.app.domain.repository.SettingsRepository
import kotlinx.coroutines.launch

/** Included only with -PenableEmergencyReset=true. */
@Composable
internal fun rememberVersionClick(repository: SettingsRepository): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val taps = remember { VersionTapCounter() }
    var resetting by remember { mutableStateOf(false) }
    return {
        if (!resetting && taps.tap(SystemClock.elapsedRealtime())) {
            resetting = true
            scope.launch {
                try {
                    repository.setEmergencyUnlocks(EmergencyUnlockState())
                    Toast.makeText(context, "Emergency unlocks restored", Toast.LENGTH_SHORT).show()
                } catch (error: java.io.IOException) {
                    Toast.makeText(context, "Could not restore emergency unlocks. Try again.", Toast.LENGTH_SHORT).show()
                } finally {
                    resetting = false
                }
            }
        }
    }
}

internal class VersionTapCounter {
    private var count = 0
    private var lastTap: Long? = null

    fun tap(now: Long): Boolean {
        if (lastTap == null || now - lastTap!! > 1_000L) count = 0
        lastTap = now
        count++
        return (count == 5).also { if (it) count = 0 }
    }
}
