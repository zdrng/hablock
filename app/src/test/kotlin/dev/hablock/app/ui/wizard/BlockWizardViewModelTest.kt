package dev.hablock.app.ui.wizard

import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.BlockSchedule
import dev.hablock.app.domain.model.Condition
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.model.InstalledApp
import dev.hablock.app.domain.model.Weekday
import dev.hablock.app.domain.repository.InstalledAppsRepository
import dev.hablock.app.domain.service.FakeBlockRepository
import dev.hablock.app.domain.service.FakeHealthRepository
import dev.hablock.app.domain.service.GateEngine
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlockWizardViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opening an existing block prefills its schedule`() {
        val schedule = BlockSchedule(
            weekdays = setOf(Weekday.MONDAY, Weekday.THURSDAY),
            startMinute = 18 * 60 + 7,
            endMinute = 23 * 60 + 41,
        )
        val existing = block(schedule = schedule)
        val viewModel = viewModel(FakeBlockRepository(listOf(existing)))

        viewModel.open(existing.id)

        val state = viewModel.uiState.value
        assertTrue(state.editing)
        assertTrue(state.scheduleEnabled)
        assertEquals(schedule.weekdays, state.scheduleWeekdays)
        assertEquals(schedule.startMinute, state.scheduleStartMinute)
        assertEquals(schedule.endMinute, state.scheduleEndMinute)
    }

    @Test
    fun `save persists one shared minute precise overnight window`() = runTest {
        val repository = FakeBlockRepository()
        val gateEngine = RecordingGateEngine()
        val viewModel = viewModel(repository, gateEngine)
        viewModel.open(null)
        completeRequiredFields(viewModel)
        viewModel.setScheduleEnabled(true)
        Weekday.entries.filterNot { it == Weekday.FRIDAY }.forEach(viewModel::toggleScheduleWeekday)
        viewModel.setScheduleStartMinute(22 * 60 + 17)
        viewModel.setScheduleEndMinute(6 * 60 + 3)

        viewModel.save("Fallback")

        val saved = repository.current().single()
        assertEquals(
            BlockSchedule(setOf(Weekday.FRIDAY), 22 * 60 + 17, 6 * 60 + 3),
            saved.schedule,
        )
        assertEquals(1, gateEngine.refreshCount)
    }

    @Test
    fun `a scheduled block requires at least one weekday`() = runTest {
        val repository = FakeBlockRepository()
        val gateEngine = RecordingGateEngine()
        val viewModel = viewModel(repository, gateEngine)
        viewModel.open(null)
        completeRequiredFields(viewModel)
        viewModel.setScheduleEnabled(true)
        Weekday.entries.forEach(viewModel::toggleScheduleWeekday)
        viewModel.setStep(2)

        assertFalse(viewModel.uiState.value.canAdvance)
        viewModel.save("Fallback")

        assertTrue(repository.current().isEmpty())
        assertEquals(0, gateEngine.refreshCount)
    }

    @Test
    fun `disabling a schedule saves an always active block`() = runTest {
        val repository = FakeBlockRepository(listOf(block(schedule = BlockSchedule(setOf(Weekday.SUNDAY), 1, 2))))
        val existing = repository.current().single()
        val viewModel = viewModel(repository)
        viewModel.open(existing.id)
        viewModel.setScheduleEnabled(false)

        viewModel.save("Fallback")

        assertNull(repository.current().single().schedule)
    }

    private fun viewModel(
        repository: FakeBlockRepository,
        gateEngine: RecordingGateEngine = RecordingGateEngine(),
    ) = BlockWizardViewModel(
        blockRepository = repository,
        installedAppsRepository = FakeInstalledAppsRepository,
        healthRepository = FakeHealthRepository(),
        gateEngine = gateEngine,
    )

    private fun completeRequiredFields(viewModel: BlockWizardViewModel) {
        viewModel.toggleApp(TEST_PACKAGE)
        val steps = viewModel.uiState.value.drafts.single { it.kind == ConditionKind.Steps }
        viewModel.toggleCondition(steps.id)
    }

    private fun block(schedule: BlockSchedule?) = Block(
        id = "existing",
        name = "Evening",
        blockedPackages = setOf(TEST_PACKAGE),
        blockedLabels = mapOf(TEST_PACKAGE to "Example"),
        conditions = listOf(Condition.Steps("steps", 10_000.0)),
        thresholdN = 1,
        incrementPct = 0.2f,
        schedule = schedule,
    )

    private object FakeInstalledAppsRepository : InstalledAppsRepository {
        override suspend fun launcherApps() = listOf(InstalledApp(TEST_PACKAGE, "Example"))
        override suspend fun homePackages(): Set<String> = emptySet()
    }

    private class RecordingGateEngine : GateEngine {
        override val states = MutableStateFlow<Map<String, GateState>>(emptyMap())
        var refreshCount = 0
            private set

        override suspend fun refreshAll() {
            refreshCount++
        }

        override suspend fun onAppForegrounded(packageName: String) = Unit
        override suspend fun onSessionExpired(blockId: String) = Unit
        override suspend fun onScheduleTransition() = Unit
        override suspend fun onDayReset() = Unit
        override suspend fun onDayBoundaryChanged() = Unit
        override suspend fun markEmergencyUnlockUsed(blockId: String) = Unit
        override suspend fun deleteBlock(blockId: String) = Unit
        override suspend fun unlock(blockId: String) = Unit
        override suspend fun relock(blockId: String) = Unit
    }

    private companion object {
        const val TEST_PACKAGE = "com.example.app"
    }
}
