package dev.hablock.app.ui.blocked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.ConditionProgress
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.model.HcAvailability
import dev.hablock.app.domain.model.usesHealthConnect
import dev.hablock.app.domain.repository.BlockRepository
import dev.hablock.app.domain.repository.HealthRepository
import dev.hablock.app.domain.service.GateEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlockedUiState(
    val block: Block? = null,
    val state: GateState? = null,
    val hcProblem: Boolean = false,
) {
    val progress: List<ConditionProgress>
        get() = state?.progress ?: block?.conditions?.map { ConditionProgress(it, 0.0, it.goal) }.orEmpty()
}

class BlockedViewModel(
    blockRepository: BlockRepository,
    private val gateEngine: GateEngine,
    private val healthRepository: HealthRepository,
    private val blockId: String,
) : ViewModel() {

    private val hcProblem = MutableStateFlow(false)

    val uiState: StateFlow<BlockedUiState> =
        combine(blockRepository.blocks, gateEngine.states, hcProblem) { blocks, states, hc ->
            val block = blocks.firstOrNull { it.id == blockId }
            BlockedUiState(block, states[blockId], hc && block?.usesHealthConnect() == true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockedUiState())

    init {
        viewModelScope.launch { updateHcProblem() }
    }

    fun refresh(): Job = viewModelScope.launch {
        gateEngine.refreshAll()
        updateHcProblem()
    }

    private suspend fun updateHcProblem() {
        val availability = runCatching { healthRepository.availability() }.getOrDefault(HcAvailability.UNAVAILABLE)
        val granted = runCatching { healthRepository.hasAllPermissions() }.getOrDefault(false)
        hcProblem.value = availability == HcAvailability.NEEDS_INSTALL ||
            availability == HcAvailability.UNAVAILABLE || !granted
    }
}
