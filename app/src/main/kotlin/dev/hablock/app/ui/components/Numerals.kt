package dev.hablock.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

/**
 * A numeral string where only the digit that changed rolls vertically.
 * Pair with [dev.hablock.app.ui.theme.numeralStyle]'s tabular figures so nothing reflows.
 */
@Composable
fun BigNumerals(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    var previousText by remember { mutableStateOf(text) }
    val increasing = remember(text) {
        (digitsOf(text) >= digitsOf(previousText)).also { previousText = text }
    }

    Row(modifier) {
        val lastIndex = text.lastIndex
        text.forEachIndexed { index, char ->
            // Key by distance from the right, not left-to-right index, so a digit or grouping
            // comma keeps its identity when a leading character appears/disappears
            // (e.g. 9,500 -> 10,000) instead of comparing against an unrelated character.
            key(lastIndex - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        if (increasing) {
                            (slideInVertically { it / 2 } + fadeIn()) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut())
                        } else {
                            (slideInVertically { -it / 2 } + fadeIn()) togetherWith
                                (slideOutVertically { it / 2 } + fadeOut())
                        }
                    },
                    label = "digit",
                ) { c ->
                    Text(c.toString(), style = style, color = color)
                }
            }
        }
    }
}

private fun digitsOf(text: String): Long = text.filter(Char::isDigit).ifEmpty { "0" }.toLong()

/** Re-emits every [intervalMillis] while the caller is composed — drives live countdowns. */
@Composable
fun rememberTicker(intervalMillis: Long = 1_000L): State<Long> =
    produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(intervalMillis)
            value = System.currentTimeMillis()
        }
    }
