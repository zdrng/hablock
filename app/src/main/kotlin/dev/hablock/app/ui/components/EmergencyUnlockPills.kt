package dev.hablock.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.model.EmergencyPill
import dev.hablock.app.ui.format.formatCountdownPrecise
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun EmergencyUnlockPills(
    pills: List<EmergencyPill>,
    onConsume: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        pills.forEach { pill ->
            EmergencyPillSurface(
                pill = pill,
                onConsume = onConsume,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EmergencyPillSurface(
    pill: EmergencyPill,
    onConsume: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tick by rememberTicker(1_000L)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val refillProgress = if (pill.available) {
        1f
    } else if (pill.refillAt != null) {
        val total = GateConstants.EMERGENCY_REFILL.inWholeMilliseconds.toFloat()
        val elapsed = (tick - (pill.refillAt - total)).coerceAtLeast(0f)
        (elapsed / total).coerceIn(0f, 1f)
    } else {
        0f
    }

    val containerColor = if (pill.available) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (pill.available) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    var holdProgress by remember { mutableFloatStateOf(0f) }
    val animatedHold by animateFloatAsState(
        targetValue = holdProgress,
        animationSpec = spring(),
        label = "hold",
    )

    Surface(
        modifier = modifier
            .height(72.dp)
            .then(
                if (pill.available && onConsume != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                holdProgress = 0f
                                val startedAt = System.currentTimeMillis()
                                val job = scope.launch {
                                    while (true) {
                                        delay(100)
                                        holdProgress = ((System.currentTimeMillis() - startedAt).toFloat() / GateConstants.EMERGENCY_HOLD.inWholeMilliseconds).coerceIn(0f, 1f)
                                        if (holdProgress >= 1f) break
                                    }
                                }
                                val released = tryAwaitRelease()
                                job.cancel()
                                if (released && holdProgress >= 1f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onConsume()
                                } else {
                                    holdProgress = 0f
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            val remaining = pill.refillAt?.let { (it - tick).coerceAtLeast(0L).milliseconds }
            Text(
                text = if (pill.available) {
                    "Emergency"
                } else if (refillProgress < 1f && remaining != null) {
                    formatCountdownPrecise(remaining)
                } else {
                    "Ready"
                },
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            if (pill.available && onConsume != null && animatedHold > 0f) {
                LinearWavyProgressIndicator(
                    progress = { animatedHold },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    color = contentColor,
                    amplitude = { 1f },
                )
            }
        }
    }
}
