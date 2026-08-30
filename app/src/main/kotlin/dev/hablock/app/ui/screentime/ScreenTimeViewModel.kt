package dev.hablock.app.ui.screentime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hablock.app.domain.repository.InstalledAppsRepository
import dev.hablock.app.domain.repository.PermissionChecker
import dev.hablock.app.domain.repository.UsageStatsRepository
import dev.hablock.app.domain.service.DayClock
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UsageRow(val packageName: String, val label: String, val minutes: Double)

data class ScreenTimeUiState(
    val loading: Boolean = true,
    val hasAccess: Boolean = true,
    val rows: List<UsageRow> = emptyList(),
    val totalMinutes: Double = 0.0,
    val since: Instant = Instant.EPOCH,
)

private const val MIN_VISIBLE_MINUTES = 1.0

class ScreenTimeViewModel(
    private val usageStatsRepository: UsageStatsRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val permissionChecker: PermissionChecker,
    private val dayClock: DayClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScreenTimeUiState())
    val uiState: StateFlow<ScreenTimeUiState> = _uiState.asStateFlow()

    private var labels: Map<String, String> = emptyMap()
    private var homes: Set<String> = emptySet()

    fun refresh(): Job = viewModelScope.launch {
        if (!permissionChecker.hasUsageAccess()) {
            _uiState.value = ScreenTimeUiState(loading = false, hasAccess = false)
            return@launch
        }
        if (labels.isEmpty()) {
            labels = runCatching { installedAppsRepository.launcherApps() }
                .getOrDefault(emptyList())
                .associate { it.packageName to it.label }
            homes = runCatching { installedAppsRepository.homePackages() }.getOrDefault(emptySet())
        }
        val (from, to) = dayClock.dayWindow()
        val entries = runCatching { usageStatsRepository.usageByApp(from, to) }.getOrDefault(emptyList())
        val rows = entries
            .filter { it.minutes >= MIN_VISIBLE_MINUTES && it.packageName !in homes }
            .sortedByDescending { it.minutes }
            .map { UsageRow(it.packageName, labels[it.packageName] ?: it.packageName.substringAfterLast('.'), it.minutes) }
        _uiState.value = ScreenTimeUiState(
            loading = false,
            hasAccess = true,
            rows = rows,
            totalMinutes = rows.sumOf { it.minutes },
            since = from,
        )
    }
}
