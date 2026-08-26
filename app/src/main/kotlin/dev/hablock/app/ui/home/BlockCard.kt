package dev.hablock.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hablock.app.R
import dev.hablock.app.domain.model.Block
import dev.hablock.app.domain.model.GateState
import dev.hablock.app.ui.components.AppShapeCluster
import dev.hablock.app.ui.components.BigNumerals
import dev.hablock.app.ui.components.ConditionMeter
import dev.hablock.app.ui.components.MorphingGateBadge
import dev.hablock.app.ui.components.rememberTicker
import dev.hablock.app.ui.format.formatClock
import dev.hablock.app.ui.theme.numeralStyle

/**
 * The hero card. The blocked apps themselves are its face — a staggered cluster of
 * icons in expressive forms — while container color and motion encode the four states.
 */
@Composable
fun BlockCard(
    block: Block,
    state: GateState?,
    expanded: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paused = !block.enabled
    val session = state as? GateState.SessionActive
    val open = state is GateState.Open
    val met = state?.metCount ?: 0
    val threshold = block.thresholdN.coerceIn(1, block.conditions.size.coerceAtLeast(1))

    val container by animateColorAsState(
        targetValue = when {
            paused -> MaterialTheme.colorScheme.surfaceContainerLowest
            session != null -> MaterialTheme.colorScheme.tertiaryContainer
            open -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "container",
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = 900f),
        label = "press",
    )

    Surface(
        modifier = modifier
            .scale(pressScale)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = stringResource(R.string.home_card_click_label),
                onLongClickLabel = stringResource(R.string.home_card_long_click_label),
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .alpha(if (paused) 0.55f else 1f),
        shape = MaterialTheme.shapes.large,
        color = container,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (block.blockedPackages.isNotEmpty()) {
                    AppShapeCluster(
                        block.blockedPackages.map { it to (block.blockedLabels[it] ?: it.substringAfterLast('.')) },
                    )
                } else {
                    MorphingGateBadge(
                        progress = if (open || session != null) 1f else met.toFloat() / threshold,
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(block.name, style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
                    Text(
                        statusLine(paused, session, open, met, threshold),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Switch(checked = block.enabled, onCheckedChange = onToggle)
            }

            AnimatedVisibility(
                visible = expanded && !paused,
                enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
            ) {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (session != null) {
                        SessionCountdown(session)
                    } else {
                        MetCountPanel(met, threshold, open)
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            state?.progress?.forEach { item ->
                                ConditionMeter(item.condition, item.current, item.required)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun statusLine(paused: Boolean, session: GateState.SessionActive?, open: Boolean, met: Int, threshold: Int): String =
    when {
        paused -> stringResource(R.string.home_card_status_paused)
        session != null -> stringResource(R.string.home_card_status_open_until, formatClock(session.endsAt))
        open -> stringResource(R.string.home_card_status_open)
        else -> stringResource(R.string.home_card_status_locked, met, threshold)
    }

/** The big "2 of 3" numeral in a tonal container — the clock-screen look. */
@Composable
private fun MetCountPanel(met: Int, threshold: Int, open: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (open) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            BigNumerals(met.toString(), style = numeralStyle(40.sp))
            Text(
                stringResource(R.string.home_card_of, threshold),
                Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SessionCountdown(session: GateState.SessionActive) {
    val tick by rememberTicker(1_000L)
    val remaining = ((session.endsAt.toEpochMilli() - tick) / 1000L).coerceAtLeast(0L)
    val minutes = remaining / 60
    val seconds = remaining % 60
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BigNumerals(
            "%d:%02d".format(minutes, seconds),
            style = numeralStyle(44.sp),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(
            stringResource(R.string.home_card_session_left),
            Modifier.padding(bottom = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
