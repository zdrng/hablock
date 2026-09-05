package dev.hablock.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class EmergencyPill(
    val available: Boolean = true,
    val refillAt: Long? = null,
)

@Serializable
data class EmergencyUnlockState(
    val pills: List<EmergencyPill> = List(2) { EmergencyPill() },
) {
    val availableCount: Int get() = pills.count { it.available }

    fun consume(now: Long, refillDuration: Long): EmergencyUnlockState? {
        val idx = pills.indexOfFirst { it.available }
        if (idx < 0) return null
        val refilled = pills.mapIndexed { i, pill ->
            if (i == idx) EmergencyPill(available = false, refillAt = now + refillDuration) else pill
        }
        return copy(pills = refilled)
    }

    fun refillExpired(now: Long): EmergencyUnlockState {
        val refilled = pills.map { pill ->
            if (!pill.available && pill.refillAt != null && pill.refillAt <= now) {
                EmergencyPill(available = true, refillAt = null)
            } else {
                pill
            }
        }
        return copy(pills = refilled)
    }
}
