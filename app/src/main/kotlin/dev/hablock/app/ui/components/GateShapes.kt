package dev.hablock.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * The app's mascot: a gate that blooms. Cookie12Sided (screwed shut) morphs into
 * Flower (open) as conditions are met. Same object at 56 dp on cards, 200+ dp as hero.
 */
@Composable
fun MorphingGateBadge(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    rotation: Float = 0f,
) {
    val morph = remember { Morph(MaterialShapes.Cookie12Sided, MaterialShapes.Flower) }
    Canvas(modifier) {
        val path = morph.toPath(progress.coerceIn(0f, 1f)).asComposePath()
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        rotate(rotation) { drawPath(path, color) }
    }
}

/** A clip [Shape] frozen at one point of a [Morph] — lets any composable wear a morphing outline. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f)).asComposePath()
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * A badge that quietly shifts into a new expressive form every couple of seconds —
 * the blocked screen's restless lock.
 */
@Composable
fun ShapeShiftingBadge(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    content: @Composable () -> Unit = {},
) {
    val pool = remember {
        listOf(
            MaterialShapes.Cookie12Sided,
            MaterialShapes.Sunny,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Flower,
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Burst,
            MaterialShapes.Clover8Leaf,
            MaterialShapes.Cookie4Sided,
        ).shuffled()
    }
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2_000)
            progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            index = (index + 1) % pool.size
            progress.snapTo(0f)
        }
    }
    val morph = remember(index) { Morph(pool[index], pool[(index + 1) % pool.size]) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val path = morph.toPath(progress.value).asComposePath()
            val matrix = Matrix()
            matrix.scale(size.width, size.height)
            path.transform(matrix)
            drawPath(path, color)
        }
        content()
    }
}

/** A single static MaterialShapes polygon filled with [color]. */
@Composable
fun PolygonBadge(
    polygon: RoundedPolygon,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    rotation: Float = 0f,
) {
    val basePath = remember(polygon) { polygon.toPath().asComposePath() }
    Canvas(modifier) {
        val path = Path()
        path.addPath(basePath)
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        rotate(rotation) { drawPath(path, color) }
    }
}

/** Endless calm Cookie⟷Flower breathing — onboarding hero and empty states. */
@Composable
fun BreathingGateBadge(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "breathing")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "morph",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(48_000, easing = LinearEasing)),
        label = "spin",
    )
    MorphingGateBadge(progress = progress, modifier = modifier, color = color, rotation = rotation)
}

private class BurstParticle(index: Int, count: Int) {
    val angle = (index.toFloat() / count) * 2f * Math.PI.toFloat() + (index % 3) * 0.31f
    val speed = 0.9f + (index % 4) * 0.22f
    val size = 0.05f + (index % 3) * 0.02f
    val spin = if (index % 2 == 0) 260f else -200f
    val shape = index % 3
}

/**
 * One-shot shape confetti for the moments a gate opens. Re-fires whenever [trigger]
 * changes to a new non-null value. Draw it in an unclipped Box above the celebrant.
 */
@Composable
fun ShapeBurst(
    trigger: Any?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    secondary: Color = MaterialTheme.colorScheme.tertiary,
) {
    val particles = remember { List(14) { BurstParticle(it, 14) } }
    val shapePaths = remember {
        listOf(
            MaterialShapes.Clover4Leaf.toPath().asComposePath(),
            MaterialShapes.Cookie4Sided.toPath().asComposePath(),
            MaterialShapes.Circle.toPath().asComposePath(),
        )
    }
    val animation = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger != null) {
            animation.snapTo(0f)
            animation.animateTo(1f, tween(900))
        }
    }
    val t = animation.value
    if (t >= 1f) return
    Canvas(modifier) {
        val radius = size.minDimension * 0.5f
        particles.forEachIndexed { index, particle ->
            val travel = radius * (0.35f + particle.speed * t)
            val gravity = radius * 0.45f * t * t
            val x = center.x + cos(particle.angle) * travel
            val y = center.y + sin(particle.angle) * travel + gravity
            val particleSize = size.minDimension * particle.size * (1f - 0.5f * t)
            val path = Path()
            path.addPath(shapePaths[particle.shape])
            val matrix = Matrix()
            matrix.scale(particleSize, particleSize)
            path.transform(matrix)
            translate(x - particleSize / 2f, y - particleSize / 2f) {
                rotate(particle.spin * t, pivot = center) {
                    drawPath(path, if (index % 2 == 0) color else secondary, alpha = (1f - t))
                }
            }
        }
    }
}
