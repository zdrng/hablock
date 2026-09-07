package dev.hablock.app.di

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.datastore.preferences.preferencesDataStore
import dev.hablock.app.data.AndroidInstalledAppsRepository
import dev.hablock.app.data.AndroidPermissionChecker
import dev.hablock.app.data.AndroidUsageStatsRepository
import dev.hablock.app.data.DataStoreSettingsRepository
import dev.hablock.app.data.DataStoreSuspensionStore
import dev.hablock.app.data.DevicePolicyController
import dev.hablock.app.data.DiagnosticsRuntime
import dev.hablock.app.data.HealthConnectRepository
import dev.hablock.app.data.db.HablockDatabase
import dev.hablock.app.data.db.RoomArchiveStore
import dev.hablock.app.data.db.RoomBlockRepository
import dev.hablock.app.data.db.RoomGateStateRepository
import dev.hablock.app.data.db.RoomHistoryRepository
import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.enforcement.EnforcementBackend
import dev.hablock.app.domain.enforcement.EnforcementCoordinator
import dev.hablock.app.domain.enforcement.SuspensionStore
import dev.hablock.app.domain.archive.ArchiveService
import dev.hablock.app.domain.repository.BlockRepository
import dev.hablock.app.domain.repository.GateStateRepository
import dev.hablock.app.domain.repository.HealthRepository
import dev.hablock.app.domain.repository.HistoryRepository
import dev.hablock.app.domain.repository.InstalledAppsRepository
import dev.hablock.app.domain.repository.PermissionChecker
import dev.hablock.app.domain.repository.SettingsRepository
import dev.hablock.app.domain.repository.UsageStatsRepository
import dev.hablock.app.domain.service.AlarmScheduler
import dev.hablock.app.domain.service.BehaviorSettingsService
import dev.hablock.app.domain.service.DayClock
import dev.hablock.app.domain.service.DefaultGateEngine
import dev.hablock.app.domain.service.DefaultDiagnosticsService
import dev.hablock.app.domain.service.DefaultMetricProvider
import dev.hablock.app.domain.service.DefaultRelinquishTimer
import dev.hablock.app.domain.service.GateEngine
import dev.hablock.app.domain.service.DiagnosticsService
import dev.hablock.app.domain.service.MetricProvider
import dev.hablock.app.domain.service.Notifier
import dev.hablock.app.domain.service.RelinquishTimer
import dev.hablock.app.enforcement.AccessibilityEnforcementBackend
import dev.hablock.app.enforcement.DeviceOwnerEnforcementBackend
import dev.hablock.app.enforcement.DiagnosticsEnforcementBackend
import dev.hablock.app.system.AndroidAlarmScheduler
import dev.hablock.app.system.GateNotifier
import dev.hablock.app.ui.MainActivity
import dev.hablock.app.ui.blocked.AndroidBlockedScreenLauncher
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.gateDataStore by preferencesDataStore(name = "hablock")

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dataStore = appContext.gateDataStore
    val database: HablockDatabase by lazy {
        Room.databaseBuilder(appContext, HablockDatabase::class.java, "hablock.db").build()
    }

    val blockRepository: BlockRepository by lazy { RoomBlockRepository(database.blockDao()) }
    val gateStateRepository: GateStateRepository by lazy { RoomGateStateRepository(database.gateStateDao()) }
    val historyRepository: HistoryRepository by lazy { RoomHistoryRepository(database.historyDao()) }
    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(dataStore) }

    private val dayBoundaryMinutes = AtomicInteger()
    private val dayBoundaryReady = applicationScope.async {
        dayBoundaryMinutes.set(settingsRepository.dayBoundaryMinutes.first())
    }
    private val configuredDayClock = DayClock(boundaryMinutesProvider = dayBoundaryMinutes::get)

    /** Every caller sees the persisted boundary; no enforcement can transiently evaluate at midnight. */
    val dayClock: DayClock
        get() {
            runBlocking { dayBoundaryReady.await() }
            return configuredDayClock
        }

    val usageStatsRepository: UsageStatsRepository by lazy { AndroidUsageStatsRepository(appContext) }
    val healthRepository: HealthRepository by lazy { HealthConnectRepository(appContext) }
    val installedAppsRepository: InstalledAppsRepository by lazy { AndroidInstalledAppsRepository(appContext) }
    val permissionChecker: PermissionChecker by lazy { AndroidPermissionChecker(appContext) }
    val deviceOwnerController: DeviceOwnerController by lazy { DevicePolicyController(appContext) }
    private val suspensionStore: SuspensionStore by lazy { DataStoreSuspensionStore(dataStore) }
    private val diagnosticsRecorder = DiagnosticsRuntime.repository

    val alarmScheduler: AlarmScheduler by lazy { AndroidAlarmScheduler(appContext, diagnosticsRecorder) }
    val notifier: Notifier by lazy {
        GateNotifier(appContext, applicationScope) {
            PendingIntent.getActivity(
                appContext,
                0,
                Intent(appContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    val metricProvider: MetricProvider by lazy {
        DefaultMetricProvider(usageStatsRepository, healthRepository)
    }

    private val accessibilityBackend: EnforcementBackend by lazy {
        AccessibilityEnforcementBackend(AndroidBlockedScreenLauncher(appContext), permissionChecker)
    }
    private val deviceOwnerBackend: EnforcementBackend by lazy {
        DeviceOwnerEnforcementBackend(deviceOwnerController, suspensionStore, diagnosticsRecorder)
    }
    private val routedEnforcement: EnforcementBackend by lazy {
        EnforcementCoordinator(accessibilityBackend, deviceOwnerBackend, deviceOwnerController)
    }
    val enforcement: EnforcementBackend by lazy {
        DiagnosticsEnforcementBackend(routedEnforcement, deviceOwnerController, diagnosticsRecorder)
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
            scope = applicationScope,
            overlapPolicies = settingsRepository.overlapPolicy,
            initialOverlapPolicy = runBlocking { settingsRepository.overlapPolicy.first() },
            diagnostics = diagnosticsRecorder,
            historyRepository = historyRepository,
        )
    }

    val diagnosticsService: DiagnosticsService by lazy {
        DefaultDiagnosticsService(
            repository = DiagnosticsRuntime.repository,
            recorder = diagnosticsRecorder,
            permissionChecker = permissionChecker,
            healthRepository = healthRepository,
            deviceOwnerController = deviceOwnerController,
        )
    }

    val archiveService: ArchiveService by lazy {
        ArchiveService(
            store = RoomArchiveStore(database, settingsRepository),
            forbiddenBlockedPackages = {
                runCatching { installedAppsRepository.homePackages() }
                    .getOrDefault(emptySet()) + appContext.packageName
            },
        )
    }

    suspend fun refreshAfterArchiveImport(restoredGlobalSettings: Boolean) {
        if (restoredGlobalSettings) {
            dayBoundaryMinutes.set(settingsRepository.dayBoundaryMinutes.first())
            alarmScheduler.scheduleDayReset(configuredDayClock.nextReset())
        }
        gateEngine.refreshAll()
    }

    val behaviorSettingsService: BehaviorSettingsService by lazy {
        BehaviorSettingsService(
            settingsRepository = settingsRepository,
            blockRepository = blockRepository,
            onOverlapPolicyChanged = { gateEngine.refreshAll() },
            onDayBoundaryChanged = {
                dayBoundaryMinutes.set(settingsRepository.dayBoundaryMinutes.first())
                gateEngine.onDayBoundaryChanged()
            },
        )
    }

    val relinquishTimer: RelinquishTimer by lazy {
        DefaultRelinquishTimer(settingsRepository, deviceOwnerController, dayClock, blockRepository, alarmScheduler, suspensionStore)
    }
}
