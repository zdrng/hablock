package dev.hablock.app.domain.model

import java.time.Instant

data class MetricSnapshot(val values: Map<String, Double>) {
    fun valueOf(conditionId: String): Double = values[conditionId] ?: 0.0
}

sealed interface RelinquishState {
    data object Idle : RelinquishState
    data class Counting(val deadline: Instant) : RelinquishState
    data object Ready : RelinquishState
}

enum class HcAvailability { FULL, NO_MINDFULNESS, NEEDS_INSTALL, UNAVAILABLE }

data class AppUsageEntry(val packageName: String, val minutes: Double)

data class InstalledApp(val packageName: String, val label: String)
