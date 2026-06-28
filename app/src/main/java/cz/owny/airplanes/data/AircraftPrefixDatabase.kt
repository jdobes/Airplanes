package cz.owny.airplanes.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json

object AircraftPrefixDatabase {

    private val json = Json { ignoreUnknownKeys = true }
    private var prefixes: Map<String, String> = emptyMap()
    private var sortedPrefixes: List<Pair<String, String>> = emptyList()

    fun init(context: Context) {
        if (prefixes.isNotEmpty()) return
        try {
            val text = context.assets.open("aircraft_prefixes.json").bufferedReader().use { it.readText() }
            prefixes = json.decodeFromString<Map<String, String>>(text)
            sortedPrefixes = prefixes.entries.map { it.key to it.value }.sortedByDescending { it.first.length }
            Log.d("Airplanes", "AircraftPrefixDatabase loaded ${prefixes.size} prefixes")
        } catch (e: Exception) {
            Log.e("Airplanes", "Failed to load aircraft_prefixes.json", e)
        }
    }

    fun lookup(registration: String): String? {
        val upper = registration.uppercase()
        for ((prefix, country) in sortedPrefixes) {
            if (upper.startsWith(prefix.uppercase())) {
                return country
            }
        }
        return null
    }
}
