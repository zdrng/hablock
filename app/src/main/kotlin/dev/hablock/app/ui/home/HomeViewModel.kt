package dev.hablock.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.repository.BlockRepository
import dev.hablock.app.domain.repository.GateStateRepository
import dev.hablock.app.domain.service.DayClock
import dev.hablock.app.domain.service.GateEngine
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlockUi(val block: Block, val state: GateState?)

data class HomeUiState(
    val loading: Boolean = true,
    val blocks: List<BlockUi> = emptyList(),
    val today: Instant = Instant.EPOCH,
)

class HomeViewModel(
    private val blockRepository: BlockRepository,
    private val gateStateRepository: GateStateRepository,
    private val gateEngine: GateEngine,
    dayClock: DayClock,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(blockRepository.blocks, gateEngine.states) { blocks, states ->
            blocks.map { BlockUi(it, states[it.id]) }
        }
            .map { HomeUiState(loading = false, blocks = it, today = dayClock.now()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setEnabled(block: Block, enabled: Boolean) {
        viewModelScope.launch {
            blockRepository.upsert(block.copy(enabled = enabled))
            gateEngine.refreshAll()
        }
    }

    fun delete(blockId: String) {
        viewModelScope.launch {
            blockRepository.delete(blockId)
            gateStateRepository.delete(blockId)
            gateEngine.refreshAll()
        }
    }

    fun refresh() {
        viewModelScope.launch { gateEngine.refreshAll() }
    }
}
