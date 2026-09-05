package dev.hablock.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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
    fun `block with custom unlock duration round trips`() {
        val custom = block.copy(unlockDurationMinutes = 45)
        val encoded = json.encodeToString(Block.serializer(), custom)
        assertEquals(custom, json.decodeFromString(Block.serializer(), encoded))
    }

    @Test
    fun `block without unlock duration decodes to default`() {
        val encoded = json.encodeToString(Block.serializer(), block)
        val without = encoded.replace(""",\"unlockDurationMinutes\":\d+""".toRegex(), "")
        val decoded = json.decodeFromString(Block.serializer(), without)
        assertEquals(30, decoded.unlockDurationMinutes)
    }

    @Test
    fun `block with blockedUntil round trips`() {
        val locked = block.copy(blockedUntil = 1_700_000_000_000L)
        val encoded = json.encodeToString(Block.serializer(), locked)
        assertEquals(locked, json.decodeFromString(Block.serializer(), encoded))
    }

    @Test
    fun `block without blockedUntil decodes to null`() {
        val encoded = json.encodeToString(Block.serializer(), block)
        val without = encoded.replace(""",\"blockedUntil\":\d+""".toRegex(), "")
        val decoded = json.decodeFromString(Block.serializer(), without)
        assertEquals(null, decoded.blockedUntil)
    }

    @Test
    fun `block with duration lock type round trips`() {
        val locked = block.copy(lockType = LockType.DURATION, blockedUntil = 1_700_000_000_000L)
        val encoded = json.encodeToString(Block.serializer(), locked)
        assertEquals(locked, json.decodeFromString(Block.serializer(), encoded))
    }

    @Test
    fun `block with password lock type round trips`() {
        val locked = block.copy(
            lockType = LockType.PASSWORD,
            blockedUntil = Long.MAX_VALUE,
            lockPasswordHash = hashPasscode("123456"),
        )
        val encoded = json.encodeToString(Block.serializer(), locked)
        assertEquals(locked, json.decodeFromString(Block.serializer(), encoded))
    }

    @Test
    fun `block without lock type decodes to null`() {
        val encoded = json.encodeToString(Block.serializer(), block)
        val without = encoded.replace(""",\"lockType\":\"[^\"]+\"""".toRegex(), "")
        val decoded = json.decodeFromString(Block.serializer(), without)
        assertEquals(null, decoded.lockType)
    }

    @Test
    fun `passcode hash is deterministic`() {
        assertEquals(hashPasscode("123456"), hashPasscode("123456"))
    }

    @Test
    fun `passcode hash differs for different codes`() {
        assertNotEquals(hashPasscode("123456"), hashPasscode("654321"))
    }

    @Test
    fun `verify passcode matches stored hash`() {
        val hash = hashPasscode("000000")
        assertTrue(verifyPasscode("000000", hash))
        assertFalse(verifyPasscode("111111", hash))
        assertFalse(verifyPasscode("000000", null))
    }

    @Test
    fun `password lock is always changes locked`() {
        val locked = block.copy(lockType = LockType.PASSWORD, blockedUntil = Long.MAX_VALUE)
        assertTrue(locked.isChangesLocked(0L))
    }

    @Test
    fun `duration lock is changes locked until expiry`() {
        val locked = block.copy(lockType = LockType.DURATION, blockedUntil = 1_000L)
        assertTrue(locked.isChangesLocked(500L))
        assertFalse(locked.isChangesLocked(1_500L))
    }

    @Test
    fun `null lock type is not changes locked`() {
        assertFalse(block.isChangesLocked())
    }

    @Test
    fun `emergency unlock state round trips`() {
        val state = EmergencyUnlockState(
            pills = listOf(
                EmergencyPill(available = false, refillAt = 1_700_000_000_000L),
                EmergencyPill(available = true, refillAt = null),
            ),
        )
        val encoded = json.encodeToString(EmergencyUnlockState.serializer(), state)
        assertEquals(state, json.decodeFromString(EmergencyUnlockState.serializer(), encoded))
    }

    @Test
    fun `emergency unlock consume reduces available count`() {
        val state = EmergencyUnlockState()
        val consumed = state.consume(1_000L, 30L * 86_400_000L)
        assertNotNull(consumed)
        assertEquals(1, consumed!!.availableCount)
    }

    @Test
    fun `emergency unlock refillExpired restores available pills`() {
        val state = EmergencyUnlockState(
            pills = listOf(
                EmergencyPill(available = false, refillAt = 500L),
                EmergencyPill(available = true, refillAt = null),
            ),
        )
        val refilled = state.refillExpired(1_000L)
        assertEquals(2, refilled.availableCount)
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
