package dev.hablock.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Goal unit: minutes for AppUsage/Exercise/Meditation, count for Steps. */
@Serializable
sealed class Condition {
    abstract val id: String
    abstract val goal: Double

    @Serializable
    @SerialName("appUsage")
    data class AppUsage(
        override val id: String,
        override val goal: Double,
        val packageName: String,
        val appLabel: String,
    ) : Condition()

    @Serializable
    @SerialName("steps")
    data class Steps(
        override val id: String,
        override val goal: Double,
    ) : Condition()

    @Serializable
    @SerialName("exercise")
    data class Exercise(
        override val id: String,
        override val goal: Double,
    ) : Condition()

    @Serializable
    @SerialName("meditation")
    data class Meditation(
        override val id: String,
        override val goal: Double,
    ) : Condition()
}
