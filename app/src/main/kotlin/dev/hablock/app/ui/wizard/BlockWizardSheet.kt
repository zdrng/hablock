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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hablock.app.R
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.model.InstalledApp
import dev.hablock.app.domain.model.LockType
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
private fun WizardHeader(
    step: Int,
    editing: Boolean,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.wizard_back))
            }
            Text(
                when {
                    step == 0 -> stringResource(R.string.wizard_title_apps)
                    step == 1 -> stringResource(R.string.wizard_title_conditions)
                    editing -> stringResource(R.string.wizard_title_review)
                    else -> stringResource(R.string.wizard_title_name)
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
            placeholder = { Text(stringResource(R.string.wizard_search_apps)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            shape = CircleShape,
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.weight(1f, fill = false).heightIn(max = 420.dp),
            // Let the sheet consume edge drags without a competing stretch animation.
            overscrollEffect = null,
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
                stringResource(R.string.wizard_pick_hint)
            } else {
                pluralStringResource(R.plurals.wizard_picked_count, uiState.selectedPackages.size, uiState.selectedPackages.size)
            },
            label = stringResource(R.string.wizard_next),
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
            overscrollEffect = null,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(uiState.visibleDrafts, key = { _, draft -> draft.id }) { _, draft ->
                ConditionCard(draft, uiState, viewModel)
            }
            item { ThresholdPanel(uiState, viewModel) }
            item { RatchetPanel(uiState, viewModel) }
            item { UnlockPanel(uiState, viewModel) }
        }
        WizardFooter(
            hint = if (uiState.conditionCount == 0) stringResource(R.string.wizard_conditions_hint) else "",
            label = stringResource(R.string.wizard_next),
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
                    Text(draft.kind.friendlyName(), style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(
                        draft.kind.friendlyHint(),
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
                    draft.kind.goalUnit(),
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
            if (draft.packageName == null) {
                stringResource(R.string.wizard_helper_prompt)
            } else {
                stringResource(R.string.wizard_helper_label)
            },
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
                    append(stringResource(R.string.wizard_threshold_prefix))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                        append(stringResource(R.string.wizard_n_of_m, uiState.thresholdN, count))
                    }
                    append(stringResource(R.string.wizard_threshold_suffix))
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
                    append(stringResource(R.string.wizard_ratchet_prefix))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                        append(formatPercent(uiState.incrementPct))
                    }
                    append(stringResource(R.string.wizard_ratchet_suffix))
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
private fun UnlockPanel(uiState: WizardUiState, viewModel: BlockWizardViewModel) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.wizard_unlock_prefix))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                        append(pluralStringResource(R.plurals.wizard_unlock_minutes, uiState.unlockDurationMinutes, uiState.unlockDurationMinutes))
                    }
                    append(stringResource(R.string.wizard_unlock_suffix))
                },
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            PlayfulStepper(
                value = "${uiState.unlockDurationMinutes}",
                onBump = viewModel::bumpDuration,
                canDecrease = uiState.unlockDurationMinutes > GateConstants.DURATION_MIN,
                canIncrease = uiState.unlockDurationMinutes < GateConstants.DURATION_MAX,
            )
            Text(
                stringResource(R.string.wizard_unit_minutes),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StepName(uiState: WizardUiState, viewModel: BlockWizardViewModel) {
    val defaultName = stringResource(R.string.wizard_default_name, uiState.defaultBlockNumber)
    val nameIdeas = stringArrayResource(R.array.wizard_name_ideas)
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::setName,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(defaultName) },
            label = { Text(stringResource(R.string.wizard_name_label)) },
            trailingIcon = {
                IconButton(onClick = { viewModel.setName(suggestName(nameIdeas, uiState.name)) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.wizard_suggest_name))
                }
            },
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
        )
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryRow(stringResource(R.string.wizard_summary_locked_apps), "${uiState.selectedPackages.size}")
                SummaryRow(stringResource(R.string.wizard_summary_conditions), "${uiState.conditionCount}")
                SummaryRow(
                    stringResource(R.string.wizard_summary_opens_on),
                    stringResource(R.string.wizard_n_of_m, uiState.thresholdN, uiState.conditionCount),
                )
                SummaryRow(stringResource(R.string.wizard_summary_ratchet), formatPercent(uiState.incrementPct))
                SummaryRow(
                    stringResource(R.string.wizard_summary_unlock_duration),
                    pluralStringResource(R.plurals.wizard_unlock_minutes, uiState.unlockDurationMinutes, uiState.unlockDurationMinutes),
                )
                if (uiState.editing && uiState.lockType != null) {
                    SummaryRow(
                        stringResource(R.string.wizard_summary_changes_lock),
                        when (uiState.lockType) {
                            LockType.PASSWORD -> stringResource(R.string.changes_lock_preset_password)
                            LockType.DURATION -> stringResource(R.string.wizard_summary_changes_locked)
                        },
                    )
                }
            }
        }
        Box(contentAlignment = Alignment.Center) {
            Button(
                onClick = { viewModel.save(defaultName) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(if (uiState.editing) R.string.wizard_save_changes else R.string.wizard_lock_it_in)) }
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

private fun suggestName(ideas: Array<String>, current: String): String {
    val next = ideas.filterNot { it == current }
    return next.random()
}

@Composable
private fun ConditionKind.friendlyName(): String = when (this) {
    ConditionKind.Steps -> stringResource(R.string.condition_steps)
    ConditionKind.Workout -> stringResource(R.string.condition_workout)
    ConditionKind.Meditation -> stringResource(R.string.condition_meditation)
    ConditionKind.AppUsage -> stringResource(R.string.wizard_condition_app_usage)
}

@Composable
private fun ConditionKind.friendlyHint(): String = when (this) {
    ConditionKind.Steps -> stringResource(R.string.wizard_hint_steps)
    ConditionKind.Workout -> stringResource(R.string.wizard_hint_workout)
    ConditionKind.Meditation -> stringResource(R.string.wizard_hint_meditation)
    ConditionKind.AppUsage -> stringResource(R.string.wizard_hint_app_usage)
}

@Composable
private fun ConditionKind.goalUnit(): String = when (this) {
    ConditionKind.Steps -> stringResource(R.string.wizard_unit_steps)
    else -> stringResource(R.string.wizard_unit_minutes)
}
