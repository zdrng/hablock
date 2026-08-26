package dev.hablock.app.domain.service

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
import kotlin.time.Duration.Companion.minutes
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

    private fun newEngine(scope: CoroutineScope) = DefaultGateEngine(
        blockRepository = blockRepository,
        gateStateRepository = gateStateRepository,
        metricProvider = metricProvider,
        dayClock = dayClock,
        alarmScheduler = alarmScheduler,
        notifier = notifier,
        enforcement = enforcement,
        sessionDuration = 30.minutes,
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
    fun `foregrounding a blocked app while open starts a session and schedules its end`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.onAppForegrounded(SOCIAL)

        assertTrue(enforcement.blocked.isEmpty())
        val stored = assertNotNull(gateStateRepository.stored("b1"))
        val session = assertNotNull(stored.activeSession)
        assertEquals(1, stored.unlockCount)
        assertEquals(11_300.0, stored.requiredNow.getValue("steps"), EPS)
        assertEquals(60.0, stored.requiredNow.getValue("workout"), EPS)
        assertEquals(listOf("b1" to Instant.ofEpochMilli(session.endsAtMillis)), alarmScheduler.sessionEnds)
        assertIs<GateState.SessionActive>(engine.states.value.getValue("b1"))
    }

    @Test
    fun `a second foreground event during a session does not ratchet again`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.onAppForegrounded(SOCIAL)
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
        engine.onAppForegrounded(SOCIAL)
        clock.advance(31 * 60 * 1_000L)

        engine.onSessionExpired("b1")

        assertEquals(listOf("Block b1"), notifier.sessionsEnded)
        assertNull(gateStateRepository.stored("b1")?.activeSession)
        assertIs<GateState.Locked>(engine.states.value.getValue("b1"))
    }

    @Test
    fun `an expired session is cleared by a plain refresh too`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.onAppForegrounded(SOCIAL)
        clock.advance(31 * 60 * 1_000L)

        engine.refreshAll()

        assertNull(gateStateRepository.stored("b1")?.activeSession)
        assertTrue(notifier.sessionsEnded.isEmpty())
    }

    @Test
    fun `day reset clears expired state, cancels its alarm and schedules the next reset`() = runTest {
        metricProvider.values = mapOf("steps" to 10_300.0)
        val engine = newEngine(backgroundScope)
        engine.onAppForegrounded(SOCIAL)
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
        engine.onAppForegrounded(SOCIAL)
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
        engine.onAppForegrounded(SOCIAL)
        assertNotNull(gateStateRepository.stored("b1")?.activeSession)

        engine.deleteBlock("b1")

        assertTrue(blockRepository.current().isEmpty())
        assertNull(gateStateRepository.stored("b1"))
        assertEquals(listOf("b1"), alarmScheduler.cancelledSessions)
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
}
