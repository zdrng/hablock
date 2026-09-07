package dev.hablock.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hablock.app.R
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.LockType
import dev.hablock.app.domain.model.isChangesLocked
import dev.hablock.app.ui.components.BreathingGateBadge
import dev.hablock.app.ui.components.EmergencyUnlockControl
import dev.hablock.app.ui.components.EmergencyUnlockPills
import dev.hablock.app.ui.components.GroupPosition
import dev.hablock.app.ui.components.GroupedListItem
import dev.hablock.app.ui.components.HablockDialog
import dev.hablock.app.ui.components.HablockIcons
import dev.hablock.app.ui.components.PasscodeInput
import dev.hablock.app.ui.components.PlayfulStepper
import dev.hablock.app.ui.components.StepShapeDots
import dev.hablock.app.ui.format.formatDate
import dev.hablock.app.ui.format.formatWeekday
import dev.hablock.app.ui.gateViewModel
import dev.hablock.app.ui.wizard.BlockWizardSheet
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun HomeScreen(addRequest: Int = 0, onAddHandled: () -> Unit = {}) {
    val viewModel = gateViewModel { container ->
        HomeViewModel(
            container.blockRepository,
            container.gateEngine,
            container.healthRepository,
            container.settingsRepository,
            container.dayClock,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var wizardTarget by remember { mutableStateOf<WizardTarget?>(null) }
    var actionsFor by remember { mutableStateOf<Block?>(null) }
    var pendingDelete by remember { mutableStateOf<Block?>(null) }
    var lockTarget by remember { mutableStateOf<Block?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    LaunchedEffect(addRequest) {
        if (addRequest > 0) {
            wizardTarget = WizardTarget.New
            onAddHandled()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                subtitle = { Text(stringResource(R.string.home_subtitle, formatWeekday(uiState.today))) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { insets ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    viewModel.refresh().join()
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(top = insets.calculateTopPadding()),
        ) {
            if (uiState.blocks.isEmpty() && !uiState.loading) {
                EmptyState(onAdd = { wizardTarget = WizardTarget.New })
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.hcProblem) {
                        item(key = "hc-problem") {
                            HealthConnectBanner(
                                permissions = viewModel.healthPermissions,
                                onGranted = { viewModel.refresh() },
                            )
                        }
                    }
                    val single = uiState.blocks.size == 1
                    items(uiState.blocks, key = { it.block.id }) { item ->
                        BlockCard(
                            block = item.block,
                            state = item.state,
                            expanded = single,
                            onToggle = { viewModel.setEnabled(item.block, it) },
                            onLongPress = { actionsFor = item.block },
                            onRelock = { viewModel.relock(item.block.id) },
                            onLockChanges = { lockTarget = item.block },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    wizardTarget?.let { target ->
        BlockWizardSheet(
            blockId = (target as? WizardTarget.Edit)?.blockId,
            onDismiss = { wizardTarget = null },
        )
    }

    actionsFor?.let { block ->
        val changesLocked = block.isChangesLocked()
        BlockActionsSheet(
            block = block,
            changesLocked = changesLocked,
            onEdit = {
                actionsFor = null
                wizardTarget = WizardTarget.Edit(block.id)
            },
            onTogglePause = {
                viewModel.setEnabled(block, !block.enabled)
                actionsFor = null
            },
            onDelete = {
                actionsFor = null
                pendingDelete = block
            },
            onUnlockChanges = {
                actionsFor = null
                lockTarget = block
            },
            onDismiss = { actionsFor = null },
        )
    }

    pendingDelete?.let { block ->
        HablockDialog(
            title = stringResource(R.string.home_delete_title, block.name),
            message = stringResource(R.string.home_delete_message),
            confirmLabel = stringResource(R.string.home_delete_confirm),
            destructive = true,
            onConfirm = {
                viewModel.delete(block.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    lockTarget?.let { block ->
        val changesLocked = block.isChangesLocked()
        if (changesLocked) {
            when (block.lockType) {
                LockType.PASSWORD -> PasswordUnlockDialog(
                    block = block,
                    emergencyUnlocks = uiState.emergencyUnlocks,
                    onUnlock = { passcode ->
                        if (viewModel.unlockWithPassword(block, passcode)) {
                            lockTarget = null
                        }
                    },
                    onEmergencyUnlock = {
                        viewModel.emergencyUnlock(block)
                        lockTarget = null
                    },
                    onDismiss = { lockTarget = null },
                )
                else -> EmergencyUnlockDialog(
                    block = block,
                    emergencyUnlocks = uiState.emergencyUnlocks,
                    onUnlock = {
                        viewModel.emergencyUnlock(block)
                        lockTarget = null
                    },
                    onDismiss = { lockTarget = null },
                )
            }
        } else {
            LockChangesSheet(
                block = block,
                onLockDuration = { untilMillis ->
                    viewModel.lockChangesDuration(block, untilMillis)
                    lockTarget = null
                },
                onLockPassword = { passcode ->
                    viewModel.lockChangesPassword(block, passcode)
                    lockTarget = null
                },
                onDismiss = { lockTarget = null },
            )
        }
    }
}

sealed interface WizardTarget {
    data object New : WizardTarget
    data class Edit(val blockId: String) : WizardTarget
}

@Composable
private fun HealthConnectBanner(permissions: Set<String>, onGranted: () -> Unit) {
    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { onGranted() }
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.home_hc_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = { healthLauncher.launch(permissions) }) { Text(stringResource(R.string.home_hc_fix)) }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BreathingGateBadge(Modifier.size(180.dp), color = MaterialTheme.colorScheme.primaryContainer)
        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onAdd) { Text(stringResource(R.string.home_empty_cta)) }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun BlockActionsSheet(
    block: Block,
    changesLocked: Boolean,
    onEdit: () -> Unit,
    onTogglePause: () -> Unit,
    onDelete: () -> Unit,
    onUnlockChanges: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                block.name,
                Modifier.padding(start = 18.dp, bottom = 14.dp),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            if (changesLocked) {
                GroupedListItem(
                    position = GroupPosition.First,
                    onClick = onUnlockChanges,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    title = stringResource(R.string.home_card_unlock_changes),
                    leading = { Icon(HablockIcons.LockOpen, contentDescription = null) },
                )
                GroupedListItem(
                    position = GroupPosition.Last,
                    onClick = null,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    title = stringResource(R.string.home_action_edit),
                    leading = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                )
            } else {
                GroupedListItem(
                    position = GroupPosition.First,
                    onClick = onEdit,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    title = stringResource(R.string.home_action_edit),
                    leading = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                )
                GroupedListItem(
                    position = GroupPosition.Middle,
                    onClick = onTogglePause,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    title = stringResource(if (block.enabled) R.string.home_action_pause else R.string.home_action_resume),
                    leading = { Icon(HablockIcons.Pause, contentDescription = null) },
                )
                GroupedListItem(
                    position = GroupPosition.Last,
                    onClick = onDelete,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.error,
                    title = stringResource(R.string.home_action_delete),
                    leading = {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                )
            }
        }
    }
}

private enum class LockStep(val titleRes: Int) {
    Method(R.string.changes_lock_step_method),
    Password(R.string.changes_lock_step_password),
    Custom(R.string.changes_lock_step_custom),
}

@Composable
private fun LockChangesSheet(
    block: Block,
    onLockDuration: (Long) -> Unit,
    onLockPassword: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var step by remember { mutableStateOf(LockStep.Method) }
    var selectedPreset by remember { mutableIntStateOf(0) }
    var customDays by remember { mutableStateOf("") }
    var passcode by remember { mutableStateOf("") }

    val durationPresets = listOf(
        1 to R.string.changes_lock_preset_1d,
        3 to R.string.changes_lock_preset_3d,
        7 to R.string.changes_lock_preset_1w,
        30 to R.string.changes_lock_preset_1m,
        365 to R.string.changes_lock_preset_1y,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step != LockStep.Method) {
                    IconButton(
                        onClick = {
                            step = LockStep.Method
                            passcode = ""
                            customDays = ""
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.wizard_back))
                    }
                }
                Text(
                    stringResource(R.string.changes_lock_title, block.name),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
            }
            val stepCount = when (selectedPreset) {
                durationPresets.size, durationPresets.size + 1 -> 2
                else -> 1
            }
            StepShapeDots(
                step = step.ordinal,
                count = stepCount,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val direction: (Int) -> Int = { if (forward) it else -it }
                    (slideInHorizontally(initialOffsetX = { direction(it) }) + fadeIn()) togetherWith
                        (slideOutHorizontally(targetOffsetX = { -direction(it) }) + fadeOut())
                },
                label = "lockStep",
            ) { currentStep ->
                when (currentStep) {
                    LockStep.Method -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.changes_lock_message), style = MaterialTheme.typography.bodyMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            durationPresets.chunked(2).forEachIndexed { rowIndex, rowPresets ->
                                Row(
                                    Modifier.fillMaxWidth().height(44.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    rowPresets.forEachIndexed { colIndex, (_, label) ->
                                        val flatIndex = rowIndex * 2 + colIndex
                                        val selected = selectedPreset == flatIndex
                                        Surface(
                                            onClick = { selectedPreset = flatIndex },
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            shape = MaterialTheme.shapes.medium,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        ) {
                                            Row(
                                                Modifier.fillMaxSize(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    stringResource(label),
                                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().height(44.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val passwordSelected = selectedPreset == durationPresets.size
                                Surface(
                                    onClick = { selectedPreset = durationPresets.size },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (passwordSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = if (passwordSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                ) {
                                    Row(
                                        Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(HablockIcons.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text(
                                            stringResource(R.string.changes_lock_preset_password),
                                            style = MaterialTheme.typography.titleMediumEmphasized,
                                        )
                                    }
                                }
                                val customSelected = selectedPreset == durationPresets.size + 1
                                Surface(
                                    onClick = { selectedPreset = durationPresets.size + 1 },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (customSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = if (customSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                ) {
                                    Row(
                                        Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            stringResource(R.string.changes_lock_preset_custom),
                                            style = MaterialTheme.typography.titleMediumEmphasized,
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            when (selectedPreset) {
                                durationPresets.size -> Button(onClick = { step = LockStep.Password }) {
                                    Text(stringResource(R.string.wizard_next))
                                }
                                durationPresets.size + 1 -> Button(onClick = { step = LockStep.Custom }) {
                                    Text(stringResource(R.string.wizard_next))
                                }
                                else -> Button(onClick = {
                                    onLockDuration(System.currentTimeMillis() + durationPresets[selectedPreset].first * 86_400_000L)
                                }) {
                                    Text(stringResource(R.string.changes_lock_confirm))
                                }
                            }
                        }
                    }
                    LockStep.Password -> Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.changes_lock_set_password_message),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        PasscodeInput(
                            value = passcode,
                            onValueChange = { passcode = it },
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Button(
                                onClick = { onLockPassword(passcode) },
                                enabled = passcode.length == 6,
                            ) {
                                Text(stringResource(R.string.changes_lock_confirm))
                            }
                        }
                    }
                    LockStep.Custom -> Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.changes_lock_custom_message),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        PlayfulStepper(
                            value = customDays.ifBlank { "1" },
                            onBump = { direction ->
                                val current = customDays.toIntOrNull()?.takeIf { it > 0 } ?: 1
                                customDays = (current + direction).coerceAtLeast(1).toString()
                            },
                        )
                        Text(
                            stringResource(R.string.changes_lock_custom_days_unit),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Button(
                                onClick = {
                                    val days = customDays.toIntOrNull()?.takeIf { it > 0 } ?: 1
                                    onLockDuration(System.currentTimeMillis() + days * 86_400_000L)
                                },
                            ) {
                                Text(stringResource(R.string.changes_lock_confirm))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordUnlockDialog(
    block: Block,
    emergencyUnlocks: dev.hablock.app.domain.model.EmergencyUnlockState,
    onUnlock: (String) -> Unit,
    onEmergencyUnlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showEmergency by remember { mutableStateOf(false) }
    if (showEmergency) {
        EmergencyUnlockDialog(block, emergencyUnlocks, onEmergencyUnlock) { showEmergency = false }
        return
    }
    var passcode by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.changes_lock_enter_password_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.changes_lock_enter_password_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasscodeInput(
                    value = passcode,
                    onValueChange = {
                        passcode = it
                        isError = false
                    },
                    isError = isError,
                )
                if (isError) {
                    Text(
                        stringResource(R.string.changes_lock_wrong_password),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                HorizontalDivider()
                Text(
                    stringResource(
                        R.string.emergency_unlock_available,
                        emergencyUnlocks.availableCount,
                        dev.hablock.app.domain.GateConstants.EMERGENCY_UNLOCKS_MAX,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showEmergency = true }) {
                    Text(stringResource(R.string.emergency_unlock_hold))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (passcode.length == 6) {
                        onUnlock(passcode)
                        isError = true
                    }
                },
                enabled = passcode.length == 6,
            ) {
                Text(stringResource(R.string.blocked_unlock_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun EmergencyUnlockDialog(
    block: Block,
    emergencyUnlocks: dev.hablock.app.domain.model.EmergencyUnlockState,
    onUnlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.emergency_screen_title),
                    style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.emergency_screen_body, block.name),
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                EmergencyUnlockPills(pills = emergencyUnlocks.pills)
                Text(stringResource(R.string.emergency_unlock_available,
                    emergencyUnlocks.availableCount, dev.hablock.app.domain.GateConstants.EMERGENCY_UNLOCKS_MAX),
                    style = MaterialTheme.typography.labelLarge)
                if (emergencyUnlocks.availableCount > 0) {
                    EmergencyUnlockControl(onUnlock)
                } else {
                    Text(stringResource(R.string.emergency_unlock_none), textAlign = TextAlign.Center)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}
