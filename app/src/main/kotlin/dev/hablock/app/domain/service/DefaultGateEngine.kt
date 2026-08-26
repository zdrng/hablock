package dev.hablock.app.domain.service

import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.BlockDayState
import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.model.MetricSnapshot
import dev.hablock.app.domain.repository.BlockRepository
import dev.hablock.app.domain.repository.GateStateRepository
import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val SNAPSHOT_TTL_MILLIS = 15_000L

private class SnapshotCacheEntry(val atMillis: Long, val dayKey: String, val snapshot: MetricSnapshot)

private class Evaluated(
    val block: Block,
    val dayState: BlockDayState,
    val snapshot: MetricSnapshot,
    val gateState: GateState,
)

class DefaultGateEngine(
    private val blockRepository: BlockRepository,
    private val gateStateRepository: GateStateRepository,
    private val metricProvider: MetricProvider,
    private val dayClock: DayClock,
    private val alarmScheduler: AlarmScheduler,
    private val notifier: Notifier,
    private val enforcement: EnforcementBackend,
    private val sessionDuration: Duration,
    scope: CoroutineScope,
) : GateEngine {

    private val evaluator = GateEvaluator()
    private val mutex = Mutex()
    private val snapshotCache = mutableMapOf<String, SnapshotCacheEntry>()
    private val _states = MutableStateFlow<Map<String, GateState>>(emptyMap())

    @Volatile
    private var knownBlocks: List<Block>? = null

    override val states: StateFlow<Map<String, GateState>> = _states.asStateFlow()

    init {
        scope.launch {
            blockRepository.blocks.collect { knownBlocks = it }
        }
    }

    override suspend fun refreshAll() {
        mutex.withLock { refresh(loadBlocks(), refreshMetrics = true) }
    }

    override suspend fun onAppForegrounded(packageName: String) {
        val known = knownBlocks
        if (known != null && known.none { it.enabled && packageName in it.blockedPackages }) return
        mutex.withLock {
            val blocks = loadBlocks()
            val affected = blocks.filter { it.enabled && packageName in it.blockedPackages }
            if (affected.isEmpty()) return@withLock
            val now = dayClock.now()
            val dayKey = dayClock.dayKey(now)
            val (from, to) = dayClock.dayWindow(now)
            val evaluated = affected.map { block ->
                val dayState = expireSession(resolveDayState(block, dayKey), now)
                val snapshot = snapshotFor(block.conditions, from, to, now, dayKey, refreshMetrics = false)
                Evaluated(block, dayState, snapshot, evaluator.evaluate(block, dayState, snapshot, now))
            }
            val locked = evaluated.firstOrNull { it.gateState is GateState.Locked }
            if (locked != null) {
                enforcement.showBlocked(packageName, locked.block.id)
            } else {
                evaluated.filter { it.gateState is GateState.Open }.forEach { startSession(it, now) }
            }
            refresh(blocks, refreshMetrics = false)
        }
    }

    override suspend fun onSessionExpired(blockId: String) {
        mutex.withLock {
            val blocks = loadBlocks()
            val stored = gateStateRepository.get(blockId)
            if (stored?.activeSession != null) {
                gateStateRepository.save(stored.copy(activeSession = null))
                blocks.firstOrNull { it.id == blockId }?.let { notifier.sessionEnded(it.id, it.name) }
            }
            refresh(blocks, refreshMetrics = true)
        }
    }

    override suspend fun deleteBlock(blockId: String) {
        mutex.withLock {
            blockRepository.delete(blockId)
            gateStateRepository.delete(blockId)
            alarmScheduler.cancelSessionEnd(blockId)
            refresh(loadBlocks(), refreshMetrics = false)
        }
    }

    override suspend fun onDayReset() {
        mutex.withLock {
            val blocks = loadBlocks()
            val now = dayClock.now()
            val dayKey = dayClock.dayKey(now)
            val carriedOver = blocks.mapNotNull { block -> carryOver(block, dayKey, now) }
            gateStateRepository.clearAll()
            carriedOver.forEach { gateStateRepository.save(it) }
            snapshotCache.clear()
            alarmScheduler.scheduleDayReset(dayClock.nextReset())
            refresh(blocks, refreshMetrics = true)
        }
    }

    /** An in-flight session survives the reset on a base-goal day state; an expired one takes its alarm with it. */
    private suspend fun carryOver(block: Block, dayKey: String, now: Instant): BlockDayState? {
        val session = gateStateRepository.get(block.id)?.activeSession ?: return null
        if (now.toEpochMilli() >= session.endsAtMillis) {
            alarmScheduler.cancelSessionEnd(block.id)
            return null
        }
        return freshDayState(block, dayKey).copy(activeSession = session)
    }

    private suspend fun refresh(blocks: List<Block>, refreshMetrics: Boolean) {
        val now = dayClock.now()
        val dayKey = dayClock.dayKey(now)
        val (from, to) = dayClock.dayWindow(now)
        val previous = _states.value
        val autoStart = !enforcement.reportsForegroundUse()
        val next = mutableMapOf<String, GateState>()
        for (block in blocks.filter { it.enabled }) {
            val dayState = expireSession(resolveDayState(block, dayKey), now)
            val snapshot = snapshotFor(block.conditions, from, to, now, dayKey, refreshMetrics)
            var state = evaluator.evaluate(block, dayState, snapshot, now)
            if (autoStart && state is GateState.Open && justUnlocked(previous[block.id], dayState)) {
                startSession(Evaluated(block, dayState, snapshot, state), now)?.let {
                    state = evaluator.evaluate(block, it, snapshot, now)
                }
            }
            next[block.id] = state
        }
        _states.value = next
        enforcement.applyState(blocks, next)
    }

    /** On a fresh process every previous state is null; only a block with no unlock today gets its auto-session. */
    private fun justUnlocked(previous: GateState?, dayState: BlockDayState): Boolean =
        if (previous != null) previous is GateState.Locked else dayState.unlockCount == 0

    private suspend fun startSession(evaluated: Evaluated, now: Instant): BlockDayState? {
        if (evaluated.dayState.activeSession != null) return null
        val started = SessionMath.startSession(
            block = evaluated.block,
            dayState = evaluated.dayState,
            snapshot = evaluated.snapshot,
            now = now,
            sessionDuration = sessionDuration,
        )
        gateStateRepository.save(started)
        started.activeSession?.let {
            alarmScheduler.scheduleSessionEnd(evaluated.block.id, Instant.ofEpochMilli(it.endsAtMillis))
        }
        return started
    }

    private suspend fun resolveDayState(block: Block, dayKey: String): BlockDayState {
        val stored = gateStateRepository.get(block.id)
        return if (stored != null && stored.dayKey == dayKey) stored else freshDayState(block, dayKey)
    }

    private fun freshDayState(block: Block, dayKey: String): BlockDayState = BlockDayState(
        blockId = block.id,
        dayKey = dayKey,
        requiredNow = block.conditions.associate { it.id to it.goal },
    )

    private suspend fun expireSession(dayState: BlockDayState, now: Instant): BlockDayState {
        val session = dayState.activeSession ?: return dayState
        if (now.toEpochMilli() < session.endsAtMillis) return dayState
        val cleared = dayState.copy(activeSession = null)
        gateStateRepository.save(cleared)
        return cleared
    }

    private suspend fun snapshotFor(
        conditions: List<Condition>,
        from: Instant,
        to: Instant,
        now: Instant,
        dayKey: String,
        refreshMetrics: Boolean,
    ): MetricSnapshot {
        if (conditions.isEmpty()) return MetricSnapshot(emptyMap())
        val key = conditions.map { it.id }.sorted().joinToString("|")
        if (!refreshMetrics) {
            val cached = snapshotCache[key]
            if (cached != null && cached.dayKey == dayKey &&
                now.toEpochMilli() - cached.atMillis < SNAPSHOT_TTL_MILLIS
            ) {
                return cached.snapshot
            }
        }
        val snapshot = metricProvider.snapshot(conditions, from, to)
        snapshotCache[key] = SnapshotCacheEntry(now.toEpochMilli(), dayKey, snapshot)
        return snapshot
    }

    private suspend fun loadBlocks(): List<Block> = blockRepository.current().also { knownBlocks = it }
}
