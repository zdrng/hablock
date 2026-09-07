package dev.hablock.app.domain.service

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private const val MINUTES_PER_DAY = 24 * 60

/** A Hablock day runs between consecutive local-time boundaries. */
class DayClock(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneOverride: ZoneId? = null,
    private val boundaryMinutesProvider: () -> Int = { 0 },
) {
    // Resolved per call so a timezone change on the device takes effect without a new instance.
    private val zone: ZoneId get() = zoneOverride ?: ZoneId.systemDefault()

    fun now(): Instant = clock.instant()

    fun dayStart(now: Instant = now()): Instant {
        val zone = zone
        val boundary = boundaryTime()
        return resetAt(dayDate(now, zone, boundary), zone, boundary)
    }

    fun dayKey(now: Instant = now()): String {
        val zone = zone
        return dayDate(now, zone, boundaryTime()).toString()
    }

    fun dayWindow(now: Instant = now()): Pair<Instant, Instant> = dayStart(now) to now

    /** Full persisted Hablock-day window for history finalization. */
    fun dayWindow(dayKey: String): Pair<Instant, Instant> {
        val date = LocalDate.parse(dayKey)
        val zone = zone
        val boundary = boundaryTime()
        return resetAt(date, zone, boundary) to resetAt(date.plusDays(1), zone, boundary)
    }

    fun nextReset(now: Instant = now()): Instant {
        val zone = zone
        val boundary = boundaryTime()
        return resetAt(dayDate(now, zone, boundary).plusDays(1), zone, boundary)
    }

    private fun boundaryTime(): LocalTime {
        val minutes = boundaryMinutesProvider()
        require(minutes in 0 until MINUTES_PER_DAY) {
            "Day boundary must be a minute of day from 0 to ${MINUTES_PER_DAY - 1}, but was $minutes"
        }
        return LocalTime.of(minutes / 60, minutes % 60)
    }

    private fun dayDate(now: Instant, zone: ZoneId, boundary: LocalTime): LocalDate {
        val localDate = now.atZone(zone).toLocalDate()
        // Compare instants, not wall-clock times: a DST gap can move the resolved boundary
        // forward, and an overlap can make the same local time occur twice.
        return if (now < resetAt(localDate, zone, boundary)) localDate.minusDays(1) else localDate
    }

    private fun resetAt(date: LocalDate, zone: ZoneId, boundary: LocalTime): Instant =
        ZonedDateTime.of(date, boundary, zone).toInstant()
}
