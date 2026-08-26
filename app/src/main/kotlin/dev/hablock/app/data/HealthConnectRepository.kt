package dev.hablock.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dev.hablock.app.domain.model.HcAvailability
import dev.hablock.app.domain.repository.HealthRepository
import java.time.Duration
import java.time.Instant

private const val MILLIS_PER_MINUTE = 60_000.0

class HealthConnectRepository(context: Context) : HealthRepository {

    private val appContext = context.applicationContext

    private val readStepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
    private val readExercisePermission = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    private val readMindfulnessPermission = HealthPermission.getReadPermission(MindfulnessSessionRecord::class)

    private val client: HealthConnectClient? by lazy {
        if (sdkStatus() == HealthConnectClient.SDK_AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(appContext) }.getOrNull()
        } else {
            null
        }
    }

    override suspend fun availability(): HcAvailability = when (sdkStatus()) {
        HealthConnectClient.SDK_UNAVAILABLE -> HcAvailability.UNAVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.NEEDS_INSTALL
        else -> if (mindfulnessSupported()) HcAvailability.FULL else HcAvailability.NO_MINDFULNESS
    }

    override suspend fun hasAllPermissions(): Boolean = runCatching {
        val granted = client?.permissionController?.getGrantedPermissions().orEmpty()
        granted.containsAll(setOf(readStepsPermission, readExercisePermission))
    }.getOrDefault(false)

    override fun requiredPermissions(): Set<String> =
        setOf(readStepsPermission, readExercisePermission, readMindfulnessPermission)

    override suspend fun steps(from: Instant, to: Instant): Double = readOrZero { hc ->
        val response = hc.aggregate(
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(from, to)),
        )
        (response[StepsRecord.COUNT_TOTAL] ?: 0L).toDouble()
    }

    override suspend fun exerciseMinutes(from: Instant, to: Instant): Double = readOrZero { hc ->
        val response = hc.aggregate(
            AggregateRequest(
                setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL),
                TimeRangeFilter.between(from, to),
            ),
        )
        val total = response[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL] ?: Duration.ZERO
        total.toMillis() / MILLIS_PER_MINUTE
    }

    override suspend fun mindfulMinutes(from: Instant, to: Instant): Double = readOrZero { hc ->
        val records = hc.readRecords(
            ReadRecordsRequest(MindfulnessSessionRecord::class, TimeRangeFilter.between(from, to)),
        ).records
        records.sumOf { record -> Duration.between(record.startTime, record.endTime).toMillis() } / MILLIS_PER_MINUTE
    }

    private fun sdkStatus(): Int = runCatching { HealthConnectClient.getSdkStatus(appContext) }
        .getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

    private fun mindfulnessSupported(): Boolean = runCatching {
        client?.features?.getFeatureStatus(HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    private suspend fun readOrZero(read: suspend (HealthConnectClient) -> Double): Double {
        val hc = client ?: return 0.0
        return runCatching { read(hc) }.getOrDefault(0.0)
    }
}
