package dev.hablock.app.domain.model

import kotlinx.serialization.Serializable

private const val MINUTES_PER_DAY = 24 * 60

@Serializable
enum class Weekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
}

/** One recurring local-time window. Overnight windows belong to their selected start weekday. */
@Serializable
data class BlockSchedule(
    val weekdays: Set<Weekday>,
    val startMinute: Int,
    val endMinute: Int,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute must be from 0 to 1439" }
        require(endMinute in 0 until MINUTES_PER_DAY) { "endMinute must be from 0 to 1439" }
    }
}
