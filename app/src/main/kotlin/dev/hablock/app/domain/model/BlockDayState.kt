package dev.hablock.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val blockId: String,
    val startedAtMillis: Long,
    val endsAtMillis: Long,
)

/** Per-block state for one day; requiredNow maps conditionId to the current (possibly ratcheted) requirement. */
@Serializable
data class BlockDayState(
    val blockId: String,
    val dayKey: String,
    val requiredNow: Map<String, Double>,
    val activeSession: Session? = null,
    val unlockCount: Int = 0,
)
