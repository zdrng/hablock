package dev.hablock.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dev.hablock.app.domain.model.AppUsageEntry
import dev.hablock.app.domain.repository.UsageStatsRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MILLIS_PER_MINUTE = 60_000.0
private const val MIN_REPORTED_MINUTES = 0.5
private const val LOOKBACK_MILLIS = 7 * 24 * 60 * 60 * 1000L

class AndroidUsageStatsRepository(context: Context) : UsageStatsRepository {

    private val appContext = context.applicationContext
    private val usageStatsManager = appContext.getSystemService(UsageStatsManager::class.java)

    override suspend fun foregroundMinutes(packageName: String, from: Instant, to: Instant): Double {
        val millis = foregroundMillis(from, to)[packageName] ?: return 0.0
        return millis / MILLIS_PER_MINUTE
    }

    override suspend fun usageByApp(from: Instant, to: Instant): List<AppUsageEntry> =
        foregroundMillis(from, to)
            .asSequence()
            .filter { (packageName, _) -> packageName != appContext.packageName }
            .map { (packageName, millis) -> AppUsageEntry(packageName, millis / MILLIS_PER_MINUTE) }
            .filter { it.minutes >= MIN_REPORTED_MINUTES }
            .sortedByDescending { it.minutes }
            .toList()

    @Suppress("DEPRECATION")
    private suspend fun foregroundMillis(from: Instant, to: Instant): Map<String, Long> =
        withContext(Dispatchers.IO) {
            val manager = usageStatsManager ?: return@withContext emptyMap()
            val fromMillis = from.toEpochMilli()
            val toMillis = to.toEpochMilli()
            if (toMillis <= fromMillis) return@withContext emptyMap()

            val usage = ForegroundUsageAccumulator(fromMillis, toMillis)
            // Replay retained events before the boundary to recover an already-open app.
            val events = runCatching { manager.queryEvents(fromMillis - LOOKBACK_MILLIS, toMillis) }.getOrNull()
                ?: return@withContext emptyMap()
            val event = UsageEvents.Event()

            while (events.getNextEvent(event)) {
                if (event.eventType == UsageEvents.Event.DEVICE_SHUTDOWN ||
                    event.eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                ) {
                    usage.closeAll(event.timeStamp)
                    continue
                }
                val packageName = event.packageName ?: continue
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> usage.open(packageName, event.timeStamp)
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> usage.close(packageName, event.timeStamp)
                }
            }
            usage.totals()
        }

}
