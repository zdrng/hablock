package dev.hablock.app.ui.screentime

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hablock.app.R
import dev.hablock.app.ui.components.AppIconCookie
import dev.hablock.app.ui.components.BigNumerals
import dev.hablock.app.ui.components.BreathingDial
import dev.hablock.app.ui.components.BreathingGateBadge
import dev.hablock.app.ui.components.GroupedListItem
import dev.hablock.app.ui.components.groupPositionOf
import dev.hablock.app.ui.format.formatClock
import dev.hablock.app.ui.format.formatMinutes
import dev.hablock.app.ui.gateViewModel
import dev.hablock.app.ui.system.openUsageAccessSettings
import dev.hablock.app.ui.theme.numeralStyle

private const val FULL_DIAL_MINUTES = 8 * 60f

@Composable
fun ScreenTimeScreen() {
    val viewModel = gateViewModel { container ->
        ScreenTimeViewModel(
            container.usageStatsRepository,
            container.installedAppsRepository,
            container.permissionChecker,
            container.dayClock,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.screentime_title)) },
                subtitle = if (uiState.hasAccess) {
                    { Text(stringResource(R.string.screentime_since, formatClock(uiState.since))) }
                } else {
                    null
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { insets ->
        if (!uiState.hasAccess) {
            NoAccessState(Modifier.padding(insets))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(top = insets.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    BreathingDial(
                        progress = { (uiState.totalMinutes / FULL_DIAL_MINUTES).toFloat().coerceIn(0f, 1f) },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BigNumerals(
                                formatMinutes(uiState.totalMinutes),
                                style = numeralStyle(44.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                stringResource(R.string.screentime_on_screen_today),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (uiState.rows.isEmpty()) {
                item {
                    Text(
                        stringResource(if (uiState.loading) R.string.screentime_loading else R.string.screentime_empty),
                        Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val peak = uiState.rows.first().minutes
                itemsIndexed(uiState.rows, key = { _, row -> row.packageName }) { index, row ->
                    GroupedListItem(
                        position = groupPositionOf(index, uiState.rows.size),
                        leading = { AppIconCookie(row.packageName, row.label, size = 32.dp) },
                        title = row.label,
                        trailing = {
                            Text(formatMinutes(row.minutes), style = MaterialTheme.typography.titleMediumEmphasized)
                        },
                        content = {
                            Spacer(Modifier.height(6.dp))
                            LinearWavyProgressIndicator(
                                progress = { (row.minutes / peak).toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                                amplitude = { (row.minutes / peak).toFloat() },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NoAccessState(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BreathingGateBadge(Modifier.size(140.dp), color = MaterialTheme.colorScheme.secondaryContainer)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.screentime_no_access_title),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.screentime_no_access_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { context.openUsageAccessSettings() }) { Text(stringResource(R.string.screentime_grant_usage)) }
    }
}
