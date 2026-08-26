package dev.hablock.app.ui.wizard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hablock.app.domain.model.InstalledApp
import dev.hablock.app.ui.components.AppIconCookie
import dev.hablock.app.ui.components.GroupedListItem
import dev.hablock.app.ui.components.PlayfulStepper
import dev.hablock.app.ui.components.StepShapeDots
import dev.hablock.app.ui.components.groupPositionOf
import dev.hablock.app.ui.format.formatPercent
import dev.hablock.app.ui.format.groupedInt
import dev.hablock.app.ui.gateViewModel

@Composable
fun BlockWizardSheet(blockId: String?, onDismiss: () -> Unit) {
    val viewModel = gateViewModel { container ->
        BlockWizardViewModel(
            container.blockRepository,
            container.installedAppsRepository,
            container.healthRepository,
            container.gateEngine,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(blockId) { viewModel.open(blockId) }
    LaunchedEffect(Unit) { viewModel.saved.collect { onDismiss() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .heightIn(min = 420.dp),
        ) {
            WizardHeader(
                step = uiState.step,
                editing = uiState.editing,
                onBack = {
                    if (uiState.step == 0) onDismiss() else viewModel.setStep(uiState.step - 1)
                },
            )
            AnimatedContent(
                targetState = uiState.step,
                transitionSpec = {
                    val forward = targetState > initialState
                    val direction = { size: Int -> if (forward) size else -size }
                    (slideInHorizontally(initialOffsetX = direction) + fadeIn()) togetherWith
                        (slideOutHorizontally(targetOffsetX = { -direction(it) }) + fadeOut())
                },
                label = "wizardStep",
            ) { step ->
                when (step) {
                    0 -> StepApps(uiState, viewModel)
                    1 -> StepConditions(uiState, viewModel)
                    else -> StepName(uiState, viewModel)
                }
            }
        }
    }
}

@Composable
private fun WizardHeader(step: Int, editing: Boolean, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                when {
                    step == 0 -> "What should we lock away?"
                    step == 1 -> "What opens it back up?"
                    editing -> "Check it over, then arm it."
                    else -> "Name it, then arm it."
                },
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        }
        StepShapeDots(
            step = step,
            count = 3,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun StepApps(uiState: WizardUiState, viewModel: BlockWizardViewModel) {
    Column(Modifier.padding(top = 10.dp)) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            shape = CircleShape,
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.weight(1f, fill = false).heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val apps = uiState.filteredApps
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                val selected = app.packageName in uiState.selectedPackages
                GroupedListItem(
                    position = groupPositionOf(index, apps.size),
                    onClick = { viewModel.toggleApp(app.packageName) },
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    leading = { AppIconCookie(app.packageName, app.label) },
                    title = app.label,
                    trailing = {
                        if (selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
        WizardFooter(
            hint = if (uiState.selectedPackages.isEmpty()) {
                "Pick at least one app"
            } else {
                "${uiState.selectedPackages.size} picked"
            },
            label = "Next",
            enabled = uiState.canAdvance,
            onClick = { viewModel.setStep(1) },
        )
    }
}

@Composable
private fun StepConditions(uiState: WizardUiState, viewModel: BlockWizardViewModel) {
    Column(Modifier.padding(top = 10.dp)) {
        LazyColumn(
            Modifier.weight(1f, fill = false).heightIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(uiState.visibleDrafts, key = { _, draft -> draft.id }) { _, draft ->
                ConditionCard(draft, uiState, viewModel)
            }
            item { ThresholdPanel(uiState, viewModel) }
            item { RatchetPanel(uiState, viewModel) }
        }
        WizardFooter(
            hint = if (uiState.conditionCount == 0) "Switch on at least one condition" else "",
            label = "Next",
            enabled = uiState.canAdvance,
            onClick = { viewModel.setStep(2) },
        )
    }
}

@Composable
private fun ConditionCard(draft: ConditionDraft, uiState: WizardUiState, viewModel: BlockWizardViewModel) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (draft.selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(draft.kind.friendlyName, style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        draft.kind.friendlyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = draft.selected, onCheckedChange = { viewModel.toggleCondition(draft.id) })
            }
            if (draft.selected) {
                PlayfulStepper(
                    value = if (draft.kind == ConditionKind.Steps) groupedInt(draft.value.toDouble()) else "${draft.value}",
                    onBump = { viewModel.bumpCondition(draft.id, it) },
                    canDecrease = draft.value > draft.kind.min,
                    canIncrease = draft.value < draft.kind.max,
                )
                Text(
                    draft.kind.goalUnit,
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (draft.kind == ConditionKind.AppUsage) {
                    HelperAppPicker(draft, uiState, viewModel)
                }
            }
        }
    }
}

@Composable
private fun HelperAppPicker(draft: ConditionDraft, uiState: WizardUiState, viewModel: BlockWizardViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (draft.packageName == null) "Which app earns the others?" else "Earning app",
            style = MaterialTheme.typography.labelLarge,
        )
        val candidates = uiState.apps.filter { it.packageName !in uiState.selectedPackages }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(candidates, key = { _, app -> app.packageName }) { _, app ->
                HelperCandidate(app, selected = app.packageName == draft.packageName) {
                    viewModel.setHelperApp(app)
                }
            }
        }
    }
}

@Composable
private fun HelperCandidate(app: InstalledApp, selected: Boolean, onPick: () -> Unit) {
    Surface(
        onClick = onPick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(10.dp).size(width = 64.dp, height = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppIconCookie(app.packageName, app.label, size = 30.dp)
            Text(
                app.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ThresholdPanel(uiState: WizardUiState, viewModel: BlockWizardViewModel) {
    val count = uiState.conditionCount.coerceAtLeast(1)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                buildAnnotatedString {
                    append("Open when ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                        append("${uiState.thresholdN} of $count")
                    }
                    append(" land.")
                },
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            if (count > 1) {
                Slider(
                    value = uiState.thresholdN.toFloat(),
                    onValueChange = { viewModel.bumpThreshold(it.toInt() - uiState.thresholdN) },
                    valueRange = 1f..count.toFloat(),
                    steps = (count - 2).coerceAtLeast(0),
                )
            }
        }
    }
}

@Composable
private fun RatchetPanel(uiState: WizardUiState, viewModel: BlockWizardViewModel) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                buildAnnotatedString {
                    append("Each reopen raises every goal by ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                        append(formatPercent(uiState.incrementPct))
                    }
                    append(".")
                },
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Slider(
                value = uiState.incrementPct,
                onValueChange = viewModel::setIncrement,
                valueRange = 0.10f..0.50f,
                steps = 7,
            )
        }
    }
}

@Composable
private fun StepName(uiState: WizardUiState, viewModel: BlockWizardViewModel) {
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::setName,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(uiState.defaultName) },
            label = { Text("Name") },
            trailingIcon = {
                IconButton(onClick = { viewModel.setName(suggestName(uiState.name)) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Suggest a name")
                }
            },
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
        )
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryRow("Locked apps", "${uiState.selectedPackages.size}")
                SummaryRow("Conditions", "${uiState.conditionCount}")
                SummaryRow("Opens on", "${uiState.thresholdN} of ${uiState.conditionCount}")
                SummaryRow("Ratchet", formatPercent(uiState.incrementPct))
            }
        }
        Box(contentAlignment = Alignment.Center) {
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (uiState.editing) "Save changes" else "Lock it in") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmallEmphasized)
    }
}

@Composable
private fun WizardFooter(hint: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onClick, enabled = enabled) { Text(label) }
    }
}

private val nameIdeas = listOf(
    "Doomscroll", "Rabbit hole", "The time sink", "Sugar jar", "Night owl",
    "Bottomless feed", "The vortex", "Candy shelf", "Snooze trap",
)

private fun suggestName(current: String): String {
    val next = nameIdeas.filterNot { it == current }
    return next.random()
}

private val ConditionKind.friendlyName: String
    get() = when (this) {
        ConditionKind.Steps -> "Steps"
        ConditionKind.Workout -> "Workout"
        ConditionKind.Meditation -> "Meditation"
        ConditionKind.AppUsage -> "Time in a good app"
    }

private val ConditionKind.friendlyHint: String
    get() = when (this) {
        ConditionKind.Steps -> "Move first, scroll later"
        ConditionKind.Workout -> "Minutes of exercise, via Health Connect"
        ConditionKind.Meditation -> "Minutes of mindfulness, via Health Connect"
        ConditionKind.AppUsage -> "Minutes in an app that earns its keep"
    }

private val ConditionKind.goalUnit: String
    get() = when (this) {
        ConditionKind.Steps -> "steps"
        else -> "minutes"
    }
