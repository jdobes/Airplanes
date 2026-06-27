package cz.owny.airplanes.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Aircraft(
    val hex: String,
    val flight: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val track: Float? = null,
    val gs: Float? = null,
    val r: String? = null,
    val t: String? = null
) {
    fun hasPosition() = lat != null && lon != null
}

@Serializable
data class AircraftResponse(
    @SerialName("msg") val messages: String? = null,
    @SerialName("ac") val aircraft: List<Aircraft> = emptyList(),
    val now: Long? = null
)

val apiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
