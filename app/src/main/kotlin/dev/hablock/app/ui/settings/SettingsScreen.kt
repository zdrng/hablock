package dev.hablock.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hablock.app.BuildConfig
import dev.hablock.app.R
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.model.RelinquishState
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
import kotlin.time.Duration.Companion.milliseconds

private enum class RelinquishDialog { Start, Confirm }

@Composable
fun SettingsScreen() {
    val viewModel = gateViewModel { container ->
        SettingsViewModel(
            container.permissionChecker,
            container.deviceOwnerController,
            container.relinquishTimer,
            container.healthRepository,
            container.settingsRepository,
        )
    }
    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val relinquish by viewModel.relinquish.collectAsStateWithLifecycle()
    val emergencyUnlocks by viewModel.emergencyUnlocks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var guideOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<RelinquishDialog?>(null) }
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

            SectionHeader(stringResource(R.string.settings_section_about))
            GroupedListItem(
                position = GroupPosition.First,
                title = stringResource(R.string.settings_version),
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
