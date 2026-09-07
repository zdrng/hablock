package dev.hablock.app.domain.service

import dev.hablock.app.domain.model.OverlapPolicy
import dev.hablock.app.domain.model.isChangesLocked
import dev.hablock.app.domain.repository.BlockRepository
import dev.hablock.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BehaviorSettings(
    val overlapPolicy: OverlapPolicy,
    val dayBoundaryMinutes: Int,
    val changesLocked: Boolean,
)

/** Applies global behavior changes behind the same lock that protects Block configuration. */
class BehaviorSettingsService(
    private val settingsRepository: SettingsRepository,
    private val blockRepository: BlockRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onOverlapPolicyChanged: suspend () -> Unit = {},
    private val onDayBoundaryChanged: suspend () -> Unit = {},
) {
    private val mutex = Mutex()

    val settings: Flow<BehaviorSettings> = combine(
        settingsRepository.overlapPolicy,
        settingsRepository.dayBoundaryMinutes,
        blockRepository.blocks,
    ) { policy, boundary, blocks ->
        BehaviorSettings(policy, boundary, blocks.any { it.isChangesLocked(nowMillis()) })
    }

    suspend fun setOverlapPolicy(policy: OverlapPolicy): Boolean = mutex.withLock {
        if (hasLockedChanges()) return false
        settingsRepository.setOverlapPolicy(policy)
        onOverlapPolicyChanged()
        true
    }

    suspend fun setDayBoundaryMinutes(minutes: Int): Boolean = mutex.withLock {
        require(minutes in 0 until MINUTES_PER_DAY)
        if (hasLockedChanges()) return false
        settingsRepository.setDayBoundaryMinutes(minutes)
        onDayBoundaryChanged()
        true
    }

    private suspend fun hasLockedChanges(): Boolean =
        blockRepository.current().any { it.isChangesLocked(nowMillis()) }
}

private const val MINUTES_PER_DAY = 24 * 60
