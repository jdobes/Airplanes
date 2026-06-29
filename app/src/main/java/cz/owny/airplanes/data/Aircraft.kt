package cz.owny.airplanes.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class Aircraft(
    val hex: String,
    val flight: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val track: Float? = null,
    val gs: Float? = null,
    val r: String? = null,
    val t: String? = null,
    val desc: String? = null,
    val alt_baro: JsonElement? = null,
    val origin: String? = null,
    val destination: String? = null,
    val airline: String? = null
) {
    fun hasPosition() = lat != null && lon != null

    val altBaroMeters: Int?
        get() = alt_baro?.let { el ->
            val p = el as? JsonPrimitive ?: return@let null
            val feet = if (p.isString) {
                if (p.content == "ground") 0 else p.content.toIntOrNull()
            } else {
                p.content.toIntOrNull()
            }
            feet?.times(0.3048)?.toInt()
        }
}

val apiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

data class Airline(
    val name: String? = null,
    val icao: String? = null,
    val iata: String? = null,
    val country: String? = null,
    val countryIso: String? = null
)

data class Airport(
    val iataCode: String? = null,
    val icaoCode: String? = null,
    val name: String? = null,
    val municipality: String? = null,
    val countryName: String? = null,
    val countryIso: String? = null
)

data class AircraftDetails(
    val photoUrl: String?,
    val photographer: String? = null,
    val photoLink: String? = null,
    val ownerCountryName: String?,
    val origin: Airport?,
    val destination: Airport?,
    val airline: Airline? = null,
    val callsign: String? = null,
    val desc: String? = null,
    val type: String? = null
)
