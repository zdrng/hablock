package dev.hablock.app.domain.service

import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.enforcement.SuspensionStore
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.BlockDayState
import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.model.MetricSnapshot
import dev.hablock.app.domain.repository.BlockRepository
import dev.hablock.app.domain.repository.GateStateRepository
import dev.hablock.app.domain.repository.HealthRepository
import dev.hablock.app.domain.repository.SettingsRepository
import dev.hablock.app.domain.repository.UsageStatsRepository
import dev.hablock.app.domain.model.AppUsageEntry
import dev.hablock.app.domain.model.HcAvailability
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

fun testBlock(
    id: String = "b1",
    packages: Set<String> = setOf("com.example.app"),
    conditions: List<Condition> = emptyList(),
    thresholdN: Int = 1,
    incrementPct: Float = 0.10f,
    enabled: Boolean = true,
): Block = Block(
    id = id,
    name = "Block $id",
    blockedPackages = packages,
    conditions = conditions,
    thresholdN = thresholdN,
    incrementPct = incrementPct,
    enabled = enabled,
)

fun snapshotOf(vararg values: Pair<String, Double>): MetricSnapshot = MetricSnapshot(values.toMap())

class MutableClock(var current: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
    override fun instant(): Instant = current
    fun advance(millis: Long) {
        current = current.plusMillis(millis)
    }
}

class FakeBlockRepository(initial: List<Block> = emptyList()) : BlockRepository {
    private val state = MutableStateFlow(initial)
    override val blocks: Flow<List<Block>> = state
    override suspend fun current(): List<Block> = state.value
    override suspend fun upsert(block: Block) {
        state.value = state.value.filterNot { it.id == block.id } + block
    }

    override suspend fun delete(blockId: String) {
        state.value = state.value.filterNot { it.id == blockId }
    }
}

class FakeGateStateRepository(initial: Map<String, BlockDayState> = emptyMap()) : GateStateRepository {
    private val states = MutableStateFlow(initial)
    var clearAllCount = 0
        private set

    override val dayStates: Flow<Map<String, BlockDayState>> = states
    override suspend fun get(blockId: String): BlockDayState? = states.value[blockId]
    override suspend fun save(state: BlockDayState) {
        states.value = states.value + (state.blockId to state)
    }

    override suspend fun delete(blockId: String) {
        states.value = states.value - blockId
    }

    override suspend fun clearAll() {
        clearAllCount++
        states.value = emptyMap()
    }

    fun stored(blockId: String): BlockDayState? = states.value[blockId]
    fun all(): Map<String, BlockDayState> = states.value
}

class FakeMetricProvider(var values: Map<String, Double> = emptyMap()) : MetricProvider {
    var calls = 0
        private set

    override suspend fun snapshot(conditions: List<Condition>, from: Instant, to: Instant): MetricSnapshot {
        calls++
        return MetricSnapshot(conditions.associate { it.id to (values[it.id] ?: 0.0) })
    }
}

class FakeAlarmScheduler : AlarmScheduler {
    val sessionEnds = mutableListOf<Pair<String, Instant>>()
    val cancelledSessions = mutableListOf<String>()
    val dayResets = mutableListOf<Instant>()
    val relinquishReady = mutableListOf<Instant>()
    var cancelRelinquishReadyCount = 0
        private set

    override fun scheduleSessionEnd(blockId: String, at: Instant) {
        sessionEnds += blockId to at
    }

    override fun cancelSessionEnd(blockId: String) {
        cancelledSessions += blockId
    }

    override fun scheduleDayReset(at: Instant) {
        dayResets += at
    }

    override fun scheduleRelinquishReady(at: Instant) {
        relinquishReady += at
    }

    override fun cancelRelinquishReady() {
        cancelRelinquishReadyCount++
    }
}

class FakeNotifier : Notifier {
    val sessionsEnded = mutableListOf<Pair<String, String>>()
    var relinquishReadyCount = 0
        private set

    override fun sessionEnded(blockId: String, blockName: String) {
        sessionsEnded += blockId to blockName
    }

    override fun relinquishReady() {
        relinquishReadyCount++
    }
}

class FakeEnforcement(var foregroundUseReported: Boolean = true) : EnforcementBackend {
    val applied = mutableListOf<Map<String, GateState>>()
    val blocked = mutableListOf<Pair<String, String>>()

    override suspend fun applyState(blocks: List<Block>, states: Map<String, GateState>) {
        applied += states
    }

    override suspend fun showBlocked(packageName: String, blockId: String) {
        blocked += packageName to blockId
    }

    override fun reportsForegroundUse(): Boolean = foregroundUseReported
}

class FakeSettingsRepository(deadline: Long? = null) : SettingsRepository {
    private val onboarding = MutableStateFlow(false)
    private val deadlineState = MutableStateFlow(deadline)

    override val onboardingDone: Flow<Boolean> = onboarding
    override suspend fun setOnboardingDone() {
        onboarding.value = true
    }

    override val relinquishDeadlineMillis: Flow<Long?> = deadlineState
    override suspend fun setRelinquishDeadline(millis: Long?) {
        deadlineState.value = millis
    }

    fun deadline(): Long? = deadlineState.value
}

class FakeDeviceOwnerController(private var owner: Boolean = true) : DeviceOwnerController {
    var relinquishCount = 0
        private set
    var applyRestrictionsCount = 0
        private set
    var relinquishSucceeds = true
    var failSuspension: Set<String> = emptySet()
    val suspensions = mutableListOf<Pair<Set<String>, Boolean>>()
    val calls = mutableListOf<String>()

    override fun isDeviceOwner(): Boolean = owner
    override fun setPackagesSuspended(packages: Set<String>, suspended: Boolean): Set<String> {
        suspensions += packages to suspended
        calls += "setPackagesSuspended"
        return failSuspension intersect packages
    }

    override fun applyRestrictions() {
        applyRestrictionsCount++
        calls += "applyRestrictions"
    }

    override fun relinquishOwnership(): Boolean {
        relinquishCount++
        calls += "relinquishOwnership"
        if (relinquishSucceeds) owner = false
        return relinquishSucceeds
    }
}

class FakeSuspensionStore(initial: Set<String> = emptySet()) : SuspensionStore {
    var stored: Set<String> = initial
        private set

    override suspend fun suspended(): Set<String> = stored
    override suspend fun setSuspended(packages: Set<String>) {
        stored = packages
    }
}

class FakeUsageStatsRepository(private val minutes: Map<String, Double> = emptyMap()) : UsageStatsRepository {
    var calls = 0
        private set

    override suspend fun foregroundMinutes(packageName: String, from: Instant, to: Instant): Double {
        calls++
        return minutes[packageName] ?: 0.0
    }

    override suspend fun usageByApp(from: Instant, to: Instant): List<AppUsageEntry> =
        minutes.map { AppUsageEntry(it.key, it.value) }
}

class FakeHealthRepository(
    private val steps: Double = 0.0,
    private val exercise: Double = 0.0,
    private val mindful: Double = 0.0,
    private val failing: Boolean = false,
) : HealthRepository {
    var stepCalls = 0
        private set

    override suspend fun availability(): HcAvailability = HcAvailability.FULL
    override suspend fun hasAllPermissions(): Boolean = true
    override fun requiredPermissions(): Set<String> = emptySet()

    override suspend fun steps(from: Instant, to: Instant): Double {
        stepCalls++
        if (failing) error("health unavailable")
        return steps
    }

    override suspend fun exerciseMinutes(from: Instant, to: Instant): Double {
        if (failing) error("health unavailable")
        return exercise
    }

    override suspend fun mindfulMinutes(from: Instant, to: Instant): Double {
        if (failing) error("health unavailable")
        return mindful
    }
}
