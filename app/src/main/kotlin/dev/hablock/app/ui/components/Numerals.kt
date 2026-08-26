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
import androidx.compose.runtime.produceState
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
    Row(modifier) {
        text.forEachIndexed { index, char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                },
                label = "digit$index",
            ) { c ->
                Text(c.toString(), style = style, color = color)
            }
        }
    }
}

/** Re-emits every [intervalMillis] while the caller is composed — drives live countdowns. */
@Composable
fun rememberTicker(intervalMillis: Long = 1_000L): State<Long> =
    produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(intervalMillis)
            value = System.currentTimeMillis()
        }
    }
