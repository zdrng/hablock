package dev.hablock.app.domain.service

import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.enforcement.resolveEnforcementPlan
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.BlockDayState
import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.DailyBlockHistory
import dev.hablock.app.domain.model.DailyConditionHistory
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.model.HistoryConditionKind
import dev.hablock.app.domain.model.MetricSnapshot
import dev.hablock.app.domain.model.OverlapPolicy
import dev.hablock.app.domain.repository.BlockRepository
import dev.hablock.app.domain.repository.DiagnosticsRecorder
import dev.hablock.app.domain.repository.GateStateRepository
import dev.hablock.app.domain.repository.HistoryRepository
import dev.hablock.app.domain.repository.NoOpDiagnosticsRecorder
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
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
    scope: CoroutineScope,
    overlapPolicies: Flow<OverlapPolicy> = flowOf(OverlapPolicy.ALL_BLOCKS),
    initialOverlapPolicy: OverlapPolicy = OverlapPolicy.ALL_BLOCKS,
    private val diagnostics: DiagnosticsRecorder = NoOpDiagnosticsRecorder,
    private val scheduleEvaluator: BlockScheduleEvaluator = BlockScheduleEvaluator(),
    private val historyRepository: HistoryRepository? = null,
) : GateEngine {

    private val evaluator = GateEvaluator()
    private val mutex = Mutex()
    private var foregroundPackage: String? = null
    private val snapshotCache = mutableMapOf<String, SnapshotCacheEntry>()
    private val _states = MutableStateFlow<Map<String, GateState>>(emptyMap())

    @Volatile
    private var knownBlocks: List<Block>? = null

    @Volatile
    private var overlapPolicy = initialOverlapPolicy

    override val states: StateFlow<Map<String, GateState>> = _states.asStateFlow()

    init {
        scope.launch {
            blockRepository.blocks.collect { knownBlocks = it }
        }
        scope.launch {
            overlapPolicies.distinctUntilChanged().collect { policy ->
                val changed = overlapPolicy != policy
                overlapPolicy = policy
                if (changed) mutex.withLock { refresh(loadBlocks(), refreshMetrics = false) }
            }
        }
    }

    override suspend fun refreshAll() {
        mutex.withLock { refresh(loadBlocks(), refreshMetrics = true) }
    }

    override suspend fun onAppForegrounded(packageName: String) {
        mutex.withLock {
            foregroundPackage = packageName
            val known = knownBlocks
            if (known != null && known.none { it.enabled && packageName in it.blockedPackages }) return@withLock
            val blocks = loadBlocks()
            if (blocks.none { it.enabled && packageName in it.blockedPackages }) return@withLock
            refresh(blocks, refreshMetrics = false)
        }
    }

    override suspend fun onSessionExpired(blockId: String) {
        mutex.withLock {
            val blocks = loadBlocks()
            val stored = gateStateRepository.get(blockId)
            val session = stored?.activeSession
            if (session != null && dayClock.now().toEpochMilli() >= session.endsAtMillis) {
                gateStateRepository.save(stored.copy(activeSession = null))
                val minutes = ((session.endsAtMillis - session.startedAtMillis) / 60_000L).toInt()
                blocks.firstOrNull { it.id == blockId }?.let {
                    notifier.cancelSessionNotification(it.id)
                    notifier.sessionEnded(it.id, it.name, minutes)
                }
            }
            refresh(blocks, refreshMetrics = true)
        }
    }

    override suspend fun onScheduleTransition() {
        mutex.withLock { refresh(loadBlocks(), refreshMetrics = false) }
    }

    override suspend fun deleteBlock(blockId: String) {
        mutex.withLock {
            blockRepository.delete(blockId)
            gateStateRepository.delete(blockId)
            alarmScheduler.cancelSessionEnd(blockId)
            notifier.cancelSessionNotification(blockId)
            refresh(loadBlocks(), refreshMetrics = false)
        }
    }

    override suspend fun unlock(blockId: String) {
        mutex.withLock {
            val blocks = loadBlocks()
            val block = blocks.firstOrNull { it.id == blockId && it.enabled } ?: return@withLock
            val now = dayClock.now()
            if (!scheduleEvaluator.isActive(block.schedule, now)) {
                refresh(blocks, refreshMetrics = false)
                return@withLock
            }
            val dayKey = dayClock.dayKey(now)
            val (from, to) = dayClock.dayWindow(now)
            val dayState = expireSession(resolveDayState(block, dayKey, now), now)
            val snapshot = snapshotFor(block.conditions, from, to, now, dayKey, refreshMetrics = true)
            val evaluated = Evaluated(block, dayState, snapshot, evaluator.evaluate(block, dayState, snapshot, now))
            if (evaluated.gateState is GateState.Open) {
                startSession(evaluated, now)
            }
            refresh(blocks, refreshMetrics = false)
        }
    }

    override suspend fun relock(blockId: String) {
        mutex.withLock {
            val blocks = loadBlocks()
            val stored = gateStateRepository.get(blockId)
            val session = stored?.activeSession
            if (session != null) {
                gateStateRepository.save(stored.copy(activeSession = null))
                alarmScheduler.cancelSessionEnd(blockId)
                notifier.cancelSessionNotification(blockId)
            }
            refresh(blocks, refreshMetrics = true)
        }
    }

    override suspend fun onDayReset() {
        mutex.withLock {
            val blocks = loadBlocks()
            val now = dayClock.now()
            val dayKey = dayClock.dayKey(now)
            blocks.forEach { block ->
                val stored = gateStateRepository.get(block.id) ?: return@forEach
                if (stored.dayKey != dayKey) rollover(block, stored, dayKey, now)
            }
            snapshotCache.clear()
            alarmScheduler.scheduleDayReset(dayClock.nextReset())
            refresh(blocks, refreshMetrics = true)
        }
    }

    override suspend fun onDayBoundaryChanged() {
        mutex.withLock {
            val blocks = loadBlocks()
            val now = dayClock.now()
            val dayKey = dayClock.dayKey(now)
            blocks.forEach { block ->
                val stored = gateStateRepository.get(block.id) ?: return@forEach
                val activeSession = stored.activeSession?.takeIf { now.toEpochMilli() < it.endsAtMillis }
                if (stored.activeSession != null && activeSession == null) {
                    alarmScheduler.cancelSessionEnd(block.id)
                    notifier.cancelSessionNotification(block.id)
                }
                gateStateRepository.save(
                    freshDayState(block, dayKey).copy(activeSession = activeSession),
                )
            }
            snapshotCache.clear()
            alarmScheduler.scheduleDayReset(dayClock.nextReset(now))
            refresh(blocks, refreshMetrics = true)
        }
    }

    override suspend fun markEmergencyUnlockUsed(blockId: String) {
        mutex.withLock {
            val block = loadBlocks().firstOrNull { it.id == blockId } ?: return@withLock
            val now = dayClock.now()
            val dayKey = dayClock.dayKey(now)
            val stored = gateStateRepository.get(blockId)
            val current = when {
                stored == null -> freshDayState(block, dayKey)
                stored.dayKey == dayKey -> stored
                else -> rollover(block, stored, dayKey, now)
            }
            if (!current.emergencyUnlockUsed) {
                gateStateRepository.save(current.copy(emergencyUnlockUsed = true))
            }
        }
    }

    /** Finalizes the stored day once, then moves any live session onto fresh base requirements. */
    private suspend fun rollover(
        block: Block,
        stored: BlockDayState,
        dayKey: String,
        now: Instant,
    ): BlockDayState {
        finalizeHistory(block, stored, dayKey, now)
        val session = stored.activeSession
        val carriedSession = if (session != null && now.toEpochMilli() < session.endsAtMillis) {
            session
        } else {
            if (session != null) {
                alarmScheduler.cancelSessionEnd(block.id)
                notifier.cancelSessionNotification(block.id)
            }
            null
        }
        return freshDayState(block, dayKey).copy(activeSession = carriedSession).also {
            gateStateRepository.save(it)
        }
    }

    private suspend fun finalizeHistory(
        block: Block,
        stored: BlockDayState,
        currentDayKey: String,
        now: Instant,
    ) {
        val history = historyRepository ?: return
        val (from, to) = dayClock.dayWindow(stored.dayKey)
        val snapshot = metricProvider.snapshot(block.conditions, from, to)
        val conditions = block.conditions.map { condition ->
            val required = stored.requiredNow[condition.id] ?: condition.goal
            val progress = snapshot.valueOf(condition.id)
            DailyConditionHistory(
                conditionId = condition.id,
                kind = condition.historyKind(),
                label = condition.historyLabel(),
                goal = condition.goal,
                required = required,
                progress = progress,
                met = progress >= required,
            )
        }
        history.importNew(
            listOf(
                DailyBlockHistory(
                    blockId = block.id,
                    dayKey = stored.dayKey,
                    blockName = block.name,
                    conditions = conditions,
                    thresholdN = block.thresholdN,
                    unlockCount = stored.unlockCount,
                    emergencyUnlockUsed = stored.emergencyUnlockUsed,
                    scheduledActive = scheduleEvaluator.wasActiveDuring(block.schedule, from, to),
                    finalizedAtMillis = now.toEpochMilli(),
                ),
            ),
        )
        history.pruneBefore(LocalDate.parse(currentDayKey).minusDays(365).toString())
    }

    private fun Condition.historyKind(): HistoryConditionKind = when (this) {
        is Condition.AppUsage -> HistoryConditionKind.APP_USAGE
        is Condition.Steps -> HistoryConditionKind.STEPS
        is Condition.Exercise -> HistoryConditionKind.EXERCISE
        is Condition.Meditation -> HistoryConditionKind.MEDITATION
    }

    private fun Condition.historyLabel(): String = when (this) {
        is Condition.AppUsage -> appLabel
        is Condition.Steps -> "Steps"
        is Condition.Exercise -> "Exercise"
        is Condition.Meditation -> "Meditation"
    }

    private suspend fun refresh(blocks: List<Block>, refreshMetrics: Boolean) {
        val now = dayClock.now()
        val dayKey = dayClock.dayKey(now)
        val (from, to) = dayClock.dayWindow(now)
        scheduleNextTransition(blocks, now)
        blocks.forEach { block ->
            val stored = gateStateRepository.get(block.id) ?: return@forEach
            if (stored.dayKey != dayKey) rollover(block, stored, dayKey, now)
        }
        val previous = _states.value
        val autoStart = !enforcement.reportsForegroundUse()
        val next = mutableMapOf<String, GateState>()
        try {
            for (block in blocks.filter { it.enabled }) {
                val dayState = expireSession(resolveDayState(block, dayKey, now), now)
                val snapshot = snapshotFor(block.conditions, from, to, now, dayKey, refreshMetrics)
                var state = evaluator.evaluate(block, dayState, snapshot, now)
                val scheduleActive = scheduleEvaluator.isActive(block.schedule, now)
                if (state !is GateState.SessionActive && !scheduleActive) {
                    state = GateState.Inactive(state.progress)
                }
                if (scheduleActive && autoStart && state is GateState.Open && justUnlocked(previous[block.id], dayState)) {
                    startSession(Evaluated(block, dayState, snapshot, state), now)?.let {
                        state = evaluator.evaluate(block, it, snapshot, now)
                    }
                }
                next[block.id] = state
            }
        } catch (cause: Throwable) {
            diagnostics.recordEvaluation(false, next.size, next.values.count(GateState::isBlocking))
            throw cause
        }
        diagnostics.recordEvaluation(true, next.size, next.values.count(GateState::isBlocking))
        _states.value = next
        val enforcementPlan = resolveEnforcementPlan(blocks, next, overlapPolicy)
        enforcement.applyState(enforcementPlan)
        foregroundPackage?.let { pkg ->
            enforcementPlan.blockingBlockId(pkg)?.let { enforcement.showBlocked(pkg, it) }
        }
        restoreSessionNotifications(blocks, next)
    }

    /** On a fresh process every previous state is null; only a block with no unlock today gets its auto-session. */
    private fun justUnlocked(previous: GateState?, dayState: BlockDayState): Boolean =
        if (previous != null) {
            previous is GateState.Locked || previous is GateState.Inactive
        } else {
            dayState.unlockCount == 0
        }

    private fun scheduleNextTransition(blocks: List<Block>, now: Instant) {
        val next = blocks.asSequence()
            .filter { it.enabled }
            .mapNotNull { scheduleEvaluator.nextTransition(it.schedule, now) }
            .minOrNull()
        if (next == null) alarmScheduler.cancelScheduleTransition()
        else alarmScheduler.scheduleScheduleTransition(next)
    }

    private fun restoreSessionNotifications(blocks: List<Block>, states: Map<String, GateState>) {
        val blockById = blocks.associateBy { it.id }
        for ((blockId, state) in states) {
            if (state is GateState.SessionActive) {
                blockById[blockId]?.let { notifier.restoreSessionNotification(blockId, it.name, state.endsAt) }
            }
        }
    }

    private suspend fun startSession(evaluated: Evaluated, now: Instant): BlockDayState? {
        if (evaluated.dayState.activeSession != null) return null
        val started = SessionMath.startSession(
            block = evaluated.block,
            dayState = evaluated.dayState,
            snapshot = evaluated.snapshot,
            now = now,
            sessionDuration = evaluated.block.unlockDurationMinutes.minutes,
        )
        gateStateRepository.save(started)
        started.activeSession?.let {
            val endsAt = Instant.ofEpochMilli(it.endsAtMillis)
            alarmScheduler.scheduleSessionEnd(evaluated.block.id, endsAt)
            notifier.sessionStarted(evaluated.block.id, evaluated.block.name, endsAt)
        }
        return started
    }

    private suspend fun resolveDayState(block: Block, dayKey: String, now: Instant): BlockDayState {
        val stored = gateStateRepository.get(block.id)
        if (stored == null) return freshDayState(block, dayKey).also { gateStateRepository.save(it) }
        if (stored.dayKey == dayKey) return stored
        return rollover(block, stored, dayKey, now)
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
        alarmScheduler.cancelSessionEnd(dayState.blockId)
        notifier.cancelSessionNotification(dayState.blockId)
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

private fun GateState.isBlocking(): Boolean = this is GateState.Locked || this is GateState.Open
