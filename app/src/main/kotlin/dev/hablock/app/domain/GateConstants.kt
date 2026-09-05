package dev.hablock.app.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object GateConstants {
    const val DAY_RESET_HOUR = 0
    val SESSION_DURATION: Duration = 30.minutes
    val RELINQUISH_COOLDOWN: Duration = 3.days
    const val INCREMENT_MIN = 0.10f
    const val INCREMENT_MAX = 0.50f
    const val INCREMENT_STEP = 0.05f

    const val DURATION_MIN = 5
    const val DURATION_MAX = 180
    const val DURATION_STEP = 5
    const val DURATION_DEFAULT = 30

    const val EMERGENCY_UNLOCKS_MAX = 2
    val EMERGENCY_REFILL: Duration = 30.days
    val EMERGENCY_HOLD: Duration = 10.seconds

    const val ACTION_SESSION_EXPIRED = "dev.hablock.app.action.SESSION_EXPIRED"
    const val ACTION_DAY_RESET = "dev.hablock.app.action.DAY_RESET"
    const val ACTION_RELINQUISH_READY = "dev.hablock.app.action.RELINQUISH_READY"
    const val ACTION_RELOCK = "dev.hablock.app.action.RELOCK"
    const val EXTRA_BLOCK_ID = "block_id"
    const val EXTRA_PACKAGE_NAME = "package_name"
}
