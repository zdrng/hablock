package dev.hablock.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.model.HcAvailability
import dev.hablock.app.domain.model.healthConnectProblem
import dev.hablock.app.domain.repository.BlockRepository
import dev.hablock.app.domain.repository.HealthRepository
import dev.hablock.app.domain.service.DayClock
import dev.hablock.app.domain.service.GateEngine
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlockUi(val block: Block, val state: GateState?)

data class HomeUiState(
    val loading: Boolean = true,
    val blocks: List<BlockUi> = emptyList(),
    val today: Instant = Instant.EPOCH,
    val hcProblem: Boolean = false,
)

class HomeViewModel(
    private val blockRepository: BlockRepository,
    private val gateEngine: GateEngine,
    private val healthRepository: HealthRepository,
    dayClock: DayClock,
) : ViewModel() {

    private val hcProblem = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> =
        combine(blockRepository.blocks, gateEngine.states, hcProblem) { blocks, states, hc ->
            HomeUiState(
                loading = false,
                blocks = blocks.map { BlockUi(it, states[it.id]) },
                today = dayClock.now(),
                hcProblem = hc,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val healthPermissions: Set<String> get() = healthRepository.requiredPermissions()

    fun setEnabled(block: Block, enabled: Boolean) {
        viewModelScope.launch {
            blockRepository.upsert(block.copy(enabled = enabled))
            gateEngine.refreshAll()
        }
    }

    fun delete(blockId: String) {
        viewModelScope.launch { gateEngine.deleteBlock(blockId) }
    }

    fun refresh(): Job = viewModelScope.launch {
        gateEngine.refreshAll()
        val availability = runCatching { healthRepository.availability() }.getOrDefault(HcAvailability.UNAVAILABLE)
        val granted = runCatching { healthRepository.hasAllPermissions() }.getOrDefault(false)
        hcProblem.value = healthConnectProblem(blockRepository.current(), availability, granted)
    }
}
