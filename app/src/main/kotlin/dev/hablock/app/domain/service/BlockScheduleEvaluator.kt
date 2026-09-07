package dev.hablock.app.domain.service

import dev.hablock.app.domain.model.BlockSchedule
import dev.hablock.app.domain.model.Weekday
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private const val SEARCH_DAYS = 8L

/** Resolves recurring wall-clock schedules to instants, including DST gaps and overlaps. */
class BlockScheduleEvaluator(
    private val zoneOverride: ZoneId? = null,
) {
    private val zone: ZoneId get() = zoneOverride ?: ZoneId.systemDefault()

    fun isActive(schedule: BlockSchedule?, now: Instant): Boolean {
        if (schedule == null) return true
        val zone = zone
        val today = now.atZone(zone).toLocalDate()
        return listOf(today.minusDays(1), today).any { startDate ->
            window(schedule, startDate, zone)?.contains(now) == true
        }
    }

    fun nextTransition(schedule: BlockSchedule?, now: Instant): Instant? {
        if (schedule == null || schedule.weekdays.isEmpty()) return null
        val zone = zone
        val today = now.atZone(zone).toLocalDate()
        return (-1L..SEARCH_DAYS)
            .mapNotNull { offset -> window(schedule, today.plusDays(offset), zone) }
            .flatMap { window -> listOf(window.start, window.end) }
            .filter { it > now }
            .minOrNull()
    }

    fun wasActiveDuring(schedule: BlockSchedule?, from: Instant, to: Instant): Boolean {
        require(to >= from) { "Schedule interval must not end before it starts" }
        if (schedule == null) return true
        val zone = zone
        val firstDate = from.atZone(zone).toLocalDate().minusDays(1)
        val lastDate = to.atZone(zone).toLocalDate()
        return generateSequence(firstDate) { date -> date.plusDays(1) }
            .takeWhile { date -> date <= lastDate }
            .mapNotNull { date -> window(schedule, date, zone) }
            .any { window -> window.start < to && window.end > from }
    }

    private fun window(schedule: BlockSchedule, startDate: LocalDate, zone: ZoneId): Window? {
        if (startDate.dayOfWeek.toWeekday() !in schedule.weekdays) return null
        val startTime = schedule.startMinute.toLocalTime()
        val endTime = schedule.endMinute.toLocalTime()
        val endDate = if (schedule.endMinute <= schedule.startMinute) startDate.plusDays(1) else startDate
        val start = ZonedDateTime.of(startDate, startTime, zone).toInstant()
        val end = ZonedDateTime.of(endDate, endTime, zone).toInstant()
        return if (end > start) Window(start, end) else null
    }

    private fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)

    private fun java.time.DayOfWeek.toWeekday(): Weekday = Weekday.valueOf(name)

    private data class Window(val start: Instant, val end: Instant) {
        fun contains(instant: Instant): Boolean = instant >= start && instant < end
    }
}
