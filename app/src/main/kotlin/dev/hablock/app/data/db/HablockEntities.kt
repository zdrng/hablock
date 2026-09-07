package dev.hablock.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "blocks",
    indices = [Index("position")],
)
data class BlockEntity(
    @androidx.room.PrimaryKey val id: String,
    val position: Long,
    val name: String,
    val thresholdN: Int,
    val incrementPct: Float,
    val unlockDurationMinutes: Int,
    val enabled: Boolean,
    val blockedUntil: Long?,
    /** Stable wire value: "duration", "password", or null. */
    val lockType: String?,
    val lockPasswordHash: String?,
)

/** A row can retain a label independently of whether that package is currently selected. */
@Entity(
    tableName = "block_apps",
    primaryKeys = ["blockId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = BlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("blockId")],
)
data class BlockAppEntity(
    val blockId: String,
    val packageName: String,
    val blocked: Boolean,
    val label: String?,
)

@Entity(
    tableName = "conditions",
    primaryKeys = ["blockId", "conditionId"],
    foreignKeys = [
        ForeignKey(
            entity = BlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("blockId"),
        Index(value = ["blockId", "position"], unique = true),
    ],
)
data class ConditionEntity(
    val blockId: String,
    val conditionId: String,
    /** The position is part of persistence because threshold ties make ordering user-visible. */
    val position: Int,
    /** Stable wire value; see [ConditionKind]. */
    val kind: String,
    val goal: Double,
    val packageName: String?,
    val appLabel: String?,
)

object ConditionKind {
    const val APP_USAGE = "app_usage"
    const val STEPS = "steps"
    const val EXERCISE = "exercise"
    const val MEDITATION = "meditation"
}

/**
 * Forward-compatible storage for one recurring local-time window per block.
 *
 * [weekdays] uses bits 0..6 for Monday..Sunday. Start and end are minutes after local midnight;
 * an end at or before the start represents a window crossing midnight.
 */
@Entity(
    tableName = "block_schedules",
    foreignKeys = [
        ForeignKey(
            entity = BlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ScheduleEntity(
    @androidx.room.PrimaryKey val blockId: String,
    val enabled: Boolean,
    val weekdays: Int,
    val startMinute: Int,
    val endMinute: Int,
)

@Entity(
    tableName = "block_day_states",
    foreignKeys = [
        ForeignKey(
            entity = BlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BlockDayStateEntity(
    @androidx.room.PrimaryKey val blockId: String,
    val dayKey: String,
    val sessionStartedAtMillis: Long?,
    val sessionEndsAtMillis: Long?,
    val unlockCount: Int,
    val emergencyUnlockUsed: Boolean,
)

@Entity(
    tableName = "day_state_requirements",
    primaryKeys = ["blockId", "conditionId"],
    foreignKeys = [
        ForeignKey(
            entity = BlockDayStateEntity::class,
            parentColumns = ["blockId"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("blockId")],
)
data class RequirementEntity(
    val blockId: String,
    val conditionId: String,
    val required: Double,
)

@Entity(
    tableName = "daily_block_history",
    primaryKeys = ["blockId", "dayKey"],
    indices = [Index("blockId"), Index("dayKey")],
)
data class DailyBlockHistoryEntity(
    val blockId: String,
    /** ISO-8601 local date (yyyy-MM-dd), so lexical order is chronological. */
    val dayKey: String,
    val blockName: String,
    val conditionCount: Int,
    val metCount: Int,
    val thresholdN: Int,
    val unlockCount: Int,
    val emergencyUnlockUsed: Boolean,
    val scheduledActive: Boolean,
    val finalizedAt: Long,
)

@Entity(
    tableName = "daily_condition_history",
    primaryKeys = ["blockId", "dayKey", "conditionId"],
    foreignKeys = [
        ForeignKey(
            entity = DailyBlockHistoryEntity::class,
            parentColumns = ["blockId", "dayKey"],
            childColumns = ["blockId", "dayKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["blockId", "dayKey"])],
)
data class DailyConditionHistoryEntity(
    val blockId: String,
    val dayKey: String,
    val conditionId: String,
    val kind: String,
    val label: String,
    val goal: Double,
    val required: Double,
    val progress: Double,
    val met: Boolean,
)
