package dev.hablock.app.domain.service

import dev.hablock.app.domain.GateConstants
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** The Gate day runs from DAY_RESET_HOUR to DAY_RESET_HOUR in local time. */
class DayClock(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneOverride: ZoneId? = null,
) {
    // Resolved per call so a timezone change on the device takes effect without a new instance.
    private val zone: ZoneId get() = zoneOverride ?: ZoneId.systemDefault()

    fun now(): Instant = clock.instant()

    fun dayStart(now: Instant = now()): Instant {
        val zoned = now.atZone(zone)
        val date = if (zoned.hour < GateConstants.DAY_RESET_HOUR) zoned.toLocalDate().minusDays(1) else zoned.toLocalDate()
        return resetAt(date)
    }

    fun dayKey(now: Instant = now()): String =
        dayStart(now).atZone(zone).toLocalDate().toString()

    fun dayWindow(now: Instant = now()): Pair<Instant, Instant> = dayStart(now) to now

    fun nextReset(now: Instant = now()): Instant =
        resetAt(dayStart(now).atZone(zone).toLocalDate().plusDays(1))

    private fun resetAt(date: LocalDate): Instant =
        ZonedDateTime.of(date, java.time.LocalTime.of(GateConstants.DAY_RESET_HOUR, 0), zone).toInstant()
}
