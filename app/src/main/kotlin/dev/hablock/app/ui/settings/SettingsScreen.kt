package dev.hablock.app.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hablock.app.BuildConfig
import dev.hablock.app.R
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.archive.ArchiveImportPreview
import dev.hablock.app.domain.model.DiagnosticRun
import dev.hablock.app.domain.model.EnforcementBackendType
import dev.hablock.app.domain.model.EnforcementDiagnostics
import dev.hablock.app.domain.model.HcAvailability
import dev.hablock.app.domain.model.OverlapPolicy
import dev.hablock.app.domain.model.RelinquishState
import dev.hablock.app.domain.service.BehaviorSettings
import dev.hablock.app.ui.components.BigNumerals
import dev.hablock.app.ui.components.EmergencyUnlockPills
import dev.hablock.app.ui.components.GrantBadge
import dev.hablock.app.ui.components.GroupPosition
import dev.hablock.app.ui.components.GroupedListItem
import dev.hablock.app.ui.components.HablockDialog
import dev.hablock.app.ui.components.SectionHeader
import dev.hablock.app.ui.components.StatusChip
import dev.hablock.app.ui.components.rememberTicker
import dev.hablock.app.ui.format.formatCountdown
import dev.hablock.app.ui.gateViewModel
import dev.hablock.app.ui.system.openAccessibilitySettings
import dev.hablock.app.ui.system.openExactAlarmSettings
import dev.hablock.app.ui.system.openUsageAccessSettings
import dev.hablock.app.ui.theme.LocalHablockAccents
import dev.hablock.app.ui.theme.numeralStyle
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private enum class RelinquishDialog { Start, Confirm }

private data class LanguageOption(val tag: String, val displayName: String)

private val LANGUAGE_OPTIONS = listOf(
    LanguageOption("", "System default"),
    LanguageOption("en", "English"),
    LanguageOption("de", "Deutsch"),
    LanguageOption("fr", "Français"),
    LanguageOption("da", "Dansk"),
    LanguageOption("nb", "Norsk"),
    LanguageOption("nl", "Nederlands"),
)

@Composable
fun SettingsScreen() {
    val viewModel = gateViewModel { container ->
        SettingsViewModel(
            container.permissionChecker,
            container.deviceOwnerController,
            container.relinquishTimer,
            container.healthRepository,
            container.settingsRepository,
            container.behaviorSettingsService,
            container.diagnosticsService,
            container.archiveService,
            container::refreshAfterArchiveImport,
        )
    }
    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val relinquish by viewModel.relinquish.collectAsStateWithLifecycle()
    val emergencyUnlocks by viewModel.emergencyUnlocks.collectAsStateWithLifecycle()
    val behaviorSettings by viewModel.behaviorSettings.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val archiveState by viewModel.archiveState.collectAsStateWithLifecycle()
    val versionClick = rememberVersionClick(viewModel.settingsRepository)
    val context = LocalContext.current
    var guideOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<RelinquishDialog?>(null) }
    var languageDialogOpen by remember { mutableStateOf(false) }
    var timePickerOpen by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            viewModel.exportArchive { json -> context.writeUtf8(it, json) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            viewModel.previewImport { context.readUtf8(it) }
        }
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = insets.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val hasExactAlarms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            SectionHeader(stringResource(R.string.settings_section_enforcement))
            GrantItem(
                position = GroupPosition.First,
                index = 0,
                label = stringResource(R.string.settings_accessibility_service),
                granted = uiState.accessibility,
            ) { context.openAccessibilitySettings() }
            GrantItem(
                position = GroupPosition.Middle,
                index = 1,
                label = stringResource(R.string.settings_usage_access),
                granted = uiState.usageAccess,
            ) { context.openUsageAccessSettings() }
            if (hasExactAlarms) {
                GrantItem(
                    position = GroupPosition.Middle,
                    index = 2,
                    label = stringResource(R.string.settings_exact_alarms),
                    granted = uiState.exactAlarms,
                ) { context.openExactAlarmSettings() }
            }
            if (uiState.healthSupported) {
                GrantItem(
                    position = GroupPosition.Last,
                    index = 3,
                    label = stringResource(R.string.settings_health_connect),
                    granted = uiState.health,
                ) { healthLauncher.launch(viewModel.healthPermissions) }
            } else {
                GroupedListItem(
                    position = GroupPosition.Last,
                    title = stringResource(R.string.settings_health_connect),
                    supporting = stringResource(R.string.settings_health_unavailable),
                )
            }
            if (hasExactAlarms && !uiState.exactAlarms) {
                Text(
                    stringResource(R.string.settings_exact_alarms_warning),
                    Modifier.padding(start = 18.dp, top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            BehaviorSettingsSection(
                settings = behaviorSettings,
                onPolicySelected = viewModel::setOverlapPolicy,
                onChooseBoundary = { timePickerOpen = true },
            )

            DiagnosticsSection(diagnostics)

            SectionHeader(stringResource(R.string.settings_section_uninstall_lock))
            GroupedListItem(
                position = GroupPosition.First,
                onClick = if (uiState.deviceOwner) null else ({ guideOpen = !guideOpen }),
                title = stringResource(R.string.settings_device_owner),
                supporting = if (uiState.deviceOwner) null else stringResource(R.string.settings_device_owner_optional),
                trailing = {
                    if (uiState.deviceOwner) {
                        MetChip()
                    } else {
                        TextButton(onClick = { guideOpen = !guideOpen }) {
                            Text(stringResource(if (guideOpen) R.string.settings_hide else R.string.settings_set_up))
                        }
                    }
                },
            )
            AnimatedVisibility(
                visible = guideOpen && !uiState.deviceOwner,
                enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
            ) {
                DeviceOwnerGuide(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
            GroupedListItem(position = GroupPosition.Last) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (uiState.deviceOwner) {
                        Text(
                            stringResource(R.string.settings_owner_active),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        RelinquishControls(
                            state = relinquish,
                            onStart = { dialog = RelinquishDialog.Start },
                            onCancel = { viewModel.cancelRelinquish() },
                            onConfirm = { dialog = RelinquishDialog.Confirm },
                        )
                        if (uiState.relinquishFailed) {
                            Text(
                                stringResource(R.string.settings_relinquish_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.settings_owner_missing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionHeader(stringResource(R.string.settings_section_emergency))
            Surface(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.settings_emergency_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EmergencyUnlockPills(pills = emergencyUnlocks.pills)
                }
            }

            ArchiveSection(
                working = archiveState == ArchiveUiState.Working,
                onExport = { exportLauncher.launch("hablock-archive.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
            )

            SectionHeader(stringResource(R.string.settings_section_language))
            val currentLocaleTag = remember {
                AppCompatDelegate.getApplicationLocales().toLanguageTags()
            }
            val currentLanguageName = LANGUAGE_OPTIONS.firstOrNull { it.tag == currentLocaleTag }?.displayName
                ?: stringResource(R.string.settings_language_system)
            GroupedListItem(
                position = GroupPosition.Single,
                onClick = { languageDialogOpen = true },
                title = stringResource(R.string.settings_section_language),
                trailing = { Text(currentLanguageName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )

            SectionHeader(stringResource(R.string.settings_section_about))
            GroupedListItem(
                position = GroupPosition.First,
                title = stringResource(R.string.settings_version),
                onClick = versionClick,
                trailing = {
                    Text(
                        stringResource(R.string.settings_version_value, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            GroupedListItem(
                position = GroupPosition.Last,
                title = stringResource(R.string.settings_offline_title),
                supporting = stringResource(R.string.settings_offline_body),
                trailing = { MetChip() },
            )
        }
    }

    when (dialog) {
        RelinquishDialog.Start -> HablockDialog(
            title = stringResource(R.string.settings_relinquish_start_title),
            message = stringResource(R.string.settings_relinquish_start_message),
            confirmLabel = stringResource(R.string.settings_relinquish_start_confirm),
            onConfirm = {
                viewModel.startRelinquish()
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        RelinquishDialog.Confirm -> HablockDialog(
            title = stringResource(R.string.settings_relinquish_confirm_title),
            message = stringResource(R.string.settings_relinquish_confirm_message),
            confirmLabel = stringResource(R.string.settings_relinquish_confirm_label),
            destructive = true,
            onConfirm = {
                viewModel.confirmRelinquish()
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }

    if (languageDialogOpen) {
        LanguagePickerDialog(
            currentTag = remember {
                AppCompatDelegate.getApplicationLocales().toLanguageTags()
            },
            onDismiss = { languageDialogOpen = false },
            onSelect = { tag ->
                val locales = if (tag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tag)
                }
                AppCompatDelegate.setApplicationLocales(locales)
                languageDialogOpen = false
            },
        )
    }

    if (timePickerOpen) {
        DayBoundaryPickerDialog(
            initialMinutes = behaviorSettings.dayBoundaryMinutes,
            onDismiss = { timePickerOpen = false },
            onConfirm = { minutes ->
                viewModel.setDayBoundaryMinutes(minutes)
                timePickerOpen = false
            },
        )
    }

    when (val state = archiveState) {
        ArchiveUiState.Idle, ArchiveUiState.Working -> Unit
        is ArchiveUiState.Preview -> ArchivePreviewDialog(
            preview = state.details,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::dismissArchiveStatus,
        )
        is ArchiveUiState.Exported -> ArchiveMessageDialog(
            title = stringResource(R.string.settings_archive_export_success_title),
            message = stringResource(
                R.string.settings_archive_export_success_message,
                state.blockCount,
                state.historyCount,
            ),
            onDismiss = viewModel::dismissArchiveStatus,
        )
        is ArchiveUiState.Imported -> ArchiveMessageDialog(
            title = stringResource(R.string.settings_archive_import_success_title),
            message = stringResource(
                R.string.settings_archive_import_success_message,
                state.result.insertedBlocks,
                state.result.skippedBlocks,
                state.result.insertedHistory,
                state.result.skippedHistory,
            ),
            onDismiss = viewModel::dismissArchiveStatus,
        )
        is ArchiveUiState.Failed -> ArchiveMessageDialog(
            title = stringResource(R.string.settings_archive_error_title),
            message = stringResource(
                if (state.operation == ArchiveUiState.Operation.EXPORT) {
                    R.string.settings_archive_export_error
                } else {
                    R.string.settings_archive_import_error
                },
            ),
            onDismiss = viewModel::dismissArchiveStatus,
        )
    }
}

@Composable
private fun ArchiveSection(
    working: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_section_archive))
    GroupedListItem(
        position = GroupPosition.First,
        title = stringResource(R.string.settings_archive_export_title),
        supporting = stringResource(R.string.settings_archive_export_body),
        trailing = {
            OutlinedButton(onClick = onExport, enabled = !working) {
                Text(
                    stringResource(
                        if (working) R.string.settings_archive_working else R.string.settings_archive_export_button,
                    ),
                )
            }
        },
    )
    GroupedListItem(
        position = GroupPosition.Last,
        title = stringResource(R.string.settings_archive_import_title),
        supporting = stringResource(R.string.settings_archive_import_body),
        trailing = {
            OutlinedButton(onClick = onImport, enabled = !working) {
                Text(
                    stringResource(
                        if (working) R.string.settings_archive_working else R.string.settings_archive_import_button,
                    ),
                )
            }
        },
    )
}

@Composable
private fun ArchivePreviewDialog(
    preview: ArchiveImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_archive_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.settings_archive_preview_counts,
                        preview.newBlockCount,
                        preview.skippedBlockCount,
                        preview.newHistoryCount,
                        preview.skippedHistoryCount,
                    ),
                )
                Text(
                    stringResource(
                        if (preview.willRestoreGlobalSettings) {
                            R.string.settings_archive_preview_settings_restore
                        } else {
                            R.string.settings_archive_preview_settings_keep
                        },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_archive_confirm_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ArchiveMessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

private suspend fun Context.writeUtf8(uri: Uri, text: String) = withContext(Dispatchers.IO) {
    val stream = contentResolver.openOutputStream(uri, "wt")
        ?: error("Could not open the selected archive destination")
    stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
}

private suspend fun Context.readUtf8(uri: Uri): String = withContext(Dispatchers.IO) {
    val stream = contentResolver.openInputStream(uri)
        ?: error("Could not open the selected archive")
    stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

@Composable
private fun DiagnosticsSection(diagnostics: EnforcementDiagnostics) {
    SectionHeader(stringResource(R.string.settings_section_diagnostics))
    GroupedListItem(position = GroupPosition.Single) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DiagnosticDetail(
                stringResource(R.string.settings_diagnostics_backend),
                when (diagnostics.activeBackend) {
                    EnforcementBackendType.ACCESSIBILITY -> stringResource(R.string.settings_diagnostics_backend_accessibility)
                    EnforcementBackendType.DEVICE_OWNER -> stringResource(R.string.settings_diagnostics_backend_device_owner)
                    null -> stringResource(R.string.settings_diagnostics_unknown)
                },
            )
            DiagnosticDetail(
                stringResource(R.string.settings_diagnostics_accessibility),
                stringResource(
                    R.string.settings_diagnostics_accessibility_value,
                    stringResource(
                        if (diagnostics.accessibilityServiceConnected) {
                            R.string.settings_diagnostics_connected
                        } else {
                            R.string.settings_diagnostics_disconnected
                        },
                    ),
                    diagnosticTimestamp(diagnostics.lastAccessibilityEventAtMillis),
                ),
            )
            DiagnosticDetail(
                stringResource(R.string.settings_diagnostics_evaluation),
                stringResource(
                    R.string.settings_diagnostics_evaluation_value,
                    diagnosticRun(diagnostics.lastEvaluation),
                    diagnostics.evaluatedBlockCount,
                    diagnostics.blockedBlockCount,
                ),
            )
            DiagnosticDetail(
                stringResource(R.string.settings_diagnostics_enforcement),
                diagnosticRun(diagnostics.lastEnforcement),
            )
            DiagnosticDetail(
                stringResource(R.string.settings_diagnostics_suspension_failures),
                diagnostics.suspensionFailures.sorted().joinToString().ifEmpty {
                    stringResource(R.string.settings_diagnostics_none)
                },
            )
            DiagnosticDetail(
                stringResource(R.string.settings_diagnostics_permissions),
                stringResource(
                    R.string.settings_diagnostics_permissions_value,
                    diagnosticBoolean(diagnostics.accessibilityPermissionGranted),
                    diagnosticBoolean(diagnostics.usageAccessGranted),
                    diagnosticHealth(diagnostics.healthAvailability, diagnostics.healthPermissionsGranted),
                    diagnosticBoolean(diagnostics.exactAlarmPermissionGranted),
                ),
            )
            DiagnosticDetail(
                stringResource(R.string.settings_diagnostics_schedule),
                stringResource(
                    R.string.settings_diagnostics_schedule_value,
                    diagnosticTimestamp(diagnostics.nextResetAtMillis, scheduled = true),
                    diagnosticTimestamp(diagnostics.nextTransitionAtMillis, scheduled = true),
                ),
            )
        }
    }
}

@Composable
private fun DiagnosticDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun diagnosticRun(run: DiagnosticRun?): String = when {
    run == null -> stringResource(R.string.settings_diagnostics_never)
    run.succeeded -> stringResource(R.string.settings_diagnostics_run_ok, diagnosticTimestamp(run.atMillis))
    else -> stringResource(R.string.settings_diagnostics_run_failed, diagnosticTimestamp(run.atMillis))
}

@Composable
private fun diagnosticBoolean(value: Boolean?): String = stringResource(
    when (value) {
        true -> R.string.status_on
        false -> R.string.settings_diagnostics_off
        null -> R.string.settings_diagnostics_unknown
    },
)

@Composable
private fun diagnosticHealth(availability: HcAvailability?, granted: Boolean?): String = stringResource(
    when {
        availability == null -> R.string.settings_diagnostics_unknown
        availability == HcAvailability.UNAVAILABLE -> R.string.settings_diagnostics_health_unavailable
        granted == true && availability == HcAvailability.NO_MINDFULNESS -> R.string.settings_diagnostics_health_limited
        granted == true -> R.string.settings_diagnostics_health_granted
        else -> R.string.settings_diagnostics_health_permission_missing
    },
)

@Composable
private fun diagnosticTimestamp(millis: Long?, scheduled: Boolean = false): String {
    if (millis == null) {
        return stringResource(
            if (scheduled) R.string.settings_diagnostics_not_scheduled else R.string.settings_diagnostics_never,
        )
    }
    val locale = LocalConfiguration.current.locales[0]
    return remember(millis, locale) {
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT,
            java.text.DateFormat.SHORT,
            locale,
        ).format(Date(millis))
    }
}

@Composable
private fun BehaviorSettingsSection(
    settings: BehaviorSettings,
    onPolicySelected: (OverlapPolicy) -> Unit,
    onChooseBoundary: () -> Unit,
) {
    val enabled = !settings.changesLocked
    SectionHeader(stringResource(R.string.settings_section_behavior))
    GroupedListItem(
        position = GroupPosition.First,
        modifier = Modifier.alpha(if (enabled) 1f else 0.55f),
        title = stringResource(R.string.settings_overlap_title),
        supporting = stringResource(R.string.settings_overlap_body),
    ) {
        Column(Modifier.padding(top = 8.dp)) {
            PolicyOption(
                label = stringResource(R.string.settings_overlap_all),
                selected = settings.overlapPolicy == OverlapPolicy.ALL_BLOCKS,
                enabled = enabled,
                onClick = { onPolicySelected(OverlapPolicy.ALL_BLOCKS) },
            )
            PolicyOption(
                label = stringResource(R.string.settings_overlap_any),
                selected = settings.overlapPolicy == OverlapPolicy.ANY_BLOCK,
                enabled = enabled,
                onClick = { onPolicySelected(OverlapPolicy.ANY_BLOCK) },
            )
        }
    }
    GroupedListItem(
        position = GroupPosition.Last,
        modifier = Modifier.alpha(if (enabled) 1f else 0.55f),
        onClick = onChooseBoundary.takeIf { enabled },
        title = stringResource(R.string.settings_day_boundary_title),
        supporting = stringResource(R.string.settings_day_boundary_body),
        trailing = {
            Text(
                boundaryTimeLabel(settings.dayBoundaryMinutes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
    if (settings.changesLocked) {
        Text(
            stringResource(R.string.settings_behavior_locked),
            Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PolicyOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun boundaryTimeLabel(minutes: Int): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(minutes, locale, is24Hour) {
        val skeleton = if (is24Hour) "Hm" else "hm"
        val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
        LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}

@Composable
private fun DayBoundaryPickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_time_picker_title)) },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.settings_time_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun LanguagePickerDialog(
    currentTag: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language_dialog_title)) },
        text = {
            Column {
                LANGUAGE_OPTIONS.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option.tag == currentTag,
                            onClick = { onSelect(option.tag) },
                        )
                        Text(
                            if (option.tag.isEmpty()) stringResource(R.string.settings_language_system) else option.displayName,
                            Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun MetChip() {
    val accents = LocalHablockAccents.current
    StatusChip(stringResource(R.string.status_on), containerColor = accents.metContainer, contentColor = accents.onMetContainer)
}

@Composable
private fun GrantItem(
    position: GroupPosition,
    index: Int,
    label: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    GroupedListItem(
        position = position,
        onClick = if (granted) null else onGrant,
        title = label,
        leading = { GrantBadge(index, granted, Modifier.size(28.dp)) },
        trailing = {
            if (granted) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.settings_granted), tint = LocalHablockAccents.current.met)
            } else {
                Button(onClick = onGrant) { Text(stringResource(R.string.action_grant)) }
            }
        },
    )
}

/**
 * The one place the wave language inverts: the relinquish countdown is a dead-flat
 * line. Everywhere else waves mean alive and encouraging; here stillness means
 * this is not a game.
 */
@Composable
private fun RelinquishControls(
    state: RelinquishState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    when (state) {
        RelinquishState.Idle -> OutlinedButton(onClick = onStart) { Text(stringResource(R.string.settings_relinquish_start_button)) }

        is RelinquishState.Counting -> {
            val tick by rememberTicker(30_000L)
            val remaining = (state.deadline.toEpochMilli() - tick).coerceAtLeast(0L).milliseconds
            val total = GateConstants.RELINQUISH_COOLDOWN
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigNumerals(
                        formatCountdown(remaining),
                        style = numeralStyle(28.sp),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        stringResource(R.string.settings_relinquish_countdown_label),
                        Modifier.padding(bottom = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearWavyProgressIndicator(
                    progress = {
                        (1f - remaining.inWholeMilliseconds.toFloat() / total.inWholeMilliseconds).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.error,
                    amplitude = { 0f },
                )
                TextButton(onClick = onCancel) { Text(stringResource(R.string.settings_relinquish_cancel)) }
            }
        }

        RelinquishState.Ready -> Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { Text(stringResource(R.string.settings_relinquish_now)) }
    }
}
