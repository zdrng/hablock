package dev.hablock.app.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.service.AlarmScheduler
import java.time.Instant

private const val DAY_RESET_REQUEST_CODE = 0
private const val RELINQUISH_READY_REQUEST_CODE = 1
private const val INEXACT_WINDOW_MILLIS = 2 * 60 * 1000L

class AndroidAlarmScheduler(private val context: Context) : AlarmScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleSessionEnd(blockId: String, at: Instant) {
        schedule(sessionPendingIntent(blockId, PendingIntent.FLAG_UPDATE_CURRENT), at)
    }

    override fun cancelSessionEnd(blockId: String) {
        sessionPendingIntent(blockId, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    override fun scheduleDayReset(at: Instant) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(GateConstants.ACTION_DAY_RESET)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAY_RESET_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        schedule(pendingIntent, at)
    }

    override fun scheduleRelinquishReady(at: Instant) {
        schedule(relinquishPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT), at)
    }

    override fun cancelRelinquishReady() {
        relinquishPendingIntent(PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun relinquishPendingIntent(extraFlags: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(GateConstants.ACTION_RELINQUISH_READY)
        return PendingIntent.getBroadcast(
            context,
            RELINQUISH_READY_REQUEST_CODE,
            intent,
            extraFlags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun sessionPendingIntent(blockId: String, extraFlags: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(GateConstants.ACTION_SESSION_EXPIRED)
            .putExtra(GateConstants.EXTRA_BLOCK_ID, blockId)
        return PendingIntent.getBroadcast(
            context,
            blockId.hashCode(),
            intent,
            extraFlags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun schedule(pendingIntent: PendingIntent?, at: Instant) {
        if (pendingIntent == null) return
        val triggerAt = at.toEpochMilli()
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, INEXACT_WINDOW_MILLIS, pendingIntent)
        }
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
}
