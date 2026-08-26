package dev.hablock.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hablock.app.domain.model.Block
import dev.hablock.app.ui.components.BreathingGateBadge
import dev.hablock.app.ui.components.GroupPosition
import dev.hablock.app.ui.components.GroupedListItem
import dev.hablock.app.ui.components.HablockDialog
import dev.hablock.app.ui.components.HablockIcons
import dev.hablock.app.ui.format.formatWeekday
import dev.hablock.app.ui.gateViewModel
import dev.hablock.app.ui.wizard.BlockWizardSheet

@Composable
fun HomeScreen(addRequest: Int = 0, onAddHandled: () -> Unit = {}) {
    val viewModel = gateViewModel { container ->
        HomeViewModel(
            container.blockRepository,
            container.gateEngine,
            container.healthRepository,
            container.dayClock,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var wizardTarget by remember { mutableStateOf<WizardTarget?>(null) }
    var actionsFor by remember { mutableStateOf<Block?>(null) }
    var pendingDelete by remember { mutableStateOf<Block?>(null) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                title = { Text("Blocks") },
                subtitle = { Text("${formatWeekday(uiState.today)} · resets at midnight") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { insets ->
        if (uiState.blocks.isEmpty() && !uiState.loading) {
            EmptyState(Modifier.padding(insets), onAdd = { wizardTarget = WizardTarget.New })
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(top = insets.calculateTopPadding()),
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
                        expanded = single || item.block.id == expandedId,
                        onClick = {
                            if (single) {
                                wizardTarget = WizardTarget.Edit(item.block.id)
                            } else {
                                expandedId = if (expandedId == item.block.id) null else item.block.id
                            }
                        },
                        onToggle = { viewModel.setEnabled(item.block, it) },
                        onLongPress = { actionsFor = item.block },
                        modifier = Modifier.fillMaxWidth(),
                    )
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
        BlockActionsSheet(
            block = block,
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
            onDismiss = { actionsFor = null },
        )
    }

    pendingDelete?.let { block ->
        HablockDialog(
            title = "Remove ${block.name}?",
            message = "These apps open freely again. This can't be undone.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                viewModel.delete(block.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
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
                "Health Connect can't be read — step, workout and meditation goals count as zero.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = { healthLauncher.launch(permissions) }) { Text("Fix") }
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
            "Nothing's locked up yet.",
            style = MaterialTheme.typography.headlineLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Pick an app that keeps stealing your evenings, then decide what earns it back.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onAdd) { Text("Build your first block") }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun BlockActionsSheet(
    block: Block,
    onEdit: () -> Unit,
    onTogglePause: () -> Unit,
    onDelete: () -> Unit,
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
            GroupedListItem(
                position = GroupPosition.First,
                onClick = onEdit,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                title = "Edit",
                leading = { Icon(Icons.Rounded.Edit, contentDescription = null) },
            )
            GroupedListItem(
                position = GroupPosition.Middle,
                onClick = onTogglePause,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                title = if (block.enabled) "Pause" else "Resume",
                leading = { Icon(HablockIcons.Pause, contentDescription = null) },
            )
            GroupedListItem(
                position = GroupPosition.Last,
                onClick = onDelete,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.error,
                title = "Delete",
                leading = {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
            )
        }
    }
}
