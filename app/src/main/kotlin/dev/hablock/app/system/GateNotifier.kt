package dev.hablock.app.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.hablock.app.R
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.service.Notifier
import java.time.Instant

private const val CHANNEL_ID = "hablock"
private const val RELINQUISH_NOTIFICATION_ID = 1
private const val SESSION_NOTIFICATION_ID = 2

class GateNotifier(
    private val context: Context,
    private val contentIntent: () -> PendingIntent,
) : Notifier {

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var channelReady = false

    override fun sessionStarted(blockId: String, blockName: String, endsAt: Instant) {
        val relockIntent = Intent(context, AlarmReceiver::class.java)
            .setAction(GateConstants.ACTION_RELOCK)
            .putExtra(GateConstants.EXTRA_BLOCK_ID, blockId)
        val relockPendingIntent = PendingIntent.getBroadcast(
            context,
            blockId.hashCode(),
            relockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_session_active_title, blockName))
            .setContentText(context.getString(R.string.notif_session_active_text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(contentIntent())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setWhen(endsAt.toEpochMilli())
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_lock_lock,
                context.getString(R.string.notif_session_relock),
                relockPendingIntent,
            )
            .build()
        try {
            manager.notify(blockId, SESSION_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    override fun sessionEnded(blockId: String, blockName: String, sessionMinutes: Int) {
        notify(
            tag = blockId,
            id = SESSION_NOTIFICATION_ID,
            title = context.getString(R.string.notif_session_ended_title, blockName),
            text = context.resources.getQuantityString(R.plurals.notif_session_ended_text, sessionMinutes, sessionMinutes),
        )
    }

    override fun cancelSessionNotification(blockId: String) {
        manager.cancel(blockId, SESSION_NOTIFICATION_ID)
    }

    override fun relinquishReady() {
        notify(
            tag = null,
            id = RELINQUISH_NOTIFICATION_ID,
            title = context.getString(R.string.notif_relinquish_title),
            text = context.getString(R.string.notif_relinquish_text),
        )
    }

    private fun notify(tag: String?, id: Int, title: String, text: String) {
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(tag, id, notification)
        } catch (_: SecurityException) {
            // Notification permission revoked between the enabled check and notify — drop silently.
        }
    }

    private fun ensureChannel() {
        if (channelReady) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
        channelReady = true
    }
}
