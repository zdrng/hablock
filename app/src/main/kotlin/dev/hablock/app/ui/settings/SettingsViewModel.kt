package dev.hablock.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hablock.app.domain.enforcement.DeviceOwnerController
import dev.hablock.app.domain.model.RelinquishState
import dev.hablock.app.domain.repository.PermissionChecker
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
)

class SettingsViewModel(
    private val permissionChecker: PermissionChecker,
    private val deviceOwnerController: DeviceOwnerController,
    private val relinquishTimer: RelinquishTimer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val relinquish: StateFlow<RelinquishState> =
        relinquishTimer.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RelinquishState.Idle)

    fun refresh() {
        _uiState.value = SettingsUiState(
            accessibility = permissionChecker.isAccessibilityServiceEnabled(),
            usageAccess = permissionChecker.hasUsageAccess(),
            exactAlarms = permissionChecker.canScheduleExactAlarms(),
            deviceOwner = deviceOwnerController.isDeviceOwner(),
        )
    }

    fun startRelinquish() = viewModelScope.launch { relinquishTimer.start() }

    fun cancelRelinquish() = viewModelScope.launch { relinquishTimer.cancel() }

    fun confirmRelinquish() = viewModelScope.launch {
        relinquishTimer.confirmRelinquish()
        refresh()
    }
}
