package dev.hablock.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hablock.app.domain.model.HcAvailability
import dev.hablock.app.domain.repository.HealthRepository
import dev.hablock.app.domain.repository.PermissionChecker
import dev.hablock.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GrantsUiState(
    val accessibility: Boolean = false,
    val usageAccess: Boolean = false,
    val notifications: Boolean = false,
    val health: Boolean = false,
    val healthAvailability: HcAvailability = HcAvailability.UNAVAILABLE,
) {
    val healthSupported: Boolean
        get() = healthAvailability == HcAvailability.FULL || healthAvailability == HcAvailability.NO_MINDFULNESS
}

class OnboardingViewModel(
    private val settingsRepository: SettingsRepository,
    private val permissionChecker: PermissionChecker,
    private val healthRepository: HealthRepository,
) : ViewModel() {

    private val _grants = MutableStateFlow(GrantsUiState())
    val grants: StateFlow<GrantsUiState> = _grants.asStateFlow()

    val healthPermissions: Set<String> get() = healthRepository.requiredPermissions()

    fun refreshGrants() {
        viewModelScope.launch {
            val availability = runCatching { healthRepository.availability() }.getOrDefault(HcAvailability.UNAVAILABLE)
            val healthGranted = runCatching { healthRepository.hasAllPermissions() }.getOrDefault(false)
            _grants.update {
                it.copy(
                    accessibility = permissionChecker.isAccessibilityServiceEnabled(),
                    usageAccess = permissionChecker.hasUsageAccess(),
                    notifications = permissionChecker.hasNotificationPermission(),
                    health = healthGranted,
                    healthAvailability = availability,
                )
            }
        }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setOnboardingDone()
            onDone()
        }
    }
}
