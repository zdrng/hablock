package dev.hablock.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForegroundUsageAccumulatorTest {
    @Test
    fun `app still open across boundary counts only time after boundary`() {
        val usage = ForegroundUsageAccumulator(100, 200)
        usage.open("reader", 50)
        assertEquals(mapOf("reader" to 100L), usage.totals())
    }

    @Test
    fun `completed lookback sessions do not count toward current day`() {
        val usage = ForegroundUsageAccumulator(100, 200)
        usage.open("reader", 10)
        usage.close("reader", 50)
        assertTrue(usage.totals().isEmpty())
    }

    @Test
    fun `crossing session and later session are counted once`() {
        val usage = ForegroundUsageAccumulator(100, 200)
        usage.open("reader", 50)
        usage.close("reader", 120)
        usage.open("reader", 150)
        assertEquals(mapOf("reader" to 70L), usage.totals())
        assertEquals(mapOf("reader" to 70L), usage.totals())
    }

    @Test
    fun `screen off in lookback clears foreground state`() {
        val usage = ForegroundUsageAccumulator(100, 200)
        usage.open("reader", 10)
        usage.closeAll(50)
        assertTrue(usage.totals().isEmpty())
    }

    @Test
    fun `background after screen off does not count the same session twice`() {
        val usage = ForegroundUsageAccumulator(100, 200)
        usage.open("reader", 110)
        usage.closeAll(150)
        usage.close("reader", 160)
        assertEquals(mapOf("reader" to 40L), usage.totals())
    }
}
