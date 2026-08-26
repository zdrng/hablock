package dev.hablock.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import dev.hablock.app.R

/**
 * Roboto Flex subset (digits + :.,/%+− only) — wide, confident clock numerals.
 * Everything else stays on the platform default so Material You feels native.
 */
@OptIn(ExperimentalTextApi::class)
val NumeralFamily = FontFamily(
    Font(
        R.font.roboto_flex_num,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.width(125f),
            FontVariation.Setting("GRAD", 50f),
        ),
    ),
)

/** Tabular figures are non-negotiable for countdowns: MM:SS must not jitter. */
fun numeralStyle(size: TextUnit): TextStyle = TextStyle(
    fontFamily = NumeralFamily,
    fontWeight = FontWeight.Bold,
    fontSize = size,
    lineHeight = size,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = "tnum",
)

