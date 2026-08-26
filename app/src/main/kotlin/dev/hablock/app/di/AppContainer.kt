package dev.hablock.app.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dev.hablock.app.BuildConfig
import dev.hablock.app.data.AndroidInstalledAppsRepository
import dev.hablock.app.data.AndroidPermissionChecker
import dev.hablock.app.data.AndroidUsageStatsRepository
import dev.hablock.app.data.DataStoreBlockRepository
import dev.hablock.app.data.DataStoreGateStateRepository
import dev.hablock.app.data.DataStoreSettingsRepository
import dev.hablock.app.data.DataStoreSuspensionStore
import dev.hablock.app.data.DevicePolicyController
import dev.hablock.app.data.HealthConnectRepository
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.enforcement.EnforcementCoordinator
import dev.hablock.app.domain.enforcement.SuspensionStore
import dev.hablock.app.domain.repository.BlockRepository
import dev.hablock.app.domain.repository.GateStateRepository
import dev.hablock.app.domain.repository.HealthRepository
import dev.hablock.app.domain.repository.InstalledAppsRepository
import dev.hablock.app.domain.repository.PermissionChecker
import dev.hablock.app.domain.repository.SettingsRepository
import dev.hablock.app.domain.repository.UsageStatsRepository
import dev.hablock.app.domain.service.AlarmScheduler
import dev.hablock.app.domain.service.DayClock
import dev.hablock.app.domain.service.DefaultGateEngine
import dev.hablock.app.domain.service.DefaultMetricProvider
import dev.hablock.app.domain.service.DefaultRelinquishTimer
import dev.hablock.app.domain.service.GateEngine
import dev.hablock.app.domain.service.MetricProvider
import dev.hablock.app.domain.service.Notifier
import dev.hablock.app.domain.service.RelinquishTimer
import dev.hablock.app.enforcement.AccessibilityEnforcementBackend
import dev.hablock.app.enforcement.DeviceOwnerEnforcementBackend
import dev.hablock.app.system.AndroidAlarmScheduler
import dev.hablock.app.system.GateNotifier
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

private val Context.gateDataStore by preferencesDataStore(name = "hablock")

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = appContext.gateDataStore

    val dayClock = DayClock()

    val blockRepository: BlockRepository by lazy { DataStoreBlockRepository(dataStore, json) }
    val gateStateRepository: GateStateRepository by lazy { DataStoreGateStateRepository(dataStore, json) }
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(dataStore) }
    val usageStatsRepository: UsageStatsRepository by lazy { AndroidUsageStatsRepository(appContext) }
    val healthRepository: HealthRepository by lazy { HealthConnectRepository(appContext) }
    val installedAppsRepository: InstalledAppsRepository by lazy { AndroidInstalledAppsRepository(appContext) }
    val permissionChecker: PermissionChecker by lazy { AndroidPermissionChecker(appContext) }
    val deviceOwnerController: DeviceOwnerController by lazy { DevicePolicyController(appContext) }
    private val suspensionStore: SuspensionStore by lazy { DataStoreSuspensionStore(dataStore) }

    val alarmScheduler: AlarmScheduler by lazy { AndroidAlarmScheduler(appContext) }
    val notifier: Notifier by lazy { GateNotifier(appContext) }

    val metricProvider: MetricProvider by lazy {
        DefaultMetricProvider(usageStatsRepository, healthRepository)
    }

    private val accessibilityBackend: EnforcementBackend by lazy {
        AccessibilityEnforcementBackend(appContext, permissionChecker)
    }
    private val deviceOwnerBackend: EnforcementBackend by lazy {
        DeviceOwnerEnforcementBackend(deviceOwnerController, suspensionStore)
    }
    val enforcement: EnforcementBackend by lazy {
        EnforcementCoordinator(accessibilityBackend, deviceOwnerBackend, deviceOwnerController)
    }

    val gateEngine: GateEngine by lazy {
        DefaultGateEngine(
            blockRepository = blockRepository,
            gateStateRepository = gateStateRepository,
            metricProvider = metricProvider,
            dayClock = dayClock,
            alarmScheduler = alarmScheduler,
            notifier = notifier,
            enforcement = enforcement,
            sessionDuration = if (BuildConfig.DEBUG) 1.minutes else GateConstants.SESSION_DURATION,
            scope = applicationScope,
        )
    }

    val relinquishTimer: RelinquishTimer by lazy {
        DefaultRelinquishTimer(settingsRepository, deviceOwnerController, dayClock, blockRepository, alarmScheduler, suspensionStore)
    }
}
