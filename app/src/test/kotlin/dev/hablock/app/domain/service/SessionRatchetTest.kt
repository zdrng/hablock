package dev.hablock.app.domain.service

import dev.hablock.app.domain.model.BlockDayState
import dev.hablock.app.domain.model.Condition
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

private const val EPS = 1e-3

class SessionRatchetTest {

    private val conditions = listOf(
        Condition.Steps("steps", 10_000.0),
        Condition.Meditation("meditation", 10.0),
        Condition.Exercise("workout", 60.0),
    )
    private val block = testBlock(conditions = conditions, thresholdN = 2, incrementPct = 0.10f)
    private val now: Instant = Instant.parse("2026-08-23T10:00:00Z")
    private val fresh = BlockDayState(
        blockId = block.id,
        dayKey = "2026-08-23",
        requiredNow = conditions.associate { it.id to it.goal },
    )

    @Test
    fun `met conditions ratchet from the observed value and unmet ones do not move`() {
        val snapshot = snapshotOf("steps" to 10_300.0, "meditation" to 0.0, "workout" to 61.0)
        val next = SessionMath.startSession(block, fresh, snapshot, now, 30.minutes)

        assertEquals(11_300.0, next.requiredNow.getValue("steps"), EPS)
        assertEquals(67.0, next.requiredNow.getValue("workout"), EPS)
        assertEquals(10.0, next.requiredNow.getValue("meditation"), EPS)
    }

    @Test
    fun `unlock count increments and a session of the configured length is opened`() {
        val snapshot = snapshotOf("steps" to 10_300.0, "workout" to 61.0)
        val next = SessionMath.startSession(block, fresh, snapshot, now, 30.minutes)

        assertEquals(1, next.unlockCount)
        val session = assertNotNull(next.activeSession)
        assertEquals(block.id, session.blockId)
        assertEquals(now.toEpochMilli(), session.startedAtMillis)
        assertEquals(now.plusSeconds(1800).toEpochMilli(), session.endsAtMillis)
    }

    @Test
    fun `a second cycle ratchets again from the new snapshot`() {
        val first = SessionMath.startSession(
            block,
            fresh,
            snapshotOf("steps" to 10_300.0, "meditation" to 0.0, "workout" to 61.0),
            now,
            30.minutes,
        )
        val later = now.plusSeconds(7200)
        val second = SessionMath.startSession(
            block,
            first.copy(activeSession = null),
            snapshotOf("steps" to 11_500.0, "meditation" to 0.0, "workout" to 70.0),
            later,
            30.minutes,
        )

        assertEquals(12_500.0, second.requiredNow.getValue("steps"), EPS)
        assertEquals(76.0, second.requiredNow.getValue("workout"), EPS)
        assertEquals(10.0, second.requiredNow.getValue("meditation"), EPS)
        assertEquals(2, second.unlockCount)
    }

    @Test
    fun `overshoot is consumed rather than banked toward the next unlock`() {
        val next = SessionMath.startSession(
            block,
            fresh,
            snapshotOf("steps" to 25_000.0, "workout" to 61.0),
            now,
            30.minutes,
        )
        assertEquals(26_000.0, next.requiredNow.getValue("steps"), EPS)
    }

    @Test
    fun `an unmet condition keeps its previous requirement across unlocks`() {
        val carried = fresh.copy(requiredNow = fresh.requiredNow + ("meditation" to 21.0))
        val next = SessionMath.startSession(
            block,
            carried,
            snapshotOf("steps" to 10_000.0, "meditation" to 20.0, "workout" to 60.0),
            now,
            30.minutes,
        )
        assertEquals(21.0, next.requiredNow.getValue("meditation"), EPS)
        assertEquals(11_000.0, next.requiredNow.getValue("steps"), EPS)
        assertEquals(66.0, next.requiredNow.getValue("workout"), EPS)
    }
}
