package cz.owny.airplanes.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json

object AirlineDatabase {

    private val json = Json { ignoreUnknownKeys = true }
    private var airlines: Map<String, String> = emptyMap()

    fun init(context: Context) {
        if (airlines.isNotEmpty()) return
        try {
            val text = context.assets.open("airlines.json").bufferedReader().use { it.readText() }
            airlines = json.decodeFromString<Map<String, String>>(text)
            Log.d("Airplanes", "AirlineDatabase loaded ${airlines.size} airlines")
        } catch (e: Exception) {
            Log.e("Airplanes", "Failed to load airlines.json", e)
        }
    }

    fun lookup(icaoCode: String): String? = airlines[icaoCode.uppercase()]
}
