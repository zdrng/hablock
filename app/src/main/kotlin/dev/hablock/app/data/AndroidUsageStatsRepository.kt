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

            val totals = mutableMapOf<String, Long>()
            val openedAt = mutableMapOf<String, Long>()
            val events = runCatching { manager.queryEvents(fromMillis, toMillis) }.getOrNull()
                ?: return@withContext emptyMap()
            val event = UsageEvents.Event()

            while (events.getNextEvent(event)) {
                val packageName = event.packageName ?: continue
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> openedAt[packageName] = event.timeStamp
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val start = openedAt.remove(packageName) ?: fromMillis
                        totals.add(packageName, event.timeStamp - start)
                    }
                }
            }
            openedAt.forEach { (packageName, start) -> totals.add(packageName, toMillis - start) }
            totals
        }

    private fun MutableMap<String, Long>.add(packageName: String, millis: Long) {
        if (millis <= 0L) return
        this[packageName] = (this[packageName] ?: 0L) + millis
    }
}
