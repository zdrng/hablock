package dev.hablock.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Block(
    val id: String,
    val name: String,
    val blockedPackages: Set<String>,
    val blockedLabels: Map<String, String> = emptyMap(),
    val conditions: List<Condition>,
    val thresholdN: Int,
    val incrementPct: Float,
    val unlockDurationMinutes: Int = 30,
    val enabled: Boolean = true,
    val blockedUntil: Long? = null,
    val lockType: LockType? = null,
    val lockPasswordHash: String? = null,
)

fun Block.isChangesLocked(now: Long = System.currentTimeMillis()): Boolean =
    when (lockType) {
        LockType.PASSWORD -> true
        LockType.DURATION -> blockedUntil != null && blockedUntil > now
        null -> blockedUntil != null && blockedUntil > now
    }
