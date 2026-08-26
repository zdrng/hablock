package dev.hablock.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import dev.hablock.app.ui.theme.LocalHablockAccents

/**
 * The pair every "met" moment wears: the brighter lime of the met accents as the surface,
 * with the darker one as content — bright and happy in dark mode too, never olive.
 */
@Composable
fun happyMetColors(): Pair<Color, Color> {
    val accents = LocalHablockAccents.current
    return if (accents.metContainer.luminance() >= accents.met.luminance()) {
        accents.metContainer to accents.onMetContainer
    } else {
        accents.met to accents.metContainer
    }
}

private val grantShapes = listOf(
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Burst,
)

/**
 * Permission-row badge: every row starts as a plain circle in its own shade of gray;
 * granting it blooms the circle into the row's own shape and turns it happy green.
 */
@Composable
fun GrantBadge(index: Int, granted: Boolean, modifier: Modifier = Modifier) {
    val target = grantShapes[index % grantShapes.size]
    val morph = remember(target) { Morph(MaterialShapes.Circle, target) }
    val progress by animateFloatAsState(
        targetValue = if (granted) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "grantMorph",
    )
    val grays = with(MaterialTheme.colorScheme) {
        listOf(surfaceContainerHigh, surfaceContainerHighest, surfaceVariant, outlineVariant)
    }
    val (happyGreen, _) = happyMetColors()
    val color by animateColorAsState(
        targetValue = if (granted) happyGreen else grays[index % grays.size],
        label = "grantColor",
    )
    Canvas(modifier) {
        val path = morph.toPath(progress.coerceIn(0f, 1f)).asComposePath()
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        drawPath(path, color)
    }
}

private val stepShapes = listOf(
    MaterialShapes.Flower,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Burst,
)

/**
 * Wizard progress: three dots where the current step blooms from a circle into its own
 * polygon, done steps settle back into filled dots, upcoming ones wait as gray dots.
 */
@Composable
fun StepShapeDots(step: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val current = index == step
            val done = index < step
            val morph = remember(index) { Morph(MaterialShapes.Circle, stepShapes[index % stepShapes.size]) }
            val bloom by animateFloatAsState(
                targetValue = if (current) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                label = "stepMorph",
            )
            val size by animateDpAsState(
                targetValue = when {
                    current -> 26.dp
                    done -> 10.dp
                    else -> 8.dp
                },
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                label = "stepSize",
            )
            val color by animateColorAsState(
                targetValue = if (current || done) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                label = "stepColor",
            )
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(size)) {
                    val path = morph.toPath(bloom.coerceIn(0f, 1f)).asComposePath()
                    val matrix = Matrix()
                    matrix.scale(this.size.width, this.size.height)
                    path.transform(matrix)
                    drawPath(path, color)
                }
            }
        }
    }
}
