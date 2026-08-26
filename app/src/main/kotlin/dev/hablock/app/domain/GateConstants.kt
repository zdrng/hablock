package dev.hablock.app.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

object GateConstants {
    const val DAY_RESET_HOUR = 0
    val SESSION_DURATION: Duration = 30.minutes
    val RELINQUISH_COOLDOWN: Duration = 3.days
    const val INCREMENT_MIN = 0.10f
    const val INCREMENT_MAX = 0.50f
    const val INCREMENT_STEP = 0.05f

    const val ACTION_SESSION_EXPIRED = "dev.hablock.app.action.SESSION_EXPIRED"
    const val ACTION_DAY_RESET = "dev.hablock.app.action.DAY_RESET"
    const val ACTION_RELINQUISH_READY = "dev.hablock.app.action.RELINQUISH_READY"
    const val EXTRA_BLOCK_ID = "block_id"
    const val EXTRA_PACKAGE_NAME = "package_name"
}
