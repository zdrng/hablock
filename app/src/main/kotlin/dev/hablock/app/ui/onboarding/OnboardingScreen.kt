package dev.hablock.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hablock.app.R
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.ui.components.BreathingGateBadge
import dev.hablock.app.ui.components.GrantBadge
import dev.hablock.app.ui.components.GroupPosition
import dev.hablock.app.ui.components.GroupedListItem
import dev.hablock.app.ui.components.PolygonBadge
import dev.hablock.app.ui.components.groupPositionOf
import dev.hablock.app.ui.gateViewModel
import dev.hablock.app.ui.system.openAccessibilitySettings
import dev.hablock.app.ui.system.openNotificationSettings
import dev.hablock.app.ui.system.openUsageAccessSettings
import dev.hablock.app.ui.theme.LocalHablockAccents
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val viewModel = gateViewModel { container ->
        OnboardingViewModel(container.settingsRepository, container.permissionChecker, container.healthRepository)
    }
    val grants by viewModel.grants.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshGrants()
        onPauseOrDispose {}
    }

    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageDots(pagerState.currentPage)
                if (pagerState.currentPage < 2) {
                    TextButton(onClick = { goTo(2) }) { Text(stringResource(R.string.onboarding_skip)) }
                }
            }
            HorizontalPager(pagerState, Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> WelcomePage(onStart = { goTo(1) })
                    1 -> ExplainPage(onNext = { goTo(2) })
                    else -> GrantsPage(
                        grants = grants,
                        healthPermissions = viewModel.healthPermissions,
                        onRefresh = viewModel::refreshGrants,
                        onContinue = { viewModel.finish(onFinished) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PageDots(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGE_COUNT) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) 26.dp else 8.dp, label = "dot")
            val color by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                label = "dotColor",
            )
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BreathingGateBadge(Modifier.size(210.dp), color = MaterialTheme.colorScheme.primaryContainer)
        }
        Text(
            stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.displayMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        Button(onClick = onStart, Modifier.fillMaxWidth()) { Text(stringResource(R.string.onboarding_welcome_cta)) }
        Spacer(Modifier.height(28.dp))
    }
}

private data class Explainer(val title: String, val detail: String)

@Composable
private fun ExplainPage(onNext: () -> Unit) {
    val sessionMinutes = GateConstants.SESSION_DURATION.inWholeMinutes.toInt()
    val titles = stringArrayResource(R.array.onboarding_explainer_titles)
    val details = stringArrayResource(R.array.onboarding_explainer_details)
    val explainers = titles.mapIndexed { index, title -> Explainer(title.format(sessionMinutes), details[index]) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.onboarding_explain_title), style = MaterialTheme.typography.headlineMediumEmphasized)
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                explainers.forEachIndexed { index, item ->
                    GroupedListItem(
                        position = groupPositionOf(index, explainers.size),
                        title = item.title,
                        supporting = item.detail,
                        leading = {
                            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                PolygonBadge(
                                    MaterialShapes.Cookie4Sided,
                                    Modifier.size(34.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                )
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.onboarding_demo_title), style = MaterialTheme.typography.titleMediumEmphasized)
                    LinearWavyProgressIndicator(
                        progress = { 0.66f },
                        modifier = Modifier.fillMaxWidth(),
                        amplitude = { it },
                    )
                    Text(
                        stringResource(R.string.onboarding_demo_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        Button(onClick = onNext, Modifier.fillMaxWidth().padding(bottom = 28.dp)) { Text(stringResource(R.string.onboarding_explain_cta)) }
    }
}

@Composable
private fun GrantsPage(
    grants: GrantsUiState,
    healthPermissions: Set<String>,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { onRefresh() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onRefresh() }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.onboarding_grants_title), style = MaterialTheme.typography.headlineMediumEmphasized)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_grants_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OnboardingGrant(GroupPosition.First, 0, stringResource(R.string.onboarding_grant_accessibility), stringResource(R.string.onboarding_grant_accessibility_detail), grants.accessibility) {
                    context.openAccessibilitySettings()
                }
                OnboardingGrant(GroupPosition.Middle, 1, stringResource(R.string.onboarding_grant_usage), stringResource(R.string.onboarding_grant_usage_detail), grants.usageAccess) {
                    context.openUsageAccessSettings()
                }
                OnboardingGrant(GroupPosition.Middle, 2, stringResource(R.string.onboarding_grant_notifications), stringResource(R.string.onboarding_grant_notifications_detail), grants.notifications) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.openNotificationSettings()
                    }
                }
                if (grants.healthSupported) {
                    OnboardingGrant(GroupPosition.Last, 3, stringResource(R.string.onboarding_grant_health), stringResource(R.string.onboarding_grant_health_detail), grants.health) {
                        healthLauncher.launch(healthPermissions)
                    }
                } else {
                    GroupedListItem(
                        position = GroupPosition.Last,
                        title = stringResource(R.string.onboarding_grant_health),
                        supporting = stringResource(R.string.onboarding_grant_health_unavailable),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        Button(onClick = onContinue, Modifier.fillMaxWidth().padding(bottom = 28.dp)) { Text(stringResource(R.string.onboarding_grants_cta)) }
    }
}

@Composable
private fun OnboardingGrant(
    position: GroupPosition,
    index: Int,
    title: String,
    detail: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    val accents = LocalHablockAccents.current
    GroupedListItem(
        position = position,
        onClick = if (granted) null else onGrant,
        title = title,
        supporting = detail,
        leading = { GrantBadge(index, granted, Modifier.size(30.dp)) },
        trailing = {
            if (granted) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.onboarding_granted), tint = accents.met)
            } else {
                Button(onClick = onGrant) { Text(stringResource(R.string.action_grant)) }
            }
        },
    )
}
