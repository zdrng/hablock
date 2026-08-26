package dev.hablock.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.hablock.app.HablockApplication
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val container = (context.applicationContext as HablockApplication).container
        val pendingResult = goAsync()

        container.applicationScope.launch {
            try {
                container.alarmScheduler.scheduleDayReset(container.dayClock.nextReset())
                val now = container.dayClock.now()
                container.gateStateRepository.dayStates.first().values.forEach { state ->
                    val session = state.activeSession ?: return@forEach
                    val endsAt = Instant.ofEpochMilli(session.endsAtMillis)
                    if (endsAt.isAfter(now)) {
                        container.alarmScheduler.scheduleSessionEnd(state.blockId, endsAt)
                    }
                }
                container.gateEngine.refreshAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
