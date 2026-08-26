package dev.hablock.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Fallback brand for devices without dynamic color: "Hablock Violet" (#7A5AF8 seed) + fixed lime.
val HablockLightScheme = lightColorScheme(
    primary = Color(0xFF5B3FD8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF180066),
    secondary = Color(0xFF605A72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6DEF9),
    onSecondaryContainer = Color(0xFF1C1830),
    tertiary = Color(0xFF7C5264),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E7),
    onTertiaryContainer = Color(0xFF301121),
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1C1B21),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1C1B21),
    surfaceVariant = Color(0xFFE5E0EC),
    onSurfaceVariant = Color(0xFF48454F),
    outline = Color(0xFF79767F),
    outlineVariant = Color(0xFFCAC5D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F1FA),
    surfaceContainer = Color(0xFFF1ECF5),
    surfaceContainerHigh = Color(0xFFECE6EF),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF313036),
    inverseOnSurface = Color(0xFFF4EFF7),
    inversePrimary = Color(0xFFC9BFFF),
)

val HablockDarkScheme = darkColorScheme(
    primary = Color(0xFFC9BFFF),
    onPrimary = Color(0xFF2C009E),
    primaryContainer = Color(0xFF4326C0),
    onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFFC9C2DC),
    onSecondary = Color(0xFF312C42),
    secondaryContainer = Color(0xFF484259),
    onSecondaryContainer = Color(0xFFE6DEF9),
    tertiary = Color(0xFFEEB8CE),
    onTertiary = Color(0xFF4A2537),
    tertiaryContainer = Color(0xFF633B4D),
    onTertiaryContainer = Color(0xFFFFD8E7),
    background = Color(0xFF141318),
    onBackground = Color(0xFFE6E1E9),
    surface = Color(0xFF141318),
    onSurface = Color(0xFFE6E1E9),
    surfaceVariant = Color(0xFF48454F),
    onSurfaceVariant = Color(0xFFCAC5D0),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF48454F),
    surfaceContainerLowest = Color(0xFF0E0D13),
    surfaceContainerLow = Color(0xFF1C1B21),
    surfaceContainer = Color(0xFF201F25),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE6E1E9),
    inverseOnSurface = Color(0xFF313036),
    inversePrimary = Color(0xFF5B3FD8),
)

/**
 * The one signal color outside the scheme: "met" lime. Deliberately not derived from
 * dynamic color so an earned condition reads the same on every wallpaper.
 */
@Immutable
data class HablockAccents(
    val met: Color,
    val onMet: Color,
    val metContainer: Color,
    val onMetContainer: Color,
)

val LightAccents = HablockAccents(
    met = Color(0xFF3F6900),
    onMet = Color(0xFFFFFFFF),
    metContainer = Color(0xFFC1F16B),
    onMetContainer = Color(0xFF0F2000),
)

val DarkAccents = HablockAccents(
    met = Color(0xFFA6D96A),
    onMet = Color(0xFF1B2E00),
    metContainer = Color(0xFF2E4F00),
    onMetContainer = Color(0xFFC1F16B),
)

val LocalHablockAccents = staticCompositionLocalOf { LightAccents }
