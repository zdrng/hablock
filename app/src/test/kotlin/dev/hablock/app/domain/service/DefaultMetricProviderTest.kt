package dev.hablock.app.domain.service

import dev.hablock.app.domain.model.Condition
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class DefaultMetricProviderTest {

    private val from: Instant = Instant.parse("2026-08-23T02:00:00Z")
    private val to: Instant = Instant.parse("2026-08-23T09:00:00Z")

    @Test
    fun `each condition type reads from its source`() = runTest {
        val provider = DefaultMetricProvider(
            FakeUsageStatsRepository(mapOf("com.example.reader" to 12.0)),
            FakeHealthRepository(steps = 8_000.0, exercise = 45.0, mindful = 5.0),
        )
        val conditions = listOf(
            Condition.AppUsage("usage", 15.0, "com.example.reader", "Reader"),
            Condition.Steps("steps", 10_000.0),
            Condition.Exercise("workout", 60.0),
            Condition.Meditation("meditation", 10.0),
        )

        val snapshot = provider.snapshot(conditions, from, to)

        assertEquals(12.0, snapshot.valueOf("usage"))
        assertEquals(8_000.0, snapshot.valueOf("steps"))
        assertEquals(45.0, snapshot.valueOf("workout"))
        assertEquals(5.0, snapshot.valueOf("meditation"))
    }

    @Test
    fun `identical lookups are deduped within one snapshot`() = runTest {
        val usage = FakeUsageStatsRepository(mapOf("com.example.reader" to 12.0))
        val health = FakeHealthRepository(steps = 8_000.0)
        val provider = DefaultMetricProvider(usage, health)
        val conditions = listOf(
            Condition.AppUsage("usageA", 15.0, "com.example.reader", "Reader"),
            Condition.AppUsage("usageB", 30.0, "com.example.reader", "Reader"),
            Condition.Steps("stepsA", 10_000.0),
            Condition.Steps("stepsB", 20_000.0),
        )

        val snapshot = provider.snapshot(conditions, from, to)

        assertEquals(1, usage.calls)
        assertEquals(1, health.stepCalls)
        assertEquals(12.0, snapshot.valueOf("usageB"))
        assertEquals(8_000.0, snapshot.valueOf("stepsB"))
    }

    @Test
    fun `a failing source contributes zero`() = runTest {
        val provider = DefaultMetricProvider(
            FakeUsageStatsRepository(mapOf("com.example.reader" to 12.0)),
            FakeHealthRepository(steps = 8_000.0, failing = true),
        )
        val conditions = listOf(
            Condition.AppUsage("usage", 15.0, "com.example.reader", "Reader"),
            Condition.Steps("steps", 10_000.0),
        )

        val snapshot = provider.snapshot(conditions, from, to)

        assertEquals(12.0, snapshot.valueOf("usage"))
        assertEquals(0.0, snapshot.valueOf("steps"))
    }
}
