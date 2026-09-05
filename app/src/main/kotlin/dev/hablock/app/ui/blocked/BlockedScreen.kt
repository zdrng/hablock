package dev.hablock.app.ui.blocked

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hablock.app.R
import dev.hablock.app.domain.model.ConditionProgress
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.ui.components.AppIconCookie
import dev.hablock.app.ui.components.ConditionMeter
import dev.hablock.app.ui.components.GroupedListItem
import dev.hablock.app.ui.components.HablockDialog
import dev.hablock.app.ui.components.ShapeBurst
import dev.hablock.app.ui.components.ShapeShiftingBadge
import dev.hablock.app.ui.components.groupPositionOf
import dev.hablock.app.ui.gateViewModel
import kotlinx.coroutines.launch

@Composable
fun BlockedScreen(
    blockId: String,
    packageName: String?,
    onClose: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val viewModel = gateViewModel(key = blockId) { container ->
        BlockedViewModel(
            container.blockRepository,
            container.gateEngine,
            container.healthRepository,
            blockId,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appLabel = packageName?.let { uiState.block?.blockedLabels?.get(it) ?: it.substringAfterLast('.') }
        ?: uiState.block?.name.orEmpty()
    val open = uiState.state is GateState.Open
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshHcStatus()
        onPauseOrDispose {}
    }
    LaunchedEffect(uiState.state) {
        if (uiState.state is GateState.SessionActive) {
            packageName?.let(onOpenApp)
            onClose()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp),
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    scope.launch {
                        viewModel.refresh().join()
                        isRefreshing = false
                    }
                },
                modifier = Modifier.weight(0.6f),
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(28.dp))
                    if (packageName != null) {
                        AppIconCookie(packageName, appLabel, size = 56.dp, contentDescription = appLabel)
                        Spacer(Modifier.height(14.dp))
                    }
                    Text(
                        stringResource(
                            if (open) R.string.blocked_headline_open else R.string.blocked_headline_locked,
                            appLabel,
                        ),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    Box(contentAlignment = Alignment.Center) {
                        ShapeShiftingBadge(
                            modifier = Modifier.size(170.dp),
                            color = if (open) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                        ) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(88.dp),
                                tint = if (open) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                            )
                        }
                        ShapeBurst(
                            trigger = if (open) "open" else null,
                            modifier = Modifier.size(280.dp),
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    ConditionGroup(uiState.progress)
                    if (uiState.hcProblem) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.blocked_hc_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
            Column(
                Modifier.weight(0.4f).fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (open) {
                    Button(
                        onClick = { showUnlockDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            stringResource(R.string.blocked_unlock_button, uiState.sessionMinutes),
                        )
                    }
                } else {
                    val met = uiState.state?.metCount ?: 0
                    Text(
                        stringResource(R.string.blocked_locked_hint, uiState.threshold - met, uiState.threshold),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    if (showUnlockDialog) {
        HablockDialog(
            title = stringResource(R.string.blocked_unlock_title, appLabel),
            message = stringResource(R.string.blocked_unlock_message, uiState.sessionMinutes),
            confirmLabel = stringResource(R.string.blocked_unlock_confirm),
            onConfirm = {
                showUnlockDialog = false
                viewModel.unlock()
            },
            onDismiss = { showUnlockDialog = false },
        )
    }
    BackHandler { onClose() }
}

@Composable
private fun ConditionGroup(progress: List<ConditionProgress>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        progress.forEachIndexed { index, item ->
            GroupedListItem(position = groupPositionOf(index, progress.size)) {
                ConditionMeter(item.condition, item.current, item.required)
            }
        }
    }
}
