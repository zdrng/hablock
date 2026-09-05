package dev.hablock.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.EmergencyUnlockState
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.model.HcAvailability
import dev.hablock.app.domain.model.LockType
import dev.hablock.app.domain.model.hashPasscode
import dev.hablock.app.domain.model.healthConnectProblem
import dev.hablock.app.domain.repository.BlockRepository
import dev.hablock.app.domain.repository.HealthRepository
import dev.hablock.app.domain.repository.SettingsRepository
import dev.hablock.app.domain.service.DayClock
import dev.hablock.app.domain.service.GateEngine
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlockUi(val block: Block, val state: GateState?)

data class HomeUiState(
    val loading: Boolean = true,
    val blocks: List<BlockUi> = emptyList(),
    val today: Instant = Instant.EPOCH,
    val hcProblem: Boolean = false,
    val emergencyUnlocks: EmergencyUnlockState = EmergencyUnlockState(),
)

class HomeViewModel(
    private val blockRepository: BlockRepository,
    private val gateEngine: GateEngine,
    private val healthRepository: HealthRepository,
    private val settingsRepository: SettingsRepository,
    dayClock: DayClock,
) : ViewModel() {

    private val hcProblem = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> =
        combine(blockRepository.blocks, gateEngine.states, hcProblem, settingsRepository.emergencyUnlocks) { blocks, states, hc, emergency ->
            HomeUiState(
                loading = false,
                blocks = blocks.map { BlockUi(it, states[it.id]) },
                today = dayClock.now(),
                hcProblem = hc,
                emergencyUnlocks = emergency,
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

    fun relock(blockId: String) {
        viewModelScope.launch { gateEngine.relock(blockId) }
    }

    fun lockChangesDuration(block: Block, untilMillis: Long) {
        viewModelScope.launch {
            blockRepository.upsert(block.copy(
                lockType = LockType.DURATION,
                blockedUntil = untilMillis,
                lockPasswordHash = null,
            ))
            gateEngine.refreshAll()
        }
    }

    fun lockChangesPassword(block: Block, passcode: String) {
        viewModelScope.launch {
            blockRepository.upsert(block.copy(
                lockType = LockType.PASSWORD,
                blockedUntil = Long.MAX_VALUE,
                lockPasswordHash = hashPasscode(passcode),
            ))
            gateEngine.refreshAll()
        }
    }

    fun unlockWithPassword(block: Block, passcode: String): Boolean {
        val stored = block.lockPasswordHash ?: return false
        if (hashPasscode(passcode) != stored) return false
        viewModelScope.launch {
            blockRepository.upsert(block.copy(
                lockType = null,
                blockedUntil = null,
                lockPasswordHash = null,
            ))
            gateEngine.refreshAll()
        }
        return true
    }

    fun emergencyUnlock(block: Block) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val current = settingsRepository.emergencyUnlocks.let {
                it.first().refillExpired(now)
            }
            val consumed = current.consume(now, GateConstants.EMERGENCY_REFILL.inWholeMilliseconds)
            if (consumed != null) {
                settingsRepository.setEmergencyUnlocks(consumed)
                blockRepository.upsert(block.copy(
                    lockType = null,
                    blockedUntil = null,
                    lockPasswordHash = null,
                ))
                gateEngine.refreshAll()
            }
        }
    }

    fun refresh(): Job = viewModelScope.launch {
        gateEngine.refreshAll()
        val availability = runCatching { healthRepository.availability() }.getOrDefault(HcAvailability.UNAVAILABLE)
        val granted = runCatching { healthRepository.hasAllPermissions() }.getOrDefault(false)
        hcProblem.value = healthConnectProblem(blockRepository.current(), availability, granted)
    }
}
