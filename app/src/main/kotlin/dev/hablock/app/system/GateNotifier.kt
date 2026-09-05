package dev.hablock.app.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.hablock.app.R
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.service.Notifier
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "hablock"
private const val RELINQUISH_NOTIFICATION_ID = 1
private const val SESSION_NOTIFICATION_ID = 2
private const val TICKER_INTERVAL_MS = 1_000L
private const val BRAND_VIOLET = 0xFF7A5AF8.toInt()

class GateNotifier(
    private val context: Context,
    private val scope: CoroutineScope,
    private val contentIntent: () -> PendingIntent,
) : Notifier {

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var channelReady = false
    private val tickers = mutableMapOf<String, Job>()
    private val maxSecondsPerBlock = mutableMapOf<String, Int>()

    override fun sessionStarted(blockId: String, blockName: String, endsAt: Instant) {
        ensureChannel()
        tickers[blockId]?.cancel()
        maxSecondsPerBlock.remove(blockId)
        postSessionNotification(blockId, blockName, endsAt)
        tickers[blockId] = scope.launch(Dispatchers.Default) {
            while (true) {
                delay(TICKER_INTERVAL_MS)
                val now = System.currentTimeMillis()
                if (now >= endsAt.toEpochMilli()) break
                postSessionNotification(blockId, blockName, endsAt)
            }
            tickers.remove(blockId)
            maxSecondsPerBlock.remove(blockId)
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
        tickers.remove(blockId)?.cancel()
        maxSecondsPerBlock.remove(blockId)
        manager.cancel(blockId, SESSION_NOTIFICATION_ID)
    }

    override fun restoreSessionNotification(blockId: String, blockName: String, endsAt: Instant) {
        if (tickers[blockId]?.isActive == true) return
        sessionStarted(blockId, blockName, endsAt)
    }

    override fun relinquishReady() {
        notify(
            tag = null,
            id = RELINQUISH_NOTIFICATION_ID,
            title = context.getString(R.string.notif_relinquish_title),
            text = context.getString(R.string.notif_relinquish_text),
        )
    }

    private fun postSessionNotification(blockId: String, blockName: String, endsAt: Instant) {
        val now = System.currentTimeMillis()
        val endsAtMillis = endsAt.toEpochMilli()
        val remainingSeconds = ((endsAtMillis - now) / 1000L).coerceAtLeast(0L).toInt()
        if (remainingSeconds <= 0) return

        val maxSeconds = maxSecondsPerBlock.getOrPut(blockId) { remainingSeconds }
        val elapsedSeconds = (maxSeconds - remainingSeconds).coerceAtLeast(0)

        val relockIntent = Intent(context, AlarmReceiver::class.java)
            .setAction(GateConstants.ACTION_RELOCK)
            .putExtra(GateConstants.EXTRA_BLOCK_ID, blockId)
        val relockPendingIntent = PendingIntent.getBroadcast(
            context,
            blockId.hashCode(),
            relockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val countdownText = context.getString(R.string.notif_session_remaining, remainingSeconds / 60, remainingSeconds % 60)

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_session_active_title, blockName))
            .setContentText(countdownText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(contentIntent())
            .setColor(BRAND_VIOLET)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endsAtMillis)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_lock_lock,
                context.getString(R.string.notif_session_relock),
                relockPendingIntent,
            )

        if (Build.VERSION.SDK_INT >= 36) {
            val progressStyle = Notification.ProgressStyle()
                .setStyledByProgress(true)
                .setProgress(elapsedSeconds)
                .addProgressSegment(
                    Notification.ProgressStyle.Segment(maxSeconds).setColor(BRAND_VIOLET)
                )
                .setProgressTrackerIcon(
                    android.graphics.drawable.Icon.createWithResource(
                        context,
                        android.R.drawable.ic_lock_idle_lock,
                    )
                )
            builder.setStyle(progressStyle)
        } else {
            builder
                .setProgress(maxSeconds, elapsedSeconds, false)
                .setStyle(Notification.BigTextStyle().bigText(countdownText))
        }

        try {
            manager.notify(blockId, SESSION_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        }
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
