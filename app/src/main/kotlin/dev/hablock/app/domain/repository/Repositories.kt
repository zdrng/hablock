package dev.hablock.app.domain.repository

import dev.hablock.app.domain.model.AppUsageEntry
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.BlockDayState
import dev.hablock.app.domain.model.DailyBlockHistory
import dev.hablock.app.domain.model.EmergencyUnlockState
import dev.hablock.app.domain.model.HcAvailability
import dev.hablock.app.domain.model.InstalledApp
import dev.hablock.app.domain.model.OverlapPolicy
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface BlockRepository {
    val blocks: Flow<List<Block>>
    suspend fun current(): List<Block>
    suspend fun upsert(block: Block)
    suspend fun delete(blockId: String)
}

interface GateStateRepository {
    val dayStates: Flow<Map<String, BlockDayState>>
    suspend fun get(blockId: String): BlockDayState?
    suspend fun save(state: BlockDayState)
    suspend fun delete(blockId: String)
    suspend fun clearAll()
}

interface HistoryRepository {
    /** Observes the newest summaries for one block, newest Hablock day first. */
    fun observe(blockId: String, limit: Int = 365): Flow<List<DailyBlockHistory>>

    /** Queries the newest summaries for one block, newest Hablock day first. */
    suspend fun latest(blockId: String, limit: Int = 365): List<DailyBlockHistory>

    /** Replaces the summary for the same block/day key; repeating an identical save is a no-op in effect. */
    suspend fun save(summary: DailyBlockHistory)

    /** Inserts only block/day keys not already present and returns the number added. */
    suspend fun importNew(summaries: List<DailyBlockHistory>): Int

    /** Deletes summaries older than the supplied ISO Hablock day key. */
    suspend fun pruneBefore(oldestDayKey: String)
}

interface SettingsRepository {
    val onboardingDone: Flow<Boolean>
    suspend fun setOnboardingDone()
    val relinquishDeadlineMillis: Flow<Long?>
    suspend fun setRelinquishDeadline(millis: Long?)
    val emergencyUnlocks: Flow<EmergencyUnlockState>
    suspend fun setEmergencyUnlocks(state: EmergencyUnlockState)
    val overlapPolicy: Flow<OverlapPolicy>
    suspend fun setOverlapPolicy(policy: OverlapPolicy)
    val dayBoundaryMinutes: Flow<Int>
    suspend fun setDayBoundaryMinutes(minutes: Int)
}

interface UsageStatsRepository {
    suspend fun foregroundMinutes(packageName: String, from: Instant, to: Instant): Double
    suspend fun usageByApp(from: Instant, to: Instant): List<AppUsageEntry>
}

interface HealthRepository {
    suspend fun availability(): HcAvailability
    suspend fun hasAllPermissions(): Boolean
    fun requiredPermissions(): Set<String>
    suspend fun steps(from: Instant, to: Instant): Double
    suspend fun exerciseMinutes(from: Instant, to: Instant): Double
    suspend fun mindfulMinutes(from: Instant, to: Instant): Double
}

interface InstalledAppsRepository {
    suspend fun launcherApps(): List<InstalledApp>
    suspend fun homePackages(): Set<String>
}

interface PermissionChecker {
    fun isAccessibilityServiceEnabled(): Boolean
    fun hasUsageAccess(): Boolean
    fun hasNotificationPermission(): Boolean
    fun canScheduleExactAlarms(): Boolean
}
