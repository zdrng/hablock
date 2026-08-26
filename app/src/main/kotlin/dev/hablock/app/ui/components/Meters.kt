package dev.hablock.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.hablock.app.R
import dev.hablock.app.domain.model.Condition
import dev.hablock.app.ui.format.displayLabel
import dev.hablock.app.ui.format.formatProgress
import dev.hablock.app.ui.theme.LocalHablockAccents

/**
 * Condition identity: little people doing the habit — a runner for steps, a lifter for
 * workouts, a meditator for mindfulness — and app-usage wears the helper app's real icon.
 */
@Composable
fun ConditionAvatar(
    condition: Condition,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    met: Boolean = false,
) {
    when (condition) {
        is Condition.AppUsage -> AppIconCookie(condition.packageName, condition.appLabel, modifier, size)
        is Condition.Steps -> ConditionPictogram(HablockIcons.Run, PictogramMotion.Stride, met, modifier, size)
        is Condition.Exercise -> ConditionPictogram(HablockIcons.Lift, PictogramMotion.Rep, met, modifier, size)
        is Condition.Meditation -> ConditionPictogram(HablockIcons.Meditate, PictogramMotion.Breathe, met, modifier, size)
    }
}

enum class PictogramMotion { Stride, Rep, Breathe }

/**
 * A tiny athlete idling on a round badge, Apple-Fitness style: the runner strides,
 * the lifter pumps out slow reps, the meditator hovers on a long breath. Met turns
 * the badge happy green.
 */
@Composable
fun ConditionPictogram(
    icon: ImageVector,
    motion: PictogramMotion,
    met: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val (happy, onHappy) = happyMetColors()
    val badge by animateColorAsState(
        targetValue = if (met) happy else MaterialTheme.colorScheme.secondaryContainer,
        label = "pictoBadge",
    )
    val glyph by animateColorAsState(
        targetValue = if (met) onHappy else MaterialTheme.colorScheme.onSecondaryContainer,
        label = "pictoGlyph",
    )
    val transition = rememberInfiniteTransition(label = "picto")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(
                durationMillis = when (motion) {
                    PictogramMotion.Stride -> 520
                    PictogramMotion.Rep -> 1_100
                    PictogramMotion.Breathe -> 2_600
                },
                easing = FastOutSlowInEasing,
            ),
            RepeatMode.Reverse,
        ),
        label = "phase",
    )
    Box(
        modifier.size(size).clip(CircleShape).background(badge),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = glyph,
            modifier = Modifier
                .size(size * 0.62f)
                .graphicsLayer {
                    when (motion) {
                        PictogramMotion.Stride -> {
                            translationY = (phase - 0.5f) * size.toPx() * 0.07f
                            rotationZ = (phase - 0.5f) * 7f
                        }
                        PictogramMotion.Rep -> {
                            translationY = (0.5f - phase) * size.toPx() * 0.09f
                            scaleX = 0.96f + phase * 0.08f
                            scaleY = 0.96f + phase * 0.08f
                        }
                        PictogramMotion.Breathe -> {
                            translationY = (phase - 0.5f) * size.toPx() * 0.05f
                            scaleX = 0.97f + phase * 0.06f
                            scaleY = 0.97f + phase * 0.06f
                        }
                    }
                },
        )
    }
}

/**
 * One condition row. The wavy bar's amplitude grows with progress — a barely-started
 * goal is a calm flat line, a nearly-earned one visibly excited. Met rows collapse
 * to a lime chip; stillness and waves both mean something.
 */
@Composable
fun ConditionMeter(
    condition: Condition,
    current: Double,
    required: Double,
    modifier: Modifier = Modifier,
) {
    val accents = LocalHablockAccents.current
    val met = current >= required
    val fraction = (current / required.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConditionAvatar(condition, size = 24.dp, met = met)
            Text(
                condition.displayLabel(),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (met) {
                StatusChip(
                    stringResource(R.string.status_done),
                    containerColor = accents.metContainer,
                    contentColor = accents.onMetContainer,
                )
            } else {
                Text(
                    condition.formatProgress(current, required),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!met) {
            LinearWavyProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                amplitude = { it },
            )
        }
    }
}
