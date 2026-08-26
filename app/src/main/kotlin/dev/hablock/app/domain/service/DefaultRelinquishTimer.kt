package dev.hablock.app.domain.service

import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.enforcement.SuspensionStore
import dev.hablock.app.domain.model.RelinquishState
import dev.hablock.app.domain.repository.BlockRepository
import dev.hablock.app.domain.repository.SettingsRepository
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

private val TICK = 60.seconds

class DefaultRelinquishTimer(
    private val settingsRepository: SettingsRepository,
    private val deviceOwnerController: DeviceOwnerController,
    private val dayClock: DayClock,
    private val blockRepository: BlockRepository,
    private val alarmScheduler: AlarmScheduler,
    private val suspensionStore: SuspensionStore,
) : RelinquishTimer {

    override val state: Flow<RelinquishState> =
        combine(settingsRepository.relinquishDeadlineMillis, ticks()) { deadline, _ ->
            when {
                deadline == null -> RelinquishState.Idle
                deadline > dayClock.now().toEpochMilli() -> RelinquishState.Counting(Instant.ofEpochMilli(deadline))
                else -> RelinquishState.Ready
            }
        }.distinctUntilChanged()

    override suspend fun start() {
        val deadline = dayClock.now().plusMillis(GateConstants.RELINQUISH_COOLDOWN.inWholeMilliseconds)
        settingsRepository.setRelinquishDeadline(deadline.toEpochMilli())
        alarmScheduler.scheduleRelinquishReady(deadline)
    }

    override suspend fun cancel() {
        settingsRepository.setRelinquishDeadline(null)
        alarmScheduler.cancelRelinquishReady()
    }

    override suspend fun confirmRelinquish(): Boolean {
        val deadline = settingsRepository.relinquishDeadlineMillis.first() ?: return false
        if (dayClock.now().toEpochMilli() < deadline) return false
        val suspended = blockRepository.current().flatMapTo(mutableSetOf()) { it.blockedPackages } +
            suspensionStore.suspended()
        deviceOwnerController.setPackagesSuspended(suspended, false)
        if (!deviceOwnerController.relinquishOwnership()) return false
        suspensionStore.setSuspended(emptySet())
        settingsRepository.setRelinquishDeadline(null)
        alarmScheduler.cancelRelinquishReady()
        return true
    }

    private fun ticks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK)
        }
    }
}
