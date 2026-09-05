package dev.hablock.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LockType {
    @SerialName("duration") DURATION,
    @SerialName("password") PASSWORD,
}
