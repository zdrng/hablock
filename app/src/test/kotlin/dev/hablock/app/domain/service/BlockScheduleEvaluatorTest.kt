package dev.hablock.app.domain.service

import dev.hablock.app.domain.model.BlockSchedule
import dev.hablock.app.domain.model.Weekday
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockScheduleEvaluatorTest {

    private val utc = BlockScheduleEvaluator(ZoneId.of("UTC"))

    @Test
    fun `missing schedule is always active and has no transition`() {
        val now = Instant.parse("2026-09-07T12:00:00Z")

        assertTrue(utc.isActive(null, now))
        assertNull(utc.nextTransition(null, now))
    }

    @Test
    fun `same-day window includes start and excludes end`() {
        val schedule = schedule(setOf(Weekday.MONDAY), 9 * 60, 17 * 60)

        assertFalse(utc.isActive(schedule, Instant.parse("2026-09-07T08:59:59Z")))
        assertTrue(utc.isActive(schedule, Instant.parse("2026-09-07T09:00:00Z")))
        assertTrue(utc.isActive(schedule, Instant.parse("2026-09-07T16:59:59Z")))
        assertFalse(utc.isActive(schedule, Instant.parse("2026-09-07T17:00:00Z")))
    }

    @Test
    fun `overnight window uses the weekday on which it starts`() {
        val schedule = schedule(setOf(Weekday.MONDAY), 22 * 60, 6 * 60)

        assertTrue(utc.isActive(schedule, Instant.parse("2026-09-07T23:00:00Z")))
        assertTrue(utc.isActive(schedule, Instant.parse("2026-09-08T05:59:59Z")))
        assertFalse(utc.isActive(schedule, Instant.parse("2026-09-08T06:00:00Z")))
        assertFalse(utc.isActive(schedule, Instant.parse("2026-09-09T05:00:00Z")))
    }

    @Test
    fun `equal start and end represents a full day from the selected weekday`() {
        val schedule = schedule(setOf(Weekday.MONDAY), 8 * 60, 8 * 60)

        assertTrue(utc.isActive(schedule, Instant.parse("2026-09-07T08:00:00Z")))
        assertTrue(utc.isActive(schedule, Instant.parse("2026-09-08T07:59:59Z")))
        assertFalse(utc.isActive(schedule, Instant.parse("2026-09-08T08:00:00Z")))
    }

    @Test
    fun `next transition returns the next start or end across the week`() {
        val schedule = schedule(setOf(Weekday.MONDAY, Weekday.FRIDAY), 9 * 60, 17 * 60)

        assertEquals(
            Instant.parse("2026-09-07T09:00:00Z"),
            utc.nextTransition(schedule, Instant.parse("2026-09-07T08:00:00Z")),
        )
        assertEquals(
            Instant.parse("2026-09-07T17:00:00Z"),
            utc.nextTransition(schedule, Instant.parse("2026-09-07T12:00:00Z")),
        )
        assertEquals(
            Instant.parse("2026-09-11T09:00:00Z"),
            utc.nextTransition(schedule, Instant.parse("2026-09-07T17:00:00Z")),
        )
    }

    @Test
    fun `empty weekday selection is never active`() {
        val schedule = schedule(emptySet(), 9 * 60, 17 * 60)

        assertFalse(utc.isActive(schedule, Instant.parse("2026-09-07T12:00:00Z")))
        assertNull(utc.nextTransition(schedule, Instant.parse("2026-09-07T12:00:00Z")))
    }

    @Test
    fun `historical interval reports whether a scheduled window occurred`() {
        val monday = schedule(setOf(Weekday.MONDAY), 9 * 60, 17 * 60)

        assertTrue(
            utc.wasActiveDuring(
                monday,
                Instant.parse("2026-09-07T00:00:00Z"),
                Instant.parse("2026-09-08T00:00:00Z"),
            ),
        )
        assertFalse(
            utc.wasActiveDuring(
                monday,
                Instant.parse("2026-09-08T00:00:00Z"),
                Instant.parse("2026-09-09T00:00:00Z"),
            ),
        )
    }

    @Test
    fun `spring gap shifts a missing start time forward`() {
        val berlin = BlockScheduleEvaluator(ZoneId.of("Europe/Berlin"))
        val schedule = schedule(setOf(Weekday.SUNDAY), 2 * 60 + 30, 4 * 60)

        assertFalse(berlin.isActive(schedule, Instant.parse("2026-03-29T01:15:00Z")))
        assertTrue(berlin.isActive(schedule, Instant.parse("2026-03-29T01:30:00Z")))
        assertFalse(berlin.isActive(schedule, Instant.parse("2026-03-29T02:00:00Z")))
        assertEquals(
            Instant.parse("2026-03-29T01:30:00Z"),
            berlin.nextTransition(schedule, Instant.parse("2026-03-29T01:15:00Z")),
        )
    }

    @Test
    fun `fall overlap forms one continuous window from the earlier start offset`() {
        val berlin = BlockScheduleEvaluator(ZoneId.of("Europe/Berlin"))
        val schedule = schedule(setOf(Weekday.SUNDAY), 2 * 60 + 30, 3 * 60)

        assertFalse(berlin.isActive(schedule, Instant.parse("2026-10-25T00:15:00Z")))
        assertTrue(berlin.isActive(schedule, Instant.parse("2026-10-25T00:30:00Z")))
        assertTrue(berlin.isActive(schedule, Instant.parse("2026-10-25T01:15:00Z")))
        assertFalse(berlin.isActive(schedule, Instant.parse("2026-10-25T02:00:00Z")))
    }

    private fun schedule(weekdays: Set<Weekday>, start: Int, end: Int) = BlockSchedule(
        weekdays = weekdays,
        startMinute = start,
        endMinute = end,
    )
}
