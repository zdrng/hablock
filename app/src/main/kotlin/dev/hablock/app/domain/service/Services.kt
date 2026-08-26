package dev.hablock.app.domain.service

import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.model.MetricSnapshot
import dev.hablock.app.domain.model.RelinquishState
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Single orchestrator for gate evaluation, sessions and enforcement; keyed by block id. */
interface GateEngine {
    val states: StateFlow<Map<String, GateState>>
    suspend fun refreshAll()
    suspend fun onAppForegrounded(packageName: String)
    suspend fun onSessionExpired(blockId: String)
    suspend fun onDayReset()
    suspend fun deleteBlock(blockId: String)
}

interface MetricProvider {
    suspend fun snapshot(conditions: List<Condition>, from: Instant, to: Instant): MetricSnapshot
}

interface AlarmScheduler {
    fun scheduleSessionEnd(blockId: String, at: Instant)
    fun cancelSessionEnd(blockId: String)
    fun scheduleDayReset(at: Instant)
    fun scheduleRelinquishReady(at: Instant)
    fun cancelRelinquishReady()
}

interface Notifier {
    fun sessionEnded(blockId: String, blockName: String)
    fun relinquishReady()
}

interface RelinquishTimer {
    val state: Flow<RelinquishState>
    suspend fun start()
    suspend fun cancel()

    /** True when device ownership was actually dropped; false leaves the countdown state untouched. */
    suspend fun confirmRelinquish(): Boolean
}
