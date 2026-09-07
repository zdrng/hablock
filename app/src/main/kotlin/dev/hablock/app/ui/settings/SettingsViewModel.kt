package dev.hablock.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hablock.app.domain.archive.ArchiveImportPreview
import dev.hablock.app.domain.archive.ArchiveImportResult
import dev.hablock.app.domain.archive.ArchiveService
import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.model.EmergencyUnlockState
import dev.hablock.app.domain.model.EnforcementDiagnostics
import dev.hablock.app.domain.model.HcAvailability
import dev.hablock.app.domain.model.OverlapPolicy
import dev.hablock.app.domain.model.RelinquishState
import dev.hablock.app.domain.repository.HealthRepository
import dev.hablock.app.domain.repository.PermissionChecker
import dev.hablock.app.domain.repository.SettingsRepository
import dev.hablock.app.domain.service.BehaviorSettings
import dev.hablock.app.domain.service.BehaviorSettingsService
import dev.hablock.app.domain.service.DiagnosticsService
import dev.hablock.app.domain.service.RelinquishTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accessibility: Boolean = false,
    val usageAccess: Boolean = false,
    val exactAlarms: Boolean = true,
    val deviceOwner: Boolean = false,
    val relinquishFailed: Boolean = false,
    val healthSupported: Boolean = true,
    val health: Boolean = false,
)

sealed interface ArchiveUiState {
    data object Idle : ArchiveUiState
    data object Working : ArchiveUiState
    data class Preview(val rawJson: String, val details: ArchiveImportPreview) : ArchiveUiState
    data class Exported(val blockCount: Int, val historyCount: Int) : ArchiveUiState
    data class Imported(val result: ArchiveImportResult) : ArchiveUiState
    data class Failed(val operation: Operation) : ArchiveUiState

    enum class Operation { EXPORT, IMPORT }
}

class SettingsViewModel(
    private val permissionChecker: PermissionChecker,
    private val deviceOwnerController: DeviceOwnerController,
    private val relinquishTimer: RelinquishTimer,
    private val healthRepository: HealthRepository,
    val settingsRepository: SettingsRepository,
    private val behaviorSettingsService: BehaviorSettingsService,
    private val diagnosticsService: DiagnosticsService,
    private val archiveService: ArchiveService,
    private val onArchiveImported: suspend (restoredGlobalSettings: Boolean) -> Unit,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val relinquish: StateFlow<RelinquishState> =
        relinquishTimer.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RelinquishState.Idle)

    val emergencyUnlocks: StateFlow<EmergencyUnlockState> =
        settingsRepository.emergencyUnlocks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmergencyUnlockState())

    val behaviorSettings: StateFlow<BehaviorSettings> = behaviorSettingsService.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        BehaviorSettings(OverlapPolicy.ALL_BLOCKS, 0, changesLocked = false),
    )

    val diagnostics: StateFlow<EnforcementDiagnostics> = diagnosticsService.diagnostics

    private val _archiveState = MutableStateFlow<ArchiveUiState>(ArchiveUiState.Idle)
    val archiveState: StateFlow<ArchiveUiState> = _archiveState.asStateFlow()

    val healthPermissions: Set<String> get() = healthRepository.requiredPermissions()

    fun refresh() {
        _uiState.value = _uiState.value.copy(
            accessibility = permissionChecker.isAccessibilityServiceEnabled(),
            usageAccess = permissionChecker.hasUsageAccess(),
            exactAlarms = permissionChecker.canScheduleExactAlarms(),
            deviceOwner = deviceOwnerController.isDeviceOwner(),
        )
        viewModelScope.launch {
            diagnosticsService.refreshEnvironment()
            val availability = runCatching { healthRepository.availability() }.getOrDefault(HcAvailability.UNAVAILABLE)
            val granted = runCatching { healthRepository.hasAllPermissions() }.getOrDefault(false)
            _uiState.value = _uiState.value.copy(
                healthSupported = availability == HcAvailability.FULL || availability == HcAvailability.NO_MINDFULNESS,
                health = granted,
            )
        }
    }

    fun startRelinquish() = viewModelScope.launch { relinquishTimer.start() }

    fun cancelRelinquish() = viewModelScope.launch { relinquishTimer.cancel() }

    fun setOverlapPolicy(policy: OverlapPolicy) = viewModelScope.launch {
        behaviorSettingsService.setOverlapPolicy(policy)
    }

    fun setDayBoundaryMinutes(minutes: Int) = viewModelScope.launch {
        behaviorSettingsService.setDayBoundaryMinutes(minutes)
    }

    fun exportArchive(writeUtf8: suspend (String) -> Unit) = viewModelScope.launch {
        _archiveState.value = ArchiveUiState.Working
        _archiveState.value = runCatching {
            archiveService.export().also { writeUtf8(it.json) }
        }.fold(
            onSuccess = { ArchiveUiState.Exported(it.blockCount, it.historyCount) },
            onFailure = { ArchiveUiState.Failed(ArchiveUiState.Operation.EXPORT) },
        )
    }

    fun previewImport(readUtf8: suspend () -> String) = viewModelScope.launch {
        _archiveState.value = ArchiveUiState.Working
        _archiveState.value = runCatching {
            val rawJson = readUtf8()
            ArchiveUiState.Preview(rawJson, archiveService.preview(rawJson))
        }.getOrElse { ArchiveUiState.Failed(ArchiveUiState.Operation.IMPORT) }
    }

    fun confirmImport() {
        val pending = _archiveState.value as? ArchiveUiState.Preview ?: return
        viewModelScope.launch {
            _archiveState.value = ArchiveUiState.Working
            _archiveState.value = runCatching { archiveService.import(pending.rawJson) }.fold(
                onSuccess = { result ->
                    runCatching { onArchiveImported(result.restoredGlobalSettings) }
                    ArchiveUiState.Imported(result)
                },
                onFailure = { ArchiveUiState.Failed(ArchiveUiState.Operation.IMPORT) },
            )
        }
    }

    fun dismissArchiveStatus() {
        _archiveState.value = ArchiveUiState.Idle
    }

    fun confirmRelinquish() = viewModelScope.launch {
        val succeeded = relinquishTimer.confirmRelinquish()
        _uiState.value = _uiState.value.copy(relinquishFailed = !succeeded)
        refresh()
    }
}
