package dev.hablock.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HistoryConditionKind {
    @SerialName("app_usage")
    APP_USAGE,

    @SerialName("steps")
    STEPS,

    @SerialName("exercise")
    EXERCISE,

    @SerialName("meditation")
    MEDITATION,
}

/** Final progress for one condition during a completed Hablock day. */
@Serializable
data class DailyConditionHistory(
    val conditionId: String,
    val kind: HistoryConditionKind,
    val label: String,
    val goal: Double,
    val required: Double,
    val progress: Double,
    val met: Boolean,
)

/** Immutable summary of one block during one completed Hablock day. */
@Serializable
data class DailyBlockHistory(
    val blockId: String,
    /** ISO-8601 local date assigned by DayClock. */
    val dayKey: String,
    val blockName: String,
    val conditions: List<DailyConditionHistory>,
    val conditionCount: Int = conditions.size,
    val metCount: Int = conditions.count(DailyConditionHistory::met),
    val thresholdN: Int,
    val unlockCount: Int,
    val emergencyUnlockUsed: Boolean,
    val scheduledActive: Boolean,
    val finalizedAtMillis: Long,
) {
    init {
        require(conditionCount == conditions.size) {
            "Condition count must match the number of condition summaries"
        }
        require(metCount == conditions.count(DailyConditionHistory::met)) {
            "Met count must match the condition summaries"
        }
    }
}
