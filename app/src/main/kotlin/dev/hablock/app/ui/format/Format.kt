package dev.hablock.app.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.hablock.app.R
import dev.hablock.app.domain.model.Condition
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration

@Composable
fun Condition.displayLabel(): String = when (this) {
    is Condition.AppUsage -> appLabel
    is Condition.Steps -> stringResource(R.string.condition_steps)
    is Condition.Exercise -> stringResource(R.string.condition_workout)
    is Condition.Meditation -> stringResource(R.string.condition_meditation)
}

@Composable
fun Condition.formatValue(value: Double): String = when (this) {
    is Condition.Steps -> groupedInt(value)
    else -> stringResource(R.string.format_minutes_value, value.roundToInt())
}

@Composable
fun Condition.formatProgress(current: Double, required: Double): String =
    stringResource(R.string.format_progress, formatValue(current), formatValue(required))

fun groupedInt(value: Double): String = String.format(Locale.getDefault(), "%,d", value.roundToInt())

fun formatMinutes(minutes: Double): String {
    val total = minutes.roundToInt()
    val hours = total / 60
    val rest = total % 60
    return if (hours > 0) "${hours}h ${rest.toString().padStart(2, '0')}m" else "${rest}m"
}

fun formatCountdown(remaining: Duration): String = remaining.toComponents { days, hours, minutes, _, _ ->
    when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

fun formatCountdownPrecise(remaining: Duration): String = remaining.toComponents { days, hours, minutes, seconds, _ ->
    when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m ${seconds.toString().padStart(2, '0')}s"
    }
}

fun formatDayHeader(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = instant.atZone(zone)
    val day = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return "$day ${date.dayOfMonth}"
}

fun formatWeekday(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    instant.atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

fun formatClock(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = instant.atZone(zone)
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

fun formatDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = instant.atZone(zone)
    val month = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return "$month ${date.dayOfMonth}"
}

fun formatPercent(fraction: Float): String = "${(fraction * 100).roundToInt()}%"
