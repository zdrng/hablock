package dev.hablock.app.domain.archive

import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.BlockSchedule
import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.DailyBlockHistory
import dev.hablock.app.domain.model.OverlapPolicy
import dev.hablock.app.domain.model.Weekday
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

const val CURRENT_ARCHIVE_VERSION = 1
private const val MAX_HISTORY_DAYS_PER_BLOCK = 365
private const val MINUTES_PER_DAY = 24 * 60

@Serializable
data class HablockArchive(
    val version: Int,
    val exportedAtMillis: Long,
    val settings: ArchiveSettings,
    val blocks: List<ArchiveBlock>,
    val history: List<DailyBlockHistory>,
)

@Serializable
data class ArchiveSettings(
    val overlapPolicy: ArchiveOverlapPolicy,
    val dayBoundaryMinutes: Int,
)

@Serializable
enum class ArchiveOverlapPolicy {
    @SerialName("all_blocks")
    ALL_BLOCKS,

    @SerialName("any_block")
    ANY_BLOCK,
}

/** Explicit allowlist of exportable Block fields. Lock state is deliberately absent. */
@Serializable
data class ArchiveBlock(
    val id: String,
    val name: String,
    val blockedPackages: List<String>,
    val blockedLabels: Map<String, String>,
    val conditions: List<Condition>,
    val thresholdN: Int,
    val incrementPct: Float,
    val unlockDurationMinutes: Int,
    val enabled: Boolean,
    val schedule: ArchiveSchedule? = null,
)

@Serializable
data class ArchiveSchedule(
    val weekdays: List<Weekday>,
    val startMinute: Int,
    val endMinute: Int,
)

data class ArchiveStoreSnapshot(
    val blocks: List<Block>,
    val history: List<DailyBlockHistory>,
    val overlapPolicy: OverlapPolicy,
    val dayBoundaryMinutes: Int,
)

data class ArchiveDestination(
    val blockIds: Set<String>,
    val historyKeys: Set<ArchiveHistoryKey>,
)

data class ArchiveHistoryKey(val blockId: String, val dayKey: String)

data class ArchiveStoreImportResult(
    val insertedBlocks: Int,
    val insertedHistory: Int,
    val restoredGlobalSettings: Boolean,
)

interface ArchiveStore {
    suspend fun snapshot(maxHistoryDaysPerBlock: Int = MAX_HISTORY_DAYS_PER_BLOCK): ArchiveStoreSnapshot
    suspend fun destination(): ArchiveDestination
    suspend fun importAdditive(
        blocks: List<Block>,
        history: List<DailyBlockHistory>,
        settings: ArchiveSettings,
    ): ArchiveStoreImportResult
}

data class ArchiveExportResult(
    val json: String,
    val blockCount: Int,
    val historyCount: Int,
)

data class ArchiveImportPreview(
    val version: Int,
    val blockCount: Int,
    val newBlockCount: Int,
    val skippedBlockCount: Int,
    val historyCount: Int,
    val newHistoryCount: Int,
    val skippedHistoryCount: Int,
    val willRestoreGlobalSettings: Boolean,
)

data class ArchiveImportResult(
    val insertedBlocks: Int,
    val skippedBlocks: Int,
    val insertedHistory: Int,
    val skippedHistory: Int,
    val restoredGlobalSettings: Boolean,
)

class ArchiveValidationException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class ArchiveService(
    private val store: ArchiveStore,
    private val forbiddenBlockedPackages: suspend () -> Set<String> = { emptySet() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
    },
) {
    suspend fun export(): ArchiveExportResult {
        val snapshot = store.snapshot()
        val archive = HablockArchive(
            version = CURRENT_ARCHIVE_VERSION,
            exportedAtMillis = nowMillis(),
            settings = ArchiveSettings(
                overlapPolicy = snapshot.overlapPolicy.toArchive(),
                dayBoundaryMinutes = snapshot.dayBoundaryMinutes,
            ),
            blocks = snapshot.blocks.map(Block::toArchive),
            history = snapshot.history,
        )
        validate(archive)
        return ArchiveExportResult(
            json = json.encodeToString(HablockArchive.serializer(), archive),
            blockCount = archive.blocks.size,
            historyCount = archive.history.size,
        )
    }

    suspend fun preview(rawJson: String): ArchiveImportPreview {
        val archive = decodeAndValidate(rawJson)
        val destination = store.destination()
        val newBlocks = archive.blocks.count { it.id !in destination.blockIds }
        val newHistory = archive.history.count {
            ArchiveHistoryKey(it.blockId, it.dayKey) !in destination.historyKeys
        }
        return ArchiveImportPreview(
            version = archive.version,
            blockCount = archive.blocks.size,
            newBlockCount = newBlocks,
            skippedBlockCount = archive.blocks.size - newBlocks,
            historyCount = archive.history.size,
            newHistoryCount = newHistory,
            skippedHistoryCount = archive.history.size - newHistory,
            willRestoreGlobalSettings = destination.blockIds.isEmpty(),
        )
    }

    suspend fun import(rawJson: String): ArchiveImportResult {
        val archive = decodeAndValidate(rawJson)
        val forbiddenPackages = forbiddenBlockedPackages()
        val result = store.importAdditive(
            blocks = archive.blocks.map { it.toEditableBlock(forbiddenPackages) },
            history = archive.history,
            settings = archive.settings,
        )
        return ArchiveImportResult(
            insertedBlocks = result.insertedBlocks,
            skippedBlocks = archive.blocks.size - result.insertedBlocks,
            insertedHistory = result.insertedHistory,
            skippedHistory = archive.history.size - result.insertedHistory,
            restoredGlobalSettings = result.restoredGlobalSettings,
        )
    }

    private fun decodeAndValidate(rawJson: String): HablockArchive {
        val archive = try {
            json.decodeFromString(HablockArchive.serializer(), rawJson)
        } catch (cause: SerializationException) {
            throw ArchiveValidationException("Archive is not valid Hablock JSON", cause)
        } catch (cause: IllegalArgumentException) {
            throw ArchiveValidationException("Archive contains an invalid value", cause)
        }
        validate(archive)
        return archive
    }

    private fun validate(archive: HablockArchive) {
        invalidUnless(archive.version == CURRENT_ARCHIVE_VERSION) {
            "Unsupported archive version ${archive.version}"
        }
        invalidUnless(archive.exportedAtMillis >= 0) { "Export timestamp must not be negative" }
        invalidUnless(archive.settings.dayBoundaryMinutes in 0 until MINUTES_PER_DAY) {
            "Day boundary must be from 0 to 1439"
        }
        invalidUnless(archive.blocks.map(ArchiveBlock::id).toSet().size == archive.blocks.size) {
            "Block IDs must be unique"
        }
        archive.blocks.forEach(::validateBlock)

        val historyKeys = archive.history.map { ArchiveHistoryKey(it.blockId, it.dayKey) }
        invalidUnless(historyKeys.toSet().size == historyKeys.size) { "History block/day keys must be unique" }
        invalidUnless(archive.history.groupingBy { it.blockId }.eachCount().values.all {
            it <= MAX_HISTORY_DAYS_PER_BLOCK
        }) { "History cannot contain more than $MAX_HISTORY_DAYS_PER_BLOCK days per block" }
        archive.history.forEach(::validateHistory)
    }

    private fun validateBlock(block: ArchiveBlock) {
        invalidUnless(block.id.isNotBlank()) { "Block ID must not be blank" }
        invalidUnless(block.name.isNotBlank()) { "Block name must not be blank" }
        invalidUnless(block.blockedPackages.all(String::isNotBlank)) { "Package names must not be blank" }
        invalidUnless(block.blockedPackages.toSet().size == block.blockedPackages.size) {
            "Blocked packages must be unique"
        }
        invalidUnless(block.blockedLabels.keys.all(String::isNotBlank)) { "Label package names must not be blank" }
        invalidUnless(block.conditions.map(Condition::id).all(String::isNotBlank)) {
            "Condition IDs must not be blank"
        }
        invalidUnless(block.conditions.map(Condition::id).toSet().size == block.conditions.size) {
            "Condition IDs must be unique within a block"
        }
        invalidUnless(block.thresholdN in 1..maxOf(1, block.conditions.size)) { "Invalid block threshold" }
        invalidUnless(block.incrementPct.isFinite() && block.incrementPct >= 0f) { "Invalid increment" }
        invalidUnless(block.unlockDurationMinutes > 0) { "Unlock duration must be positive" }
        block.conditions.forEach { condition ->
            invalidUnless(condition.goal.isFinite() && condition.goal > 0.0) { "Condition goals must be positive" }
            if (condition is Condition.AppUsage) {
                invalidUnless(condition.packageName.isNotBlank()) { "App-usage package must not be blank" }
                invalidUnless(condition.appLabel.isNotBlank()) { "App-usage label must not be blank" }
            }
        }
        block.schedule?.let { schedule ->
            invalidUnless(schedule.weekdays.toSet().size == schedule.weekdays.size) {
                "Schedule weekdays must be unique"
            }
            invalidUnless(schedule.startMinute in 0 until MINUTES_PER_DAY) { "Invalid schedule start" }
            invalidUnless(schedule.endMinute in 0 until MINUTES_PER_DAY) { "Invalid schedule end" }
        }
    }

    private fun validateHistory(day: DailyBlockHistory) {
        invalidUnless(day.blockId.isNotBlank()) { "History block ID must not be blank" }
        invalidUnless(runCatching { LocalDate.parse(day.dayKey).toString() == day.dayKey }.getOrDefault(false)) {
            "History day key must be an ISO date"
        }
        invalidUnless(day.blockName.isNotBlank()) { "History block name must not be blank" }
        invalidUnless(day.thresholdN in 1..maxOf(1, day.conditionCount)) { "Invalid history threshold" }
        invalidUnless(day.unlockCount >= 0) { "History unlock count must not be negative" }
        invalidUnless(day.finalizedAtMillis >= 0) { "History finalization timestamp must not be negative" }
        invalidUnless(day.conditions.map { it.conditionId }.toSet().size == day.conditions.size) {
            "History condition IDs must be unique within a day"
        }
        day.conditions.forEach { condition ->
            invalidUnless(condition.conditionId.isNotBlank()) { "History condition ID must not be blank" }
            invalidUnless(condition.label.isNotBlank()) { "History condition label must not be blank" }
            invalidUnless(condition.goal.isFinite() && condition.goal > 0.0) { "Invalid history goal" }
            invalidUnless(condition.required.isFinite() && condition.required >= 0.0) { "Invalid history requirement" }
            invalidUnless(condition.progress.isFinite() && condition.progress >= 0.0) { "Invalid history progress" }
        }
    }

    private inline fun invalidUnless(value: Boolean, message: () -> String) {
        if (!value) throw ArchiveValidationException(message())
    }
}

private fun Block.toArchive() = ArchiveBlock(
    id = id,
    name = name,
    blockedPackages = blockedPackages.sorted(),
    blockedLabels = blockedLabels.toSortedMap(),
    conditions = conditions,
    thresholdN = thresholdN,
    incrementPct = incrementPct,
    unlockDurationMinutes = unlockDurationMinutes,
    enabled = enabled,
    schedule = schedule?.let {
        ArchiveSchedule(it.weekdays.sortedBy(Weekday::ordinal), it.startMinute, it.endMinute)
    },
)

private fun ArchiveBlock.toEditableBlock(forbiddenPackages: Set<String>) = Block(
    id = id,
    name = name,
    blockedPackages = blockedPackages.filterNotTo(mutableSetOf()) { it in forbiddenPackages },
    blockedLabels = blockedLabels.filterKeys { it !in forbiddenPackages },
    conditions = conditions,
    thresholdN = thresholdN,
    incrementPct = incrementPct,
    unlockDurationMinutes = unlockDurationMinutes,
    enabled = enabled,
    blockedUntil = null,
    lockType = null,
    lockPasswordHash = null,
    schedule = schedule?.let {
        BlockSchedule(it.weekdays.toSet(), it.startMinute, it.endMinute)
    },
)

internal fun ArchiveOverlapPolicy.toDomain(): OverlapPolicy = when (this) {
    ArchiveOverlapPolicy.ALL_BLOCKS -> OverlapPolicy.ALL_BLOCKS
    ArchiveOverlapPolicy.ANY_BLOCK -> OverlapPolicy.ANY_BLOCK
}

private fun OverlapPolicy.toArchive(): ArchiveOverlapPolicy = when (this) {
    OverlapPolicy.ALL_BLOCKS -> ArchiveOverlapPolicy.ALL_BLOCKS
    OverlapPolicy.ANY_BLOCK -> ArchiveOverlapPolicy.ANY_BLOCK
}
