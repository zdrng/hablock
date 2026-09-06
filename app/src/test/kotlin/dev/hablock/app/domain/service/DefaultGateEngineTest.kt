package dev.hablock.app.domain.service

import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.enforcement.EnforcementCoordinator
import dev.hablock.app.domain.model.BlockDayState
import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.GateState
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

private const val SOCIAL = "com.example.social"
private const val EPS = 1e-3

class DefaultGateEngineTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val clock = MutableClock(Instant.parse("2026-08-23T09:00:00Z"), zone)
    private val dayClock = DayClock(clock, zone)

    private val conditions = listOf(
        Condition.Steps("steps", 10_000.0),
        Condition.Exercise("workout", 60.0),
    )
    private val block = testBlock(id = "b1", packages = setOf(SOCIAL), conditions = conditions, thresholdN = 1)

    private val blockRepository = FakeBlockRepository(listOf(block))
    private val gateStateRepository = FakeGateStateRepository()
    private val metricProvider = FakeMetricProvider()
    private val alarmScheduler = FakeAlarmScheduler()
    private val notifier = FakeNotifier()
    private val enforcement = FakeEnforcement()

    private fun newEngine(scope: CoroutineScope, backend: EnforcementBackend = enforcement) = DefaultGateEngine(
        blockRepository = blockRepository,
        gateStateRepository = gateStateRepository,
        metricProvider = metricProvider,
        dayClock = dayClock,
        alarmScheduler = alarmScheduler,
        notifier = notifier,
        enforcement = backend,
        scope = scope,
    )

    @Test
    fun `foregrounding a blocked app while locked shows the block screen`() = runTest {
        val engine = newEngine(backgroundScope)
        engine.onAppForegrounded(SOCIAL)

        assertEquals(listOf(SOCIAL to "b1"), enforcement.blocked)
        assertTrue(alarmScheduler.sessionEnds.isEmpty())
        assertNull(gateStateRepository.stored("b1")?.activeSession)
        assertIs<GateState.Locked>(engine.states.value.getValue("b1"))
    }

    @Test
    fun `foregrounding a blocked app while open shows the blocked screen instead of auto-starting`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.onAppForegrounded(SOCIAL)

        assertEquals(listOf(SOCIAL to "b1"), enforcement.blocked)
        assertNull(gateStateRepository.stored("b1")?.activeSession)
        assertTrue(alarmScheduler.sessionEnds.isEmpty())
        assertIs<GateState.Open>(engine.states.value.getValue("b1"))
    }

    @Test
    fun `unlock starts a session when the gate is open`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.refreshAll()

        engine.unlock("b1")

        val stored = assertNotNull(gateStateRepository.stored("b1"))
        val session = assertNotNull(stored.activeSession)
        assertEquals(1, stored.unlockCount)
        assertEquals(11_300.0, stored.requiredNow.getValue("steps"), EPS)
        assertEquals(60.0, stored.requiredNow.getValue("workout"), EPS)
        assertEquals(listOf("b1" to Instant.ofEpochMilli(session.endsAtMillis)), alarmScheduler.sessionEnds)
        assertEquals(listOf(Triple("b1", "Block b1", Instant.ofEpochMilli(session.endsAtMillis))), notifier.sessionsStarted)
        assertIs<GateState.SessionActive>(engine.states.value.getValue("b1"))
    }

    @Test
    fun `unlock does nothing when the gate is locked`() = runTest {
        val engine = newEngine(backgroundScope)
        engine.unlock("b1")

        assertNull(gateStateRepository.stored("b1")?.activeSession)
        assertTrue(alarmScheduler.sessionEnds.isEmpty())
        assertIs<GateState.Locked>(engine.states.value.getValue("b1"))
    }

    @Test
    fun `relock clears the session but keeps the ratcheted requirements`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.refreshAll()
        engine.unlock("b1")
        assertNotNull(gateStateRepository.stored("b1")?.activeSession)

        engine.relock("b1")

        assertNull(gateStateRepository.stored("b1")?.activeSession)
        assertEquals(1, gateStateRepository.stored("b1")?.unlockCount)
        assertEquals(11_300.0, gateStateRepository.stored("b1")?.requiredNow?.getValue("steps") ?: 0.0, EPS)
        assertEquals(listOf("b1"), alarmScheduler.cancelledSessions)
        assertEquals(listOf("b1"), notifier.cancelledNotifications)
        assertIs<GateState.Locked>(engine.states.value.getValue("b1"))
    }

    @Test
    fun `relock does nothing without an active session`() = runTest {
        val engine = newEngine(backgroundScope)
        engine.relock("b1")

        assertNull(gateStateRepository.stored("b1")?.activeSession)
        assertTrue(alarmScheduler.cancelledSessions.isEmpty())
    }

    @Test
    fun `a second foreground event during a session does not ratchet again`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.refreshAll()
        engine.unlock("b1")
        clock.advance(1_000)
        engine.onAppForegrounded(SOCIAL)

        val stored = assertNotNull(gateStateRepository.stored("b1"))
        assertEquals(1, stored.unlockCount)
        assertEquals(11_300.0, stored.requiredNow.getValue("steps"), EPS)
        assertEquals(1, alarmScheduler.sessionEnds.size)
    }

    @Test
    fun `metric snapshots are cached across a burst of foreground events`() = runTest {
        val engine = newEngine(backgroundScope)
        engine.onAppForegrounded(SOCIAL)
        clock.advance(2_000)
        engine.onAppForegrounded(SOCIAL)
        assertEquals(1, metricProvider.calls)

        clock.advance(20_000)
        engine.onAppForegrounded(SOCIAL)
        assertEquals(2, metricProvider.calls)

        engine.refreshAll()
        assertEquals(3, metricProvider.calls)
    }

    @Test
    fun `foregrounding an unrelated app does nothing`() = runTest {
        val engine = newEngine(backgroundScope)
        engine.onAppForegrounded("com.example.notes")

        assertTrue(enforcement.blocked.isEmpty())
        assertTrue(enforcement.applied.isEmpty())
        assertEquals(0, metricProvider.calls)
    }

    @Test
    fun `session expiry clears the session and notifies`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.refreshAll()
        engine.unlock("b1")
        clock.advance(31 * 60 * 1_000L)

        engine.onSessionExpired("b1")

        assertEquals(listOf("cancel:b1", "ended:b1"), notifier.notificationEvents)
        assertEquals(listOf(Triple("b1", "Block b1", 30)), notifier.sessionsEnded)
        assertEquals(listOf("b1"), notifier.cancelledNotifications)
        assertNull(gateStateRepository.stored("b1")?.activeSession)
        assertIs<GateState.Locked>(engine.states.value.getValue("b1"))
    }

    @Test
    fun `an expired session is cleared by a plain refresh too`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.refreshAll()
        engine.unlock("b1")
        clock.advance(31 * 60 * 1_000L)

        engine.refreshAll()

        assertNull(gateStateRepository.stored("b1")?.activeSession)
        assertTrue(notifier.sessionsEnded.isEmpty())
        assertEquals(listOf("b1"), notifier.cancelledNotifications)
        assertEquals(listOf("b1"), alarmScheduler.cancelledSessions)
    }

    @Test
    fun `day reset clears expired state, cancels its alarm and schedules the next reset`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.refreshAll()
        engine.unlock("b1")
        clock.advance(31 * 60 * 1_000L)

        engine.onDayReset()

        assertEquals(1, gateStateRepository.clearAllCount)
        assertTrue(gateStateRepository.all().isEmpty())
        assertEquals(listOf("b1"), alarmScheduler.cancelledSessions)
        assertEquals(listOf(dayClock.nextReset()), alarmScheduler.dayResets)
        val state = engine.states.value.getValue("b1")
        assertIs<GateState.Open>(state)
        assertEquals(10_000.0, state.progress.first().required, EPS)
    }

    @Test
    fun `day reset carries an in-flight session into the new day`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.refreshAll()
        engine.unlock("b1")
        val session = assertNotNull(gateStateRepository.stored("b1")?.activeSession)

        engine.onDayReset()

        val stored = assertNotNull(gateStateRepository.stored("b1"))
        assertEquals(session, stored.activeSession)
        assertEquals(dayClock.dayKey(), stored.dayKey)
        assertEquals(0, stored.unlockCount)
        assertEquals(10_000.0, stored.requiredNow.getValue("steps"), EPS)
        assertTrue(alarmScheduler.cancelledSessions.isEmpty())
        assertIs<GateState.SessionActive>(engine.states.value.getValue("b1"))
    }

    @Test
    fun `expiry without an active session does not notify`() = runTest {
        val engine = newEngine(backgroundScope)
        engine.onSessionExpired("b1")

        assertTrue(notifier.sessionsEnded.isEmpty())
        assertIs<GateState.Locked>(engine.states.value.getValue("b1"))
    }

    @Test
    fun `an unobservable backend starts a session as soon as the gate opens`() = runTest {
        enforcement.foregroundUseReported = false
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.refreshAll()

        assertIs<GateState.SessionActive>(engine.states.value.getValue("b1"))
        val stored = assertNotNull(gateStateRepository.stored("b1"))
        assertEquals(1, stored.unlockCount)
        assertEquals(11_300.0, stored.requiredNow.getValue("steps"), EPS)
        assertEquals(1, alarmScheduler.sessionEnds.size)
        assertEquals(listOf(engine.states.value), enforcement.applied)
    }

    @Test
    fun `an observable backend leaves an open gate open`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.refreshAll()

        assertIs<GateState.Open>(engine.states.value.getValue("b1"))
        assertNull(gateStateRepository.stored("b1")?.activeSession)
        assertTrue(alarmScheduler.sessionEnds.isEmpty())
    }

    @Test
    fun `an unobservable backend does not re-burn a session on a still-open gate`() = runTest {
        enforcement.foregroundUseReported = false
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.refreshAll()
        assertIs<GateState.SessionActive>(engine.states.value.getValue("b1"))

        metricProvider.values = mapOf("steps" to 21_500.0)
        clock.advance(31 * 60 * 1_000L)
        engine.onSessionExpired("b1")
        engine.refreshAll()

        assertIs<GateState.Open>(engine.states.value.getValue("b1"))
        assertEquals(1, gateStateRepository.stored("b1")?.unlockCount)
        assertEquals(1, alarmScheduler.sessionEnds.size)
    }

    @Test
    fun `a process restart does not re-burn a session on an unobservable backend`() = runTest {
        enforcement.foregroundUseReported = false
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.refreshAll()
        clock.advance(31 * 60 * 1_000L)
        engine.onSessionExpired("b1")

        metricProvider.values = mapOf("steps" to 21_500.0)
        val restarted = newEngine(backgroundScope)
        restarted.refreshAll()

        assertIs<GateState.Open>(restarted.states.value.getValue("b1"))
        assertEquals(1, gateStateRepository.stored("b1")?.unlockCount)
        assertEquals(1, alarmScheduler.sessionEnds.size)
    }

    @Test
    fun `deleting a block removes its state, cancels its alarm and unpublishes it`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.refreshAll()
        engine.unlock("b1")
        assertNotNull(gateStateRepository.stored("b1")?.activeSession)

        engine.deleteBlock("b1")

        assertTrue(blockRepository.current().isEmpty())
        assertNull(gateStateRepository.stored("b1"))
        assertEquals(listOf("b1"), alarmScheduler.cancelledSessions)
        assertEquals(listOf("b1"), notifier.cancelledNotifications)
        assertTrue(engine.states.value.isEmpty())

        engine.refreshAll()
        assertNull(gateStateRepository.stored("b1"))
    }

    @Test
    fun `a cached snapshot does not survive the day boundary`() = runTest {
        clock.current = Instant.parse("2026-08-22T21:59:55Z")
        val engine = newEngine(backgroundScope)
        engine.onAppForegrounded(SOCIAL)
        assertEquals(1, metricProvider.calls)

        clock.advance(10_000)
        engine.onAppForegrounded(SOCIAL)

        assertEquals(2, metricProvider.calls)
    }

    @Test
    fun `a stale day state is treated as fresh`() = runTest {
        gateStateRepository.save(
            BlockDayState(
                blockId = "b1",
                dayKey = "2026-08-20",
                requiredNow = mapOf("steps" to 25_000.0, "workout" to 200.0),
                unlockCount = 7,
            ),
        )
        metricProvider.values = mapOf("steps" to 10_000.0)
        val engine = newEngine(backgroundScope)
        engine.refreshAll()

        val state = engine.states.value.getValue("b1")
        assertIs<GateState.Open>(state)
        assertEquals(10_000.0, state.progress.first().required, EPS)
    }

    @Test
    fun `refresh publishes enabled blocks only and applies enforcement`() = runTest {
        blockRepository.upsert(testBlock(id = "b2", packages = setOf("com.example.video"), conditions = conditions, enabled = false))
        val engine = newEngine(backgroundScope)
        engine.refreshAll()

        assertEquals(setOf("b1"), engine.states.value.keys)
        assertEquals(listOf(engine.states.value), enforcement.applied)
    }
    @Test
    fun `expiry interrupts the foreground app even when the next goals are met`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.unlock("b1")
        engine.onAppForegrounded(SOCIAL)
        assertTrue(enforcement.blocked.isEmpty())
        metricProvider.values = mapOf("steps" to 30_000.0)
        clock.advance(31 * 60_000L)
        engine.onSessionExpired("b1")
        assertEquals(listOf(SOCIAL to "b1"), enforcement.blocked)
        assertIs<GateState.Open>(engine.states.value.getValue("b1"))
    }

    @Test
    fun `expiry does not interrupt an unrelated foreground screen`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.unlock("b1")
        engine.onAppForegrounded(SOCIAL)
        engine.onAppForegrounded("com.example.home")
        clock.advance(31 * 60_000L)
        engine.onSessionExpired("b1")
        assertTrue(enforcement.blocked.isEmpty())
    }

    @Test
    fun `refresh interrupts a foreground session after a missed expiry alarm`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.unlock("b1")
        engine.onAppForegrounded(SOCIAL)
        clock.advance(31 * 60_000L)
        engine.refreshAll()
        assertEquals(listOf(SOCIAL to "b1"), enforcement.blocked)
    }

    @Test
    fun `an early expiry callback preserves the current session`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.unlock("b1")
        val session = gateStateRepository.stored("b1")?.activeSession
        engine.onSessionExpired("b1")
        assertEquals(session, gateStateRepository.stored("b1")?.activeSession)
        assertTrue(notifier.notificationEvents.isEmpty())
        assertIs<GateState.SessionActive>(engine.states.value.getValue("b1"))
    }

    @Test
    fun `restart after missed midnight reset preserves and persists a live session`() = runTest {
        clock.current = Instant.parse("2026-08-22T21:50:00Z")
        metricProvider.values = mapOf("steps" to 10_300.0)
        newEngine(backgroundScope).unlock("b1")
        val session = assertNotNull(gateStateRepository.stored("b1")?.activeSession)
        clock.advance(15 * 60_000L)
        metricProvider.values = emptyMap()
        val restarted = newEngine(backgroundScope)
        restarted.refreshAll()
        val stored = assertNotNull(gateStateRepository.stored("b1"))
        assertEquals(session, stored.activeSession)
        assertEquals(dayClock.dayKey(), stored.dayKey)
        assertEquals(0, stored.unlockCount)
        assertEquals(10_000.0, stored.requiredNow.getValue("steps"), EPS)
        assertIs<GateState.SessionActive>(restarted.states.value.getValue("b1"))
        restarted.refreshAll()
        assertEquals(session, gateStateRepository.stored("b1")?.activeSession)
        clock.advance(16 * 60_000L)
        restarted.onSessionExpired("b1")
        assertNull(gateStateRepository.stored("b1")?.activeSession)
        assertIs<GateState.Locked>(restarted.states.value.getValue("b1"))
    }

    @Test
    fun `device owner with accessibility requires confirmation and schedules a timed session`() = runTest {
        val ownerBackend = FakeEnforcement(false)
        val coordinator = EnforcementCoordinator(enforcement, ownerBackend, FakeDeviceOwnerController())
        val engine = newEngine(backgroundScope, coordinator)
        metricProvider.values = mapOf("steps" to 10_300.0)
        engine.refreshAll()
        engine.onAppForegrounded(SOCIAL)
        assertEquals(listOf(SOCIAL to "b1"), enforcement.blocked)
        assertNull(gateStateRepository.stored("b1")?.activeSession)
        engine.onAppForegrounded("dev.hablock.app")
        engine.unlock("b1")
        assertEquals(1, alarmScheduler.sessionEnds.size)
        assertIs<GateState.SessionActive>(engine.states.value.getValue("b1"))
        engine.onAppForegrounded(SOCIAL)
        clock.advance(31 * 60_000L)
        engine.onSessionExpired("b1")
        assertEquals(2, enforcement.blocked.size)
        assertIs<GateState.Locked>(ownerBackend.applied.last().getValue("b1"))
    }

    @Test
    fun `missed midnight reset does not revive an expired session`() = runTest {
        clock.current = Instant.parse("2026-08-22T21:50:00Z")
        metricProvider.values = mapOf("steps" to 10_300.0)
        newEngine(backgroundScope).unlock("b1")
        clock.advance(40 * 60_000L)
        metricProvider.values = emptyMap()
        val restarted = newEngine(backgroundScope)
        restarted.refreshAll()
        val stored = assertNotNull(gateStateRepository.stored("b1"))
        assertNull(stored.activeSession)
        assertEquals(dayClock.dayKey(), stored.dayKey)
        assertEquals(listOf("b1"), alarmScheduler.cancelledSessions)
        assertIs<GateState.Locked>(restarted.states.value.getValue("b1"))
    }

}
