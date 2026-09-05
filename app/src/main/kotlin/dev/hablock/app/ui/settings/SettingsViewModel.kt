package dev.hablock.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.model.EmergencyUnlockState
import dev.hablock.app.domain.model.HcAvailability
import dev.hablock.app.domain.model.RelinquishState
import dev.hablock.app.domain.repository.HealthRepository
import dev.hablock.app.domain.repository.PermissionChecker
import dev.hablock.app.domain.repository.SettingsRepository
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

class SettingsViewModel(
    private val permissionChecker: PermissionChecker,
    private val deviceOwnerController: DeviceOwnerController,
    private val relinquishTimer: RelinquishTimer,
    private val healthRepository: HealthRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val relinquish: StateFlow<RelinquishState> =
        relinquishTimer.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RelinquishState.Idle)

    val emergencyUnlocks: StateFlow<EmergencyUnlockState> =
        settingsRepository.emergencyUnlocks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmergencyUnlockState())

    val healthPermissions: Set<String> get() = healthRepository.requiredPermissions()

    fun refresh() {
        _uiState.value = _uiState.value.copy(
            accessibility = permissionChecker.isAccessibilityServiceEnabled(),
            usageAccess = permissionChecker.hasUsageAccess(),
            exactAlarms = permissionChecker.canScheduleExactAlarms(),
            deviceOwner = deviceOwnerController.isDeviceOwner(),
        )
        viewModelScope.launch {
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

    fun confirmRelinquish() = viewModelScope.launch {
        val succeeded = relinquishTimer.confirmRelinquish()
        _uiState.value = _uiState.value.copy(relinquishFailed = !succeeded)
        refresh()
    }
}
