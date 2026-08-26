package dev.hablock.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class SerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val block = Block(
        id = "b1",
        name = "Social",
        blockedPackages = setOf("com.example.social", "com.example.video"),
        blockedLabels = mapOf("com.example.social" to "Social"),
        conditions = listOf(
            Condition.AppUsage(id = "usage", goal = 15.0, packageName = "com.example.reader", appLabel = "Reader"),
            Condition.Steps(id = "steps", goal = 10_000.0),
            Condition.Exercise(id = "workout", goal = 60.0),
            Condition.Meditation(id = "meditation", goal = 10.0),
        ),
        thresholdN = 2,
        incrementPct = 0.15f,
        enabled = true,
    )

    @Test
    fun `block with every condition type round trips`() {
        val encoded = json.encodeToString(Block.serializer(), block)
        assertEquals(block, json.decodeFromString(Block.serializer(), encoded))
    }

    @Test
    fun `block day state round trips including the active session`() {
        val state = BlockDayState(
            blockId = "b1",
            dayKey = "2026-08-23",
            requiredNow = mapOf("steps" to 11_300.0, "workout" to 67.0, "meditation" to 10.0),
            activeSession = Session(blockId = "b1", startedAtMillis = 1_770_000_000_000, endsAtMillis = 1_770_001_800_000),
            unlockCount = 3,
        )
        val encoded = json.encodeToString(BlockDayState.serializer(), state)
        assertEquals(state, json.decodeFromString(BlockDayState.serializer(), encoded))
    }

    @Test
    fun `unknown fields from a newer schema are ignored`() {
        val payload = """
            {"blockId":"b1","dayKey":"2026-08-23","requiredNow":{"steps":11300.0},
             "unlockCount":1,"futureField":"ignored"}
        """.trimIndent()
        val decoded = json.decodeFromString(BlockDayState.serializer(), payload)
        assertEquals("b1", decoded.blockId)
        assertEquals(1, decoded.unlockCount)
        assertEquals(11_300.0, decoded.requiredNow.getValue("steps"))
    }
}
