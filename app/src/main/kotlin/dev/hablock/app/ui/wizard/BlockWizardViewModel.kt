package dev.hablock.app.ui.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.HcAvailability
import dev.hablock.app.domain.model.InstalledApp
import dev.hablock.app.domain.repository.BlockRepository
import dev.hablock.app.domain.repository.HealthRepository
import dev.hablock.app.domain.repository.InstalledAppsRepository
import dev.hablock.app.domain.service.GateEngine
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConditionKind(
    val label: String,
    val min: Int,
    val max: Int,
    val step: Int,
    val default: Int,
    val health: Boolean,
) {
    Steps("STEPS", 1_000, 30_000, 500, 10_000, true),
    Workout("WORKOUT", 5, 180, 5, 45, true),
    Meditation("MEDITATION", 5, 60, 5, 10, true),
    AppUsage("APP USAGE", 5, 180, 5, 40, false),
}

data class ConditionDraft(
    val kind: ConditionKind,
    val id: String = UUID.randomUUID().toString(),
    val selected: Boolean = false,
    val value: Int = kind.default,
    val packageName: String? = null,
    val appLabel: String? = null,
) {
    val ready: Boolean get() = selected && (kind != ConditionKind.AppUsage || packageName != null)
}

data class WizardUiState(
    val step: Int = 0,
    val loading: Boolean = true,
    val editing: Boolean = false,
    val apps: List<InstalledApp> = emptyList(),
    val query: String = "",
    val selectedPackages: Set<String> = emptySet(),
    val drafts: List<ConditionDraft> = emptyList(),
    val thresholdN: Int = 1,
    val incrementPct: Float = 0.20f,
    val name: String = "",
    val defaultBlockNumber: Int = 1,
    val availability: HcAvailability = HcAvailability.UNAVAILABLE,
) {
    val activeDrafts: List<ConditionDraft> get() = drafts.filter { it.ready }
    val conditionCount: Int get() = activeDrafts.size

    /** A selected draft stays visible even when its source is gone, so it can be dropped on sight. */
    val visibleDrafts: List<ConditionDraft>
        get() = drafts.filter { it.selected || it.kind.availableUnder(availability) }

    val filteredApps: List<InstalledApp>
        get() = if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    val canAdvance: Boolean
        get() = when (step) {
            0 -> selectedPackages.isNotEmpty()
            1 -> conditionCount > 0
            else -> true
        }

    fun unavailable(draft: ConditionDraft): Boolean = !draft.kind.availableUnder(availability)
}

class BlockWizardViewModel(
    private val blockRepository: BlockRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val healthRepository: HealthRepository,
    private val gateEngine: GateEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WizardUiState())
    val uiState: StateFlow<WizardUiState> = _uiState.asStateFlow()

    private val savedEvents = Channel<Unit>(Channel.CONFLATED)
    val saved: Flow<Unit> = savedEvents.receiveAsFlow()

    private var existing: Block? = null

    /** Called each time the sheet opens so a reused ViewModel starts from a clean draft. */
    fun open(blockId: String?) {
        savedEvents.tryReceive()
        _uiState.value = WizardUiState()
        viewModelScope.launch {
            val apps = runCatching { installedAppsRepository.launcherApps() }.getOrDefault(emptyList())
            val availability = runCatching { healthRepository.availability() }.getOrDefault(HcAvailability.UNAVAILABLE)
            val blocks = runCatching { blockRepository.current() }.getOrDefault(emptyList())
            existing = blocks.firstOrNull { it.id == blockId }
            _uiState.update { state ->
                val base = state.copy(
                    loading = false,
                    apps = apps.sortedBy { it.label.lowercase() },
                    availability = availability,
                    defaultBlockNumber = blocks.size + 1,
                    editing = existing != null,
                    drafts = defaultDrafts(),
                )
                existing?.let { block -> base.prefill(block) } ?: base
            }
        }
    }

    private fun WizardUiState.prefill(block: Block): WizardUiState {
        val drafts = defaultDrafts().map { draft ->
            val match = block.conditions.firstOrNull { it.matches(draft.kind) }
            when {
                match == null -> draft
                match is Condition.AppUsage -> draft.copy(
                    id = match.id,
                    selected = true,
                    value = match.goal.toInt(),
                    packageName = match.packageName,
                    appLabel = match.appLabel,
                )
                else -> draft.copy(id = match.id, selected = true, value = match.goal.toInt())
            }
        }
        return copy(
            selectedPackages = block.blockedPackages,
            drafts = drafts,
            thresholdN = block.thresholdN,
            incrementPct = block.incrementPct,
            name = block.name,
        )
    }

    fun setStep(step: Int) = _uiState.update { it.copy(step = step.coerceIn(0, 2)) }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun toggleApp(packageName: String) = _uiState.update { state ->
        val selected = if (packageName in state.selectedPackages) {
            state.selectedPackages - packageName
        } else {
            state.selectedPackages + packageName
        }
        val drafts = state.drafts.map { draft ->
            if (draft.kind == ConditionKind.AppUsage && draft.packageName in selected) {
                draft.copy(packageName = null, appLabel = null, selected = false)
            } else {
                draft
            }
        }
        state.copy(selectedPackages = selected, drafts = drafts).clampThreshold()
    }

    fun toggleCondition(id: String) = _uiState.update { state ->
        state.copy(drafts = state.drafts.map { if (it.id == id) it.copy(selected = !it.selected) else it })
            .clampThreshold()
    }

    fun bumpCondition(id: String, direction: Int) = _uiState.update { state ->
        state.copy(
            drafts = state.drafts.map { draft ->
                if (draft.id != id) {
                    draft
                } else {
                    val next = (draft.value + direction * draft.kind.step)
                        .coerceIn(draft.kind.min, draft.kind.max)
                    draft.copy(value = next)
                }
            },
        )
    }

    fun setHelperApp(app: InstalledApp) = _uiState.update { state ->
        state.copy(
            drafts = state.drafts.map { draft ->
                if (draft.kind == ConditionKind.AppUsage) {
                    draft.copy(packageName = app.packageName, appLabel = app.label, selected = true)
                } else {
                    draft
                }
            },
        ).clampThreshold()
    }

    fun bumpThreshold(direction: Int) = _uiState.update { state ->
        state.copy(thresholdN = state.thresholdN + direction).clampThreshold()
    }

    fun setIncrement(pct: Float) = _uiState.update {
        it.copy(incrementPct = pct.coerceIn(GateConstants.INCREMENT_MIN, GateConstants.INCREMENT_MAX))
    }

    fun bumpIncrement(direction: Int) = _uiState.update {
        val next = it.incrementPct + direction * GateConstants.INCREMENT_STEP
        it.copy(incrementPct = next.coerceIn(GateConstants.INCREMENT_MIN, GateConstants.INCREMENT_MAX))
    }

    fun setName(name: String) = _uiState.update { it.copy(name = name) }

    fun save(fallbackName: String) {
        val state = _uiState.value
        if (state.selectedPackages.isEmpty() || state.conditionCount == 0) return
        viewModelScope.launch {
            val labels = state.apps.associate { it.packageName to it.label }
            blockRepository.upsert(
                Block(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = state.name.ifBlank { fallbackName },
                    blockedPackages = state.selectedPackages,
                    blockedLabels = state.selectedPackages.associateWith {
                        labels[it] ?: it.substringAfterLast('.')
                    },
                    conditions = state.activeDrafts.map { it.toCondition() },
                    thresholdN = state.thresholdN.coerceIn(1, state.conditionCount),
                    incrementPct = state.incrementPct,
                    enabled = existing?.enabled ?: true,
                ),
            )
            gateEngine.refreshAll()
            savedEvents.send(Unit)
        }
    }
}

private fun defaultDrafts(): List<ConditionDraft> = ConditionKind.entries.map { ConditionDraft(kind = it) }

private fun ConditionKind.availableUnder(availability: HcAvailability): Boolean = when {
    !health -> true
    availability == HcAvailability.FULL -> true
    availability == HcAvailability.NO_MINDFULNESS -> this != ConditionKind.Meditation
    else -> false
}

private fun Condition.matches(kind: ConditionKind): Boolean = when (this) {
    is Condition.Steps -> kind == ConditionKind.Steps
    is Condition.Exercise -> kind == ConditionKind.Workout
    is Condition.Meditation -> kind == ConditionKind.Meditation
    is Condition.AppUsage -> kind == ConditionKind.AppUsage
}

private fun ConditionDraft.toCondition(): Condition = when (kind) {
    ConditionKind.Steps -> Condition.Steps(id, value.toDouble())
    ConditionKind.Workout -> Condition.Exercise(id, value.toDouble())
    ConditionKind.Meditation -> Condition.Meditation(id, value.toDouble())
    ConditionKind.AppUsage -> Condition.AppUsage(
        id = id,
        goal = value.toDouble(),
        packageName = requireNotNull(packageName),
        appLabel = appLabel.orEmpty(),
    )
}

private fun WizardUiState.clampThreshold(): WizardUiState =
    copy(thresholdN = thresholdN.coerceIn(1, conditionCount.coerceAtLeast(1)))
