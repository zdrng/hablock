package dev.hablock.app.domain.enforcement

import dev.hablock.app.domain.model.GateState
import dev.hablock.app.domain.model.OverlapPolicy
import dev.hablock.app.domain.service.testBlock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val PACKAGE = "com.example.social"
private val LOCKED = GateState.Locked(emptyList(), 1)
private val OPEN = GateState.Open(emptyList(), 1)
private val SESSION = GateState.SessionActive(emptyList(), Instant.parse("2026-08-23T10:00:00Z"))

class OverlapEnforcementTest {

    private val blocks = listOf(
        testBlock(id = "z-block", packages = setOf(PACKAGE)),
        testBlock(id = "a-block", packages = setOf(PACKAGE)),
    )

    @Test
    fun `all-blocks policy releases only when every matching block has a session`() {
        val partlyActive = resolveEnforcementPlan(
            blocks,
            mapOf("a-block" to SESSION, "z-block" to OPEN),
            OverlapPolicy.ALL_BLOCKS,
        )
        val fullyActive = resolveEnforcementPlan(
            blocks,
            mapOf("a-block" to SESSION, "z-block" to SESSION),
            OverlapPolicy.ALL_BLOCKS,
        )

        assertEquals("z-block", partlyActive.blockingBlockId(PACKAGE))
        assertNull(fullyActive.blockingBlockId(PACKAGE))
    }

    @Test
    fun `any-block policy releases when one matching block has a session`() {
        val plan = resolveEnforcementPlan(
            blocks,
            mapOf("a-block" to LOCKED, "z-block" to SESSION),
            OverlapPolicy.ANY_BLOCK,
        )

        assertNull(plan.blockingBlockId(PACKAGE))
    }

    @Test
    fun `any-block policy deterministically prefers an open unresolved block`() {
        val plan = resolveEnforcementPlan(
            blocks,
            mapOf("a-block" to LOCKED, "z-block" to OPEN),
            OverlapPolicy.ANY_BLOCK,
        )

        assertEquals("z-block", plan.blockingBlockId(PACKAGE))
    }

    @Test
    fun `unresolved selection is stable regardless of repository order`() {
        val states = mapOf("a-block" to LOCKED, "z-block" to LOCKED)

        assertEquals(
            "a-block",
            resolveEnforcementPlan(blocks, states, OverlapPolicy.ALL_BLOCKS).blockingBlockId(PACKAGE),
        )
        assertEquals(
            "a-block",
            resolveEnforcementPlan(blocks.reversed(), states, OverlapPolicy.ALL_BLOCKS).blockingBlockId(PACKAGE),
        )
    }

    @Test
    fun `disabled matching blocks do not participate`() {
        val withDisabledBlock = blocks + testBlock(id = "disabled", packages = setOf(PACKAGE), enabled = false)
        val plan = resolveEnforcementPlan(
            withDisabledBlock,
            mapOf("a-block" to SESSION, "z-block" to SESSION, "disabled" to LOCKED),
            OverlapPolicy.ALL_BLOCKS,
        )

        assertNull(plan.blockingBlockId(PACKAGE))
    }

    @Test
    fun `schedule-inactive matching blocks do not participate`() {
        val plan = resolveEnforcementPlan(
            blocks,
            mapOf("a-block" to GateState.Inactive(emptyList()), "z-block" to SESSION),
            OverlapPolicy.ALL_BLOCKS,
        )

        assertNull(plan.blockingBlockId(PACKAGE))
    }
}
