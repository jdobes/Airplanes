package cz.owny.airplanes.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AirportJsonEntry(
    val city: String,
    val country_code: String
)

object AirportDatabase {

    private val json = Json { ignoreUnknownKeys = true }
    private var airports: Map<String, AirportJsonEntry> = emptyMap()

    fun init(context: Context) {
        if (airports.isNotEmpty()) return
        try {
            val text = context.assets.open("airports.json").bufferedReader().use { it.readText() }
            airports = json.decodeFromString<Map<String, AirportJsonEntry>>(text)
            Log.d("Airplanes", "AirportDatabase loaded ${airports.size} airports")
        } catch (e: Exception) {
            Log.e("Airplanes", "Failed to load airports.json", e)
        }
    }

    fun lookup(iataCode: String): AirportJsonEntry? = airports[iataCode.uppercase()]
}
