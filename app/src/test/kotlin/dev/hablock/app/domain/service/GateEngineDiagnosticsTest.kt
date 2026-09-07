package dev.hablock.app.domain.service

import dev.hablock.app.data.InMemoryDiagnosticsRepository
import dev.hablock.app.domain.model.BlockSchedule
import dev.hablock.app.domain.model.Condition
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GateEngineDiagnosticsTest {
    private val zone = ZoneId.of("UTC")
    private val clock = MutableClock(Instant.parse("2026-09-07T12:00:00Z"), zone)
    private val dayClock = DayClock(clock, zone)

    @Test
    fun `refresh records evaluation result and counts`() = runTest {
        val diagnostics = InMemoryDiagnosticsRepository { 99L }
        val condition = Condition.Steps("steps", 10_000.0)
        val engine = DefaultGateEngine(
            blockRepository = FakeBlockRepository(listOf(testBlock(conditions = listOf(condition)))),
            gateStateRepository = FakeGateStateRepository(),
            metricProvider = FakeMetricProvider(),
            dayClock = dayClock,
            alarmScheduler = FakeAlarmScheduler(),
            notifier = FakeNotifier(),
            enforcement = FakeEnforcement(),
            scope = backgroundScope,
            diagnostics = diagnostics,
        )

        engine.refreshAll()

        val snapshot = diagnostics.diagnostics.value
        assertTrue(snapshot.lastEvaluation?.succeeded == true)
        assertEquals(99L, snapshot.lastEvaluation?.atMillis)
        assertEquals(1, snapshot.evaluatedBlockCount)
        assertEquals(1, snapshot.blockedBlockCount)
    }

    @Test
    fun `inactive scheduled blocks are not reported as still blocking`() = runTest {
        val diagnostics = InMemoryDiagnosticsRepository { 99L }
        val condition = Condition.Steps("steps", 10_000.0)
        val block = testBlock(conditions = listOf(condition)).copy(
            schedule = BlockSchedule(emptySet(), 0, 0),
        )
        val engine = DefaultGateEngine(
            blockRepository = FakeBlockRepository(listOf(block)),
            gateStateRepository = FakeGateStateRepository(),
            metricProvider = FakeMetricProvider(),
            dayClock = dayClock,
            alarmScheduler = FakeAlarmScheduler(),
            notifier = FakeNotifier(),
            enforcement = FakeEnforcement(),
            scope = backgroundScope,
            diagnostics = diagnostics,
        )

        engine.refreshAll()

        assertEquals(1, diagnostics.diagnostics.value.evaluatedBlockCount)
        assertEquals(0, diagnostics.diagnostics.value.blockedBlockCount)
    }

    @Test
    fun `metric failure records unsuccessful evaluation and still propagates`() = runTest {
        val diagnostics = InMemoryDiagnosticsRepository()
        val condition = Condition.Steps("steps", 10_000.0)
        val engine = DefaultGateEngine(
            blockRepository = FakeBlockRepository(listOf(testBlock(conditions = listOf(condition)))),
            gateStateRepository = FakeGateStateRepository(),
            metricProvider = object : MetricProvider {
                override suspend fun snapshot(
                    conditions: List<Condition>,
                    from: Instant,
                    to: Instant,
                ) = error("provider unavailable")
            },
            dayClock = dayClock,
            alarmScheduler = FakeAlarmScheduler(),
            notifier = FakeNotifier(),
            enforcement = FakeEnforcement(),
            scope = backgroundScope,
            diagnostics = diagnostics,
        )

        assertFailsWith<IllegalStateException> { engine.refreshAll() }

        assertFalse(diagnostics.diagnostics.value.lastEvaluation?.succeeded == true)
        assertEquals(0, diagnostics.diagnostics.value.evaluatedBlockCount)
    }
}
