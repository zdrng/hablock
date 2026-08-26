package dev.hablock.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import dev.hablock.app.ui.components.HablockIcons
import dev.hablock.app.ui.components.MorphShape
import dev.hablock.app.ui.home.HomeScreen
import dev.hablock.app.ui.screentime.ScreenTimeScreen
import dev.hablock.app.ui.settings.SettingsScreen

enum class Destination(val label: String) {
    Blocks("Blocks"),
    ScreenTime("Time"),
    Settings("Settings"),
}

private val Destination.icon: ImageVector
    get() = when (this) {
        Destination.Blocks -> Icons.Rounded.Lock
        Destination.ScreenTime -> HablockIcons.Clock
        Destination.Settings -> Icons.Rounded.Settings
    }

@Composable
fun AppNav() {
    var destination by rememberSaveable { mutableStateOf(Destination.Blocks) }
    var addRequested by rememberSaveable { mutableStateOf(0) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (destination) {
                Destination.Blocks -> HomeScreen(addRequest = addRequested, onAddHandled = { addRequested = 0 })
                Destination.ScreenTime -> ScreenTimeScreen()
                Destination.Settings -> SettingsScreen()
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .offset(y = -FloatingToolbarDefaults.ScreenOffset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                ) {
                    Destination.entries.forEach { entry ->
                        val selected = entry == destination
                        ToggleButton(
                            checked = selected,
                            onCheckedChange = { destination = entry },
                            modifier = Modifier.padding(horizontal = 4.dp),
                            shapes = ToggleButtonDefaults.shapes(),
                        ) {
                            Icon(entry.icon, contentDescription = entry.label)
                            AnimatedVisibility(
                                visible = selected,
                                enter = expandHorizontally() + fadeIn(),
                                exit = shrinkHorizontally() + fadeOut(),
                            ) {
                                Text(entry.label, Modifier.padding(start = 8.dp), maxLines = 1)
                            }
                        }
                    }
                }
                MorphingFabReveal(
                    visible = destination == Destination.Blocks,
                    onClick = { addRequested++ },
                )
            }
        }
    }
}

private val fabRevealShapes = listOf(
    { MaterialShapes.Burst },
    { MaterialShapes.Sunny },
    { MaterialShapes.Clover8Leaf },
    { MaterialShapes.Cookie9Sided },
    { MaterialShapes.Flower },
    { MaterialShapes.Cookie4Sided },
)

/**
 * The add button doesn't slide in — it grows out of a random expressive form and
 * settles into a circle. It stays composed at zero width when hidden and its slot
 * (gap included) animates continuously, so the centered bar never jumps sideways.
 */
@Composable
private fun MorphingFabReveal(visible: Boolean, onClick: () -> Unit) {
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    var seed by remember { mutableStateOf(0) }
    LaunchedEffect(visible) {
        if (visible) {
            if (progress.value < 0.05f) seed = fabRevealShapes.indices.random()
            progress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
        } else {
            progress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
        }
    }
    val t = progress.value
    val morph = remember(seed) { Morph(fabRevealShapes[seed](), MaterialShapes.Circle) }
    Box(
        Modifier.padding(start = 8.dp * t).width(56.dp * t).height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .requiredSize(56.dp)
                .graphicsLayer {
                    scaleX = t
                    scaleY = t
                }
                .clip(MorphShape(morph, t)),
        ) {
            FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onClick) {
                Icon(Icons.Rounded.Add, contentDescription = "New block")
            }
        }
    }
}
