package dev.hablock.app.ui.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hablock.app.R
import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.model.EmergencyPill
import dev.hablock.app.ui.format.formatCountdownPrecise
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun EmergencyUnlockPills(
    pills: List<EmergencyPill>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        pills.forEach { pill ->
            EmergencyPillSurface(
                pill = pill,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EmergencyPillSurface(
    pill: EmergencyPill,
    modifier: Modifier = Modifier,
) {
    val tick by rememberTicker(1_000L)

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

    Surface(
        modifier = modifier.height(72.dp),
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
                    stringResource(R.string.emergency_pill_available)
                } else if (refillProgress < 1f && remaining != null) {
                    formatCountdownPrecise(remaining)
                } else {
                    stringResource(R.string.emergency_pill_ready)
                },
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
