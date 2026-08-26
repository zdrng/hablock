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
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hablock.app.domain.model.ConditionProgress
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.ui.components.AppIconCookie
import dev.hablock.app.ui.components.ConditionMeter
import dev.hablock.app.ui.components.GroupedListItem
import dev.hablock.app.ui.components.ShapeBurst
import dev.hablock.app.ui.components.ShapeShiftingBadge
import dev.hablock.app.ui.components.groupPositionOf
import dev.hablock.app.ui.gateViewModel
import kotlinx.coroutines.delay

/**
 * The screen that decides whether Hablock feels supportive or smug. Rule: specific and
 * actionable, matter-of-fact about the lock, never scolding.
 */
@Composable
fun BlockedScreen(
    blockId: String,
    packageName: String?,
    onClose: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val viewModel = gateViewModel(key = blockId) { container ->
        BlockedViewModel(container.blockRepository, container.gateEngine, blockId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appLabel = packageName?.let { uiState.block?.blockedLabels?.get(it) ?: it.substringAfterLast('.') }
        ?: uiState.block?.name.orEmpty()
    val open = uiState.state is GateState.Open
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.state) {
        if (uiState.state is GateState.SessionActive) onClose()
    }
    LaunchedEffect(checking) {
        if (checking) {
            viewModel.refresh()
            delay(750)
            checking = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(28.dp))
                if (packageName != null) {
                    AppIconCookie(packageName, appLabel, size = 56.dp)
                    Spacer(Modifier.height(14.dp))
                }
                Text(
                    if (open) "$appLabel is yours to open." else "$appLabel is locked.",
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
                        Text("🔒", fontSize = 56.sp)
                    }
                    ShapeBurst(
                        trigger = if (open) "open" else null,
                        modifier = Modifier.size(280.dp),
                    )
                }
                Spacer(Modifier.height(28.dp))
                ConditionGroup(uiState.progress)
                Spacer(Modifier.height(20.dp))
            }
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (open) {
                    Button(
                        onClick = { packageName?.let(onOpenApp) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open $appLabel — 30 minutes") }
                } else {
                    Button(
                        onClick = { checking = true },
                        enabled = !checking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (checking) {
                            ContainedLoadingIndicator(Modifier.size(28.dp))
                        } else {
                            Text("Check again")
                        }
                    }
                }
            }
        }
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
