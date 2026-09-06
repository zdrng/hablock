package dev.hablock.app.ui.settings

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionTapCounterTest {
    @Test fun requiresFiveTapsAndStartsFreshAfterReset() {
        val counter = VersionTapCounter()
        repeat(4) { assertFalse(counter.tap(it * 100L)) }
        assertTrue(counter.tap(400L))
        assertFalse(counter.tap(500L))
    }

    @Test fun longPauseDiscardsPartialSequence() {
        val counter = VersionTapCounter()
        repeat(4) { assertFalse(counter.tap(it * 100L)) }
        assertFalse(counter.tap(1_301L))
        repeat(3) { assertFalse(counter.tap(1_400L + it * 100L)) }
        assertTrue(counter.tap(1_700L))
    }

    @Test fun exactlyOneSecondBetweenTapsIsAccepted() {
        val counter = VersionTapCounter()
        repeat(4) { assertFalse(counter.tap(it * 1_000L)) }
        assertTrue(counter.tap(4_000L))
    }
}
