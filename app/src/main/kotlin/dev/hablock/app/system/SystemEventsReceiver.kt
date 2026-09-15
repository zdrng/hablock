package dev.hablock.app.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.hablock.app.HablockApplication
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Re-arms alarms and re-evaluates after events that invalidate absolute RTC schedules. */
class SystemEventsReceiver : BroadcastReceiver() {

    private val handledActions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in handledActions) return

        val container = (context.applicationContext as HablockApplication).container
        val pendingResult = goAsync()

        container.applicationScope.launch {
            try {
                container.alarmScheduler.scheduleDayReset(container.dayClock.nextReset())
                val now = container.dayClock.now()
                container.settingsRepository.relinquishDeadlineMillis.first()?.let { deadline ->
                    // Deliver past deadlines too if the device was off when the timer elapsed.
                    container.alarmScheduler.scheduleRelinquishReady(Instant.ofEpochMilli(deadline))
                }
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
