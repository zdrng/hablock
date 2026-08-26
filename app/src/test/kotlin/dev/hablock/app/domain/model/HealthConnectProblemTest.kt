package dev.hablock.app.domain.model

import dev.hablock.app.domain.service.testBlock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthConnectProblemTest {

    private val stepsBlock = testBlock(id = "hc", conditions = listOf(Condition.Steps("s", 10_000.0)))
    private val usageBlock = testBlock(id = "usage", conditions = listOf(Condition.AppUsage("u", 30.0, "com.example", "Example")))

    @Test
    fun `no problem when no enabled block uses health connect`() {
        assertFalse(healthConnectProblem(listOf(usageBlock), HcAvailability.UNAVAILABLE, permissionsGranted = false))
        assertFalse(healthConnectProblem(listOf(stepsBlock.copy(enabled = false)), HcAvailability.UNAVAILABLE, permissionsGranted = false))
        assertFalse(healthConnectProblem(emptyList(), HcAvailability.UNAVAILABLE, permissionsGranted = false))
    }

    @Test
    fun `no problem when health connect works and permissions are granted`() {
        assertFalse(healthConnectProblem(listOf(stepsBlock), HcAvailability.FULL, permissionsGranted = true))
        assertFalse(healthConnectProblem(listOf(stepsBlock), HcAvailability.NO_MINDFULNESS, permissionsGranted = true))
    }

    @Test
    fun `problem when permissions are missing or health connect is absent`() {
        assertTrue(healthConnectProblem(listOf(stepsBlock), HcAvailability.FULL, permissionsGranted = false))
        assertTrue(healthConnectProblem(listOf(stepsBlock), HcAvailability.NEEDS_INSTALL, permissionsGranted = true))
        assertTrue(healthConnectProblem(listOf(stepsBlock), HcAvailability.UNAVAILABLE, permissionsGranted = true))
    }

    @Test
    fun `usesHealthConnect matches only health conditions`() {
        assertTrue(stepsBlock.usesHealthConnect())
        assertTrue(testBlock(conditions = listOf(Condition.Exercise("e", 30.0))).usesHealthConnect())
        assertTrue(testBlock(conditions = listOf(Condition.Meditation("m", 10.0))).usesHealthConnect())
        assertFalse(usageBlock.usesHealthConnect())
    }
}
