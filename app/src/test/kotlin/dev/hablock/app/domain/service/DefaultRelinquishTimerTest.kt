package dev.hablock.app.domain.service

import dev.hablock.app.domain.GateConstants
import dev.hablock.app.domain.model.RelinquishState
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class DefaultRelinquishTimerTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val now: Instant = Instant.parse("2026-08-23T09:00:00Z")
    private val clock = MutableClock(now, zone)
    private val dayClock = DayClock(clock, zone)
    private val deviceOwner = FakeDeviceOwnerController()
    private val alarmScheduler = FakeAlarmScheduler()
    private val blockRepository = FakeBlockRepository(
        listOf(
            testBlock(id = "b1", packages = setOf("com.example.social", "com.example.video")),
            testBlock(id = "b2", packages = setOf("com.example.news")),
        ),
    )

    private fun newTimer(settings: FakeSettingsRepository) =
        DefaultRelinquishTimer(settings, deviceOwner, dayClock, blockRepository, alarmScheduler)

    @Test
    fun `no deadline is idle`() = runTest {
        assertEquals(RelinquishState.Idle, newTimer(FakeSettingsRepository()).state.first())
    }

    @Test
    fun `a future deadline counts down`() = runTest {
        val deadline = now.plusSeconds(3600)
        val state = newTimer(FakeSettingsRepository(deadline.toEpochMilli())).state.first()
        assertIs<RelinquishState.Counting>(state)
        assertEquals(deadline, state.deadline)
    }

    @Test
    fun `a reached deadline is ready`() = runTest {
        val settings = FakeSettingsRepository(now.minusSeconds(1).toEpochMilli())
        assertEquals(RelinquishState.Ready, newTimer(settings).state.first())
    }

    @Test
    fun `start sets the cooldown deadline and cancel clears it`() = runTest {
        val settings = FakeSettingsRepository()
        val timer = newTimer(settings)

        timer.start()
        val deadline = now.plusMillis(GateConstants.RELINQUISH_COOLDOWN.inWholeMilliseconds)
        assertEquals(deadline.toEpochMilli(), settings.deadline())
        assertEquals(listOf(deadline), alarmScheduler.relinquishReady)

        timer.cancel()
        assertNull(settings.deadline())
        assertEquals(1, alarmScheduler.cancelRelinquishReadyCount)
    }

    @Test
    fun `confirm does nothing before the deadline`() = runTest {
        val settings = FakeSettingsRepository()
        val timer = newTimer(settings)
        timer.start()

        timer.confirmRelinquish()

        assertTrue(deviceOwner.calls.isEmpty())
        assertEquals(0, deviceOwner.relinquishCount)
        assertEquals(now.plusMillis(GateConstants.RELINQUISH_COOLDOWN.inWholeMilliseconds).toEpochMilli(), settings.deadline())
    }

    @Test
    fun `confirm relinquishes once the deadline has passed`() = runTest {
        val settings = FakeSettingsRepository()
        val timer = newTimer(settings)
        timer.start()
        clock.advance(GateConstants.RELINQUISH_COOLDOWN.inWholeMilliseconds)

        timer.confirmRelinquish()

        assertEquals(1, deviceOwner.relinquishCount)
        assertNull(settings.deadline())
    }

    @Test
    fun `confirm unsuspends every blocked package before relinquishing`() = runTest {
        val settings = FakeSettingsRepository()
        val timer = newTimer(settings)
        timer.start()
        clock.advance(GateConstants.RELINQUISH_COOLDOWN.inWholeMilliseconds)

        timer.confirmRelinquish()

        assertEquals(listOf("setPackagesSuspended", "relinquishOwnership"), deviceOwner.calls)
        assertEquals(
            listOf(setOf("com.example.social", "com.example.video", "com.example.news") to false),
            deviceOwner.suspensions,
        )
        assertEquals(1, alarmScheduler.cancelRelinquishReadyCount)
    }

    @Test
    fun `confirm without a pending deadline is a no-op`() = runTest {
        newTimer(FakeSettingsRepository()).confirmRelinquish()
        assertTrue(deviceOwner.calls.isEmpty())
        assertEquals(0, deviceOwner.relinquishCount)
    }
}
