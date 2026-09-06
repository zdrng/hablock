package dev.hablock.app.ui.components

import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hablock.app.R
import dev.hablock.app.domain.GateConstants
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

/** Progress stays above the touch target so a finger never covers the countdown. */
@Composable
fun EmergencyUnlockControl(onUnlock: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var accessibleHold by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose {
            resumed = false
            accessibleHold = false
            holding = false
            progress = 0f
        }
    }
    val unlock by rememberUpdatedState(onUnlock)
    val haptic = LocalHapticFeedback.current
    val seconds = ceil((1f - progress) * GateConstants.EMERGENCY_HOLD.inWholeSeconds).toInt()
    val label = stringResource(when {
        accessibleHold && progress >= 1f -> R.string.blocked_unlock_confirm
        progress >= 1f -> R.string.emergency_release
        else -> R.string.emergency_unlock_hold
    })
    LaunchedEffect(accessibleHold) {
        if (accessibleHold) {
            holding = true
            val start = SystemClock.elapsedRealtime()
            try {
                while (progress < 1f) {
                    progress = ((SystemClock.elapsedRealtime() - start).toFloat() /
                        GateConstants.EMERGENCY_HOLD.inWholeMilliseconds).coerceIn(0f, 1f)
                    delay(16)
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } finally {
                if (progress < 1f) { holding = false; progress = 0f }
            }
        }
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeWidth = 12.dp,
            )
            Text(
                stringResource(R.string.emergency_seconds, seconds),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            stringResource(when {
                accessibleHold -> R.string.emergency_accessible_hint
                holding -> R.string.emergency_release_hint
                else -> R.string.emergency_hold_hint
            }),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Surface(
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
                role = Role.Button
                // Accessible alternative remains a deliberate long-click, never a plain tap.
                onLongClick(label = label) {
                    if (resumed && progress >= 1f && !submitted) {
                        submitted = true
                        unlock()
                    } else if (resumed && !holding) {
                        accessibleHold = true
                    }
                    true
                }
            }.pointerInput(resumed) {
                detectTapGestures(onPress = {
                    if (resumed && !submitted && !accessibleHold) coroutineScope {
                        holding = true
                        progress = 0f
                        val start = SystemClock.elapsedRealtime()
                        val timer = launch {
                            while (progress < 1f) {
                                progress = ((SystemClock.elapsedRealtime() - start).toFloat() /
                                    GateConstants.EMERGENCY_HOLD.inWholeMilliseconds).coerceIn(0f, 1f)
                                if (progress >= 1f) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                else delay(16)
                            }
                        }
                        try {
                            if (tryAwaitRelease() && progress >= 1f && !submitted) {
                                submitted = true
                                unlock()
                            }
                        } finally {
                            timer.cancel()
                            holding = false
                            progress = 0f
                        }
                    }
                })
            },
        ) {
            Text(label, Modifier.padding(24.dp), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}
