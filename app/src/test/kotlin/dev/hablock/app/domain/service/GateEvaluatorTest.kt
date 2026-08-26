package dev.hablock.app.domain.service

import dev.hablock.app.domain.model.BlockDayState
import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.model.Session
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GateEvaluatorTest {

    private val evaluator = GateEvaluator()
    private val now: Instant = Instant.parse("2026-08-23T10:00:00Z")

    private val conditions = listOf(
        Condition.Steps("steps", 10_000.0),
        Condition.Meditation("meditation", 10.0),
        Condition.Exercise("workout", 60.0),
    )

    @Test
    fun `n of m matrix over every threshold and met count`() {
        val metValues = listOf(
            snapshotOf("steps" to 0.0, "meditation" to 0.0, "workout" to 0.0),
            snapshotOf("steps" to 10_000.0, "meditation" to 0.0, "workout" to 0.0),
            snapshotOf("steps" to 10_000.0, "meditation" to 10.0, "workout" to 0.0),
            snapshotOf("steps" to 10_000.0, "meditation" to 10.0, "workout" to 60.0),
        )
        for (threshold in 1..conditions.size) {
            val block = testBlock(conditions = conditions, thresholdN = threshold)
            for (metCount in metValues.indices) {
                val state = evaluator.evaluate(block, null, metValues[metCount], now)
                if (metCount >= threshold) {
                    assertIs<GateState.Open>(state, "threshold=$threshold met=$metCount")
                } else {
                    assertIs<GateState.Locked>(state, "threshold=$threshold met=$metCount")
                }
                assertEquals(metCount, state.metCount)
            }
        }
    }

    @Test
    fun `value exactly equal to requirement counts as met`() {
        val block = testBlock(conditions = conditions, thresholdN = 3)
        val snapshot = snapshotOf("steps" to 10_000.0, "meditation" to 10.0, "workout" to 60.0)
        val state = evaluator.evaluate(block, null, snapshot, now)
        assertIs<GateState.Open>(state)
        assertTrue(state.progress.all { it.met })
    }

    @Test
    fun `one unit short of requirement is not met`() {
        val block = testBlock(conditions = conditions, thresholdN = 1)
        val snapshot = snapshotOf("steps" to 9_999.0, "meditation" to 9.9, "workout" to 59.9)
        assertIs<GateState.Locked>(evaluator.evaluate(block, null, snapshot, now))
    }

    @Test
    fun `block without conditions is permanently locked`() {
        val block = testBlock(conditions = emptyList(), thresholdN = 0)
        val state = evaluator.evaluate(block, null, snapshotOf(), now)
        assertIs<GateState.Locked>(state)
        assertTrue(state.progress.isEmpty())
    }

    @Test
    fun `threshold above condition count is coerced to condition count`() {
        val two = conditions.take(2)
        val block = testBlock(conditions = two, thresholdN = 5)
        assertEquals(2, evaluator.effectiveThreshold(block))
        val allMet = snapshotOf("steps" to 10_000.0, "meditation" to 10.0)
        assertIs<GateState.Open>(evaluator.evaluate(block, null, allMet, now))
        val oneMet = snapshotOf("steps" to 10_000.0, "meditation" to 0.0)
        assertIs<GateState.Locked>(evaluator.evaluate(block, null, oneMet, now))
    }

    @Test
    fun `threshold below one is coerced to one`() {
        val block = testBlock(conditions = conditions, thresholdN = 0)
        assertEquals(1, evaluator.effectiveThreshold(block))
        assertIs<GateState.Locked>(evaluator.evaluate(block, null, snapshotOf(), now))
    }

    @Test
    fun `ratcheted requirement overrides the base goal`() {
        val block = testBlock(conditions = conditions, thresholdN = 1)
        val dayState = dayState(requiredNow = mapOf("steps" to 11_300.0, "meditation" to 10.0, "workout" to 60.0))
        val snapshot = snapshotOf("steps" to 10_500.0)
        val state = evaluator.evaluate(block, dayState, snapshot, now)
        assertIs<GateState.Locked>(state)
        assertEquals(11_300.0, state.progress.first().required)
    }

    @Test
    fun `active session wins over an otherwise open gate`() {
        val block = testBlock(conditions = conditions, thresholdN = 3)
        val endsAt = now.plusSeconds(600)
        val dayState = dayState(session = Session("b1", now.toEpochMilli(), endsAt.toEpochMilli()))
        val snapshot = snapshotOf("steps" to 10_000.0, "meditation" to 10.0, "workout" to 60.0)
        val state = evaluator.evaluate(block, dayState, snapshot, now)
        assertIs<GateState.SessionActive>(state)
        assertEquals(endsAt, state.endsAt)
    }

    @Test
    fun `active session wins over an otherwise locked gate`() {
        val block = testBlock(conditions = conditions, thresholdN = 3)
        val dayState = dayState(session = Session("b1", now.toEpochMilli(), now.plusSeconds(1).toEpochMilli()))
        assertIs<GateState.SessionActive>(evaluator.evaluate(block, dayState, snapshotOf(), now))
    }

    @Test
    fun `expired session is not session active`() {
        val block = testBlock(conditions = conditions, thresholdN = 1)
        val dayState = dayState(
            requiredNow = mapOf("steps" to 10_000.0, "meditation" to 10.0, "workout" to 60.0),
            session = Session("b1", now.minusSeconds(1800).toEpochMilli(), now.toEpochMilli()),
        )
        val snapshot = snapshotOf("steps" to 10_000.0)
        assertIs<GateState.Open>(evaluator.evaluate(block, dayState, snapshot, now))
    }

    private fun dayState(
        requiredNow: Map<String, Double> = conditions.associate { it.id to it.goal },
        session: Session? = null,
    ) = BlockDayState(blockId = "b1", dayKey = "2026-08-23", requiredNow = requiredNow, activeSession = session)
}
