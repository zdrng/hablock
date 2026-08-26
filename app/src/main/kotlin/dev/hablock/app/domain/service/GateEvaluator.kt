package dev.hablock.app.domain.service

import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.BlockDayState
import dev.hablock.app.domain.model.ConditionProgress
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.model.MetricSnapshot
import java.time.Instant

/** Pure N-of-M gate decision; a block without conditions is permanently locked. */
class GateEvaluator {

    fun evaluate(
        block: Block,
        dayState: BlockDayState?,
        snapshot: MetricSnapshot,
        now: Instant,
    ): GateState {
        val progress = progressOf(block, dayState, snapshot)
        val session = dayState?.activeSession
        if (session != null && now.toEpochMilli() < session.endsAtMillis) {
            return GateState.SessionActive(progress, Instant.ofEpochMilli(session.endsAtMillis))
        }
        if (block.conditions.isEmpty()) return GateState.Locked(emptyList(), block.thresholdN)
        val threshold = effectiveThreshold(block)
        val met = progress.count { it.met }
        return if (met >= threshold) {
            GateState.Open(progress, threshold)
        } else {
            GateState.Locked(progress, threshold)
        }
    }

    fun effectiveThreshold(block: Block): Int =
        if (block.conditions.isEmpty()) block.thresholdN else block.thresholdN.coerceIn(1, block.conditions.size)

    private fun progressOf(
        block: Block,
        dayState: BlockDayState?,
        snapshot: MetricSnapshot,
    ): List<ConditionProgress> = block.conditions.map { condition ->
        ConditionProgress(
            condition = condition,
            current = snapshot.valueOf(condition.id),
            required = dayState?.requiredNow?.get(condition.id) ?: condition.goal,
        )
    }
}
