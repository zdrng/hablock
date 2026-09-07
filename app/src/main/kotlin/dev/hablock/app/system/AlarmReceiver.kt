package dev.hablock.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.hablock.app.HablockApplication
import dev.hablock.app.domain.GateConstants
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val blockId = intent.getStringExtra(GateConstants.EXTRA_BLOCK_ID)
        val container = (context.applicationContext as HablockApplication).container
        val pendingResult = goAsync()

        container.applicationScope.launch {
            try {
                when (action) {
                    GateConstants.ACTION_SESSION_EXPIRED ->
                        blockId?.let { container.gateEngine.onSessionExpired(it) }
                    GateConstants.ACTION_DAY_RESET -> container.gateEngine.onDayReset()
                    GateConstants.ACTION_SCHEDULE_TRANSITION -> container.gateEngine.onScheduleTransition()
                    GateConstants.ACTION_RELINQUISH_READY -> container.notifier.relinquishReady()
                    GateConstants.ACTION_RELOCK ->
                        blockId?.let { container.gateEngine.relock(it) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
