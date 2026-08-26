package dev.hablock.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Breathing centerpiece: two nested wavy rings at different phases read as organic
 * depth — no custom drawing at all. [breathing] animates the amplitude when there is no
 * hard progress story to tell; a session countdown passes its own amplitude instead.
 */
@Composable
fun BreathingDial(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    breathing: Boolean = true,
    amplitude: (Float) -> Float = { it },
    center: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "dial")
    val breath by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val amplitudeLambda: (Float) -> Float = if (breathing) ({ breath }) else amplitude
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(0.86f),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            amplitude = { amplitudeLambda(it) * 0.6f },
            wavelength = 28.dp,
            waveSpeed = 6.dp,
        )
        CircularWavyProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            amplitude = amplitudeLambda,
            wavelength = 40.dp,
            waveSpeed = 12.dp,
        )
        center()
    }
}
