package dev.hablock.app.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.hablock.app.domain.service.Notifier
import dev.hablock.app.ui.MainActivity

private const val CHANNEL_ID = "hablock"
private const val CHANNEL_NAME = "Hablock"
private const val RELINQUISH_NOTIFICATION_ID = 1
private const val SESSION_NOTIFICATION_BASE = 1000

class GateNotifier(private val context: Context) : Notifier {

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var channelReady = false

    override fun sessionEnded(blockName: String) {
        notify(
            id = SESSION_NOTIFICATION_BASE + blockName.hashCode(),
            title = "$blockName is locked again",
            text = "That was your 30. The goals nudged up a little — next round's on you.",
        )
    }

    override fun relinquishReady() {
        notify(
            id = RELINQUISH_NOTIFICATION_ID,
            title = "The 3-day timer is done",
            text = "You can now hand back control and uninstall Hablock.",
        )
    }

    private fun notify(id: Int, title: String, text: String) {
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(mainActivityIntent())
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun ensureChannel() {
        if (channelReady) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT),
        )
        channelReady = true
    }

    private fun mainActivityIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
