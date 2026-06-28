package cz.owny.airplanes.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AdsbdbEnvelope(val response: AdsbdbResponse)

@Serializable
data class AdsbdbResponse(
    val aircraft: AdsbdbAircraft? = null,
    val flightroute: Flightroute? = null
)

@Serializable
data class AdsbdbAircraft(
    val type: String? = null,
    @SerialName("icao_type") val icaoType: String? = null,
    val manufacturer: String? = null,
    @SerialName("mode_s") val modeS: String? = null,
    val registration: String? = null,
    @SerialName("registered_owner_country_name") val ownerCountryName: String? = null,
    @SerialName("registered_owner_country_iso_name") val ownerCountryIso: String? = null,
    @SerialName("registered_owner") val owner: String? = null,
    @SerialName("url_photo") val urlPhoto: String? = null,
    @SerialName("url_photo_thumbnail") val urlPhotoThumbnail: String? = null
)

@Serializable
data class Flightroute(
    val callsign: String? = null,
    val airline: Airline? = null,
    val origin: Airport? = null,
    val destination: Airport? = null
)

@Serializable
data class Airline(
    val name: String? = null,
    val icao: String? = null,
    val iata: String? = null,
    val country: String? = null,
    @SerialName("country_iso") val countryIso: String? = null
)

@Serializable
data class Airport(
    @SerialName("iata_code") val iataCode: String? = null,
    @SerialName("icao_code") val icaoCode: String? = null,
    val name: String? = null,
    val municipality: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    @SerialName("country_iso_name") val countryIso: String? = null
)

data class AircraftDetails(
    val photoUrl: String?,
    val ownerCountryName: String?,
    val origin: Airport?,
    val destination: Airport?,
    val airline: Airline? = null,
    val callsign: String? = null
)
