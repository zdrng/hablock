package dev.hablock.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring

enum class GroupPosition { Single, First, Middle, Last }

fun groupPositionOf(index: Int, count: Int): GroupPosition = when {
    count <= 1 -> GroupPosition.Single
    index == 0 -> GroupPosition.First
    index == count - 1 -> GroupPosition.Last
    else -> GroupPosition.Middle
}

private fun GroupPosition.shape(outer: Dp = 22.dp, inner: Dp = 6.dp): RoundedCornerShape = when (this) {
    GroupPosition.Single -> RoundedCornerShape(outer)
    GroupPosition.First -> RoundedCornerShape(outer, outer, inner, inner)
    GroupPosition.Middle -> RoundedCornerShape(inner)
    GroupPosition.Last -> RoundedCornerShape(inner, inner, outer, outer)
}

/** M3E grouped list item: 2 dp gaps, per-position corner rounding, gentle press shrink. */
@Composable
fun GroupedListItem(
    position: GroupPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    title: String? = null,
    supporting: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(stiffness = 900f),
        label = "press",
    )
    val body: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) {
                    Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                }
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content?.invoke()
            }
            trailing?.invoke()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().scale(pressScale),
            shape = position.shape(),
            color = containerColor,
            contentColor = contentColor,
            interactionSource = interaction,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = position.shape(),
            color = containerColor,
            contentColor = contentColor,
            content = body,
        )
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 18.dp, top = 26.dp, bottom = 10.dp),
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Small tonal pill with a clover-leaf leading dot: met / sealed / paused states. */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    dot: Boolean = true,
) {
    Surface(modifier, shape = RoundedCornerShape(50), color = containerColor, contentColor = contentColor) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot) {
                PolygonBadge(MaterialShapes.Clover4Leaf, Modifier.size(10.dp), color = contentColor)
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
