package dev.hablock.app.ui.blocked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.ConditionProgress
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.repository.BlockRepository
import dev.hablock.app.domain.service.GateEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlockedUiState(
    val block: Block? = null,
    val state: GateState? = null,
) {
    val progress: List<ConditionProgress>
        get() = state?.progress ?: block?.conditions?.map { ConditionProgress(it, 0.0, it.goal) }.orEmpty()
}

class BlockedViewModel(
    blockRepository: BlockRepository,
    private val gateEngine: GateEngine,
    private val blockId: String,
) : ViewModel() {

    val uiState: StateFlow<BlockedUiState> =
        combine(blockRepository.blocks, gateEngine.states) { blocks, states ->
            BlockedUiState(blocks.firstOrNull { it.id == blockId }, states[blockId])
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockedUiState())

    fun refresh(): Job = viewModelScope.launch { gateEngine.refreshAll() }
}
