package dev.hablock.app.domain.service

import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.BlockDayState
import dev.hablock.app.domain.model.MetricSnapshot
import dev.hablock.app.domain.model.Session
import java.time.Instant
import kotlin.time.Duration

/** The ratchet: unlocking consumes the effort that met each requirement and raises the bar by a slice of the base goal. */
object SessionMath {

    fun startSession(
        block: Block,
        dayState: BlockDayState,
        snapshot: MetricSnapshot,
        now: Instant,
        sessionDuration: Duration,
    ): BlockDayState {
        val requiredNow = block.conditions.associate { condition ->
            val required = dayState.requiredNow[condition.id] ?: condition.goal
            val current = snapshot.valueOf(condition.id)
            val next = if (current >= required) current + block.incrementPct * condition.goal else required
            condition.id to next
        }
        val endsAt = now.plusMillis(sessionDuration.inWholeMilliseconds)
        return dayState.copy(
            requiredNow = requiredNow,
            activeSession = Session(block.id, now.toEpochMilli(), endsAt.toEpochMilli()),
            unlockCount = dayState.unlockCount + 1,
        )
    }
}
