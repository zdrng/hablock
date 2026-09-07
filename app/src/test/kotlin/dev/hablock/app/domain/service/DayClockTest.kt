package dev.hablock.app.domain.service

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DayClockTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    private fun clockAt(
        instant: String,
        boundaryMinutesProvider: () -> Int = { 0 },
    ) = DayClock(
        clock = Clock.fixed(Instant.parse(instant), zone),
        zoneOverride = zone,
        boundaryMinutesProvider = boundaryMinutesProvider,
    )

    @Test
    fun `just before local midnight belongs to the ending gate day`() {
        val clock = clockAt("2026-08-22T21:59:00Z")
        assertEquals("2026-08-22", clock.dayKey())
        assertEquals(Instant.parse("2026-08-21T22:00:00Z"), clock.dayStart())
    }

    @Test
    fun `local midnight starts the next gate day`() {
        val clock = clockAt("2026-08-22T22:00:00Z")
        assertEquals("2026-08-23", clock.dayKey())
        assertEquals(Instant.parse("2026-08-22T22:00:00Z"), clock.dayStart())
    }

    @Test
    fun `the small hours belong to the current gate day`() {
        val clock = clockAt("2026-08-23T01:00:00Z")
        assertEquals("2026-08-23", clock.dayKey())
    }

    @Test
    fun `day window runs from the day start to now`() {
        val now = Instant.parse("2026-08-23T09:30:00Z")
        val clock = clockAt("2026-08-23T09:30:00Z")
        val (from, to) = clock.dayWindow()
        assertEquals(Instant.parse("2026-08-22T22:00:00Z"), from)
        assertEquals(now, to)
    }

    @Test
    fun `historical day window spans consecutive configured boundaries`() {
        val clock = clockAt("2026-03-30T12:00:00Z") { 2 * 60 + 30 }

        assertEquals(
            Instant.parse("2026-03-29T01:30:00Z") to Instant.parse("2026-03-30T00:30:00Z"),
            clock.dayWindow("2026-03-29"),
        )
    }

    @Test
    fun `next reset is always strictly in the future`() {
        var instant = Instant.parse("2026-08-22T01:00:00Z")
        repeat(48) {
            val clock = DayClock(Clock.fixed(instant, zone), zone)
            assertTrue(clock.nextReset() > instant, "nextReset not in the future at $instant")
            instant = instant.plusSeconds(3600)
        }
    }

    @Test
    fun `next reset from the reset instant is the following day`() {
        val clock = clockAt("2026-08-22T22:00:00Z")
        assertEquals(Instant.parse("2026-08-23T22:00:00Z"), clock.nextReset())
    }

    @Test
    fun `time before a configured boundary belongs to the previous Hablock day`() {
        val clock = clockAt("2026-08-23T02:29:59Z") { 4 * 60 + 30 }

        assertEquals("2026-08-22", clock.dayKey())
        assertEquals(Instant.parse("2026-08-22T02:30:00Z"), clock.dayStart())
        assertEquals(Instant.parse("2026-08-23T02:30:00Z"), clock.nextReset())
    }

    @Test
    fun `configured boundary instant starts the next Hablock day`() {
        val now = Instant.parse("2026-08-23T02:30:00Z")
        val clock = clockAt(now.toString()) { 4 * 60 + 30 }

        assertEquals("2026-08-23", clock.dayKey())
        assertEquals(now, clock.dayStart())
        assertEquals(now to now, clock.dayWindow())
        assertEquals(Instant.parse("2026-08-24T02:30:00Z"), clock.nextReset())
    }

    @Test
    fun `day window follows a configured boundary`() {
        val now = Instant.parse("2026-08-23T09:30:00Z")
        val clock = clockAt(now.toString()) { 4 * 60 + 30 }

        assertEquals(Instant.parse("2026-08-23T02:30:00Z") to now, clock.dayWindow())
    }

    @Test
    fun `provider changes affect the existing clock instance`() {
        var boundaryMinutes = 0
        val clock = clockAt("2026-08-23T01:00:00Z") { boundaryMinutes }

        assertEquals("2026-08-23", clock.dayKey())
        assertEquals(Instant.parse("2026-08-22T22:00:00Z"), clock.dayStart())

        boundaryMinutes = 4 * 60

        assertEquals("2026-08-22", clock.dayKey())
        assertEquals(Instant.parse("2026-08-22T02:00:00Z"), clock.dayStart())
        assertEquals(Instant.parse("2026-08-23T02:00:00Z"), clock.nextReset())
    }

    @Test
    fun `invalid boundary values are rejected when read`() {
        assertFailsWith<IllegalArgumentException> {
            clockAt("2026-08-23T09:30:00Z") { -1 }.dayStart()
        }
        assertFailsWith<IllegalArgumentException> {
            clockAt("2026-08-23T09:30:00Z") { 24 * 60 }.nextReset()
        }
    }

    @Test
    fun `boundary in a spring DST gap resolves forward without starting the day early`() {
        // Europe/Berlin skips from 00:59:59Z (01:59:59 local) to 03:00 local.
        // A 02:30 boundary therefore resolves to 03:30 local (01:30Z).
        val beforeResolvedBoundary = clockAt("2026-03-29T01:15:00Z") { 2 * 60 + 30 }

        assertEquals("2026-03-28", beforeResolvedBoundary.dayKey())
        assertEquals(Instant.parse("2026-03-28T01:30:00Z"), beforeResolvedBoundary.dayStart())
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), beforeResolvedBoundary.nextReset())

        val atResolvedBoundary = clockAt("2026-03-29T01:30:00Z") { 2 * 60 + 30 }
        assertEquals("2026-03-29", atResolvedBoundary.dayKey())
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), atResolvedBoundary.dayStart())
        assertEquals(Instant.parse("2026-03-30T00:30:00Z"), atResolvedBoundary.nextReset())
        assertEquals(
            Duration.ofHours(23),
            Duration.between(atResolvedBoundary.dayStart(), atResolvedBoundary.nextReset()),
        )
    }

    @Test
    fun `boundary in a fall DST overlap happens once at the earlier offset`() {
        // Europe/Berlin repeats 02:00-02:59. ZonedDateTime resolves 02:30 at the
        // earlier summer offset, so the repeated 02:15 is already in the new day.
        val beforeFirstBoundary = clockAt("2026-10-25T00:15:00Z") { 2 * 60 + 30 }
        assertEquals("2026-10-24", beforeFirstBoundary.dayKey())
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), beforeFirstBoundary.nextReset())

        val repeatedTime = clockAt("2026-10-25T01:15:00Z") { 2 * 60 + 30 }
        assertEquals("2026-10-25", repeatedTime.dayKey())
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), repeatedTime.dayStart())
        assertEquals(Instant.parse("2026-10-26T01:30:00Z"), repeatedTime.nextReset())
        assertEquals(
            Duration.ofHours(25),
            Duration.between(repeatedTime.dayStart(), repeatedTime.nextReset()),
        )
    }
}
