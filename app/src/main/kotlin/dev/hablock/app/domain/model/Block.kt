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
    val enabled: Boolean = true,
)
