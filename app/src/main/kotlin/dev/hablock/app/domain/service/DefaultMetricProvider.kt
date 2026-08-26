package dev.hablock.app.domain.service

import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.MetricSnapshot
import dev.hablock.app.domain.repository.HealthRepository
import dev.hablock.app.domain.repository.UsageStatsRepository
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

private sealed interface MetricKey {
    data class Usage(val packageName: String) : MetricKey
    data object Steps : MetricKey
    data object Exercise : MetricKey
    data object Mindful : MetricKey
}

class DefaultMetricProvider(
    private val usageStatsRepository: UsageStatsRepository,
    private val healthRepository: HealthRepository,
) : MetricProvider {

    override suspend fun snapshot(conditions: List<Condition>, from: Instant, to: Instant): MetricSnapshot {
        val reads = mutableMapOf<MetricKey, Double>()
        val values = mutableMapOf<String, Double>()
        for (condition in conditions) {
            val key = keyOf(condition)
            values[condition.id] = reads[key] ?: read(key, from, to).also { reads[key] = it }
        }
        return MetricSnapshot(values)
    }

    private fun keyOf(condition: Condition): MetricKey = when (condition) {
        is Condition.AppUsage -> MetricKey.Usage(condition.packageName)
        is Condition.Steps -> MetricKey.Steps
        is Condition.Exercise -> MetricKey.Exercise
        is Condition.Meditation -> MetricKey.Mindful
    }

    private suspend fun read(key: MetricKey, from: Instant, to: Instant): Double = try {
        when (key) {
            is MetricKey.Usage -> usageStatsRepository.foregroundMinutes(key.packageName, from, to)
            MetricKey.Steps -> healthRepository.steps(from, to)
            MetricKey.Exercise -> healthRepository.exerciseMinutes(from, to)
            MetricKey.Mindful -> healthRepository.mindfulMinutes(from, to)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        0.0
    }
}
