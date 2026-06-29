package cz.owny.airplanes.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AdsbFiResponse(
    val ac: List<AdsbFiAircraft> = emptyList(),
    val msg: String? = null
)

@Serializable
data class AdsbFiAircraft(
    val desc: String? = null,
    val t: String? = null,
    val r: String? = null
)
