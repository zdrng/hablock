package dev.hablock.app.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import dev.hablock.app.domain.service.Notifier

private const val CHANNEL_ID = "hablock"
private const val CHANNEL_NAME = "Hablock"
private const val RELINQUISH_NOTIFICATION_ID = 1
private const val SESSION_NOTIFICATION_ID = 2

class GateNotifier(
    private val context: Context,
    private val contentIntent: () -> PendingIntent,
) : Notifier {

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var channelReady = false

    override fun sessionEnded(blockId: String, blockName: String) {
        notify(
            tag = blockId,
            id = SESSION_NOTIFICATION_ID,
            title = "$blockName is locked again",
            text = "That was your 30. The goals nudged up a little — next round's on you.",
        )
    }

    override fun relinquishReady() {
        notify(
            tag = null,
            id = RELINQUISH_NOTIFICATION_ID,
            title = "The 3-day timer is done",
            text = "You can now hand back control and uninstall Hablock.",
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
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT),
        )
        channelReady = true
    }
}
