package cz.owny.airplanes.data

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import cz.owny.airplanes.Config
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

class AdsbdbRepository {

    private val api = Retrofit.Builder()
        .baseUrl("https://api.adsbdb.com/")
        .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AdsbdbApi::class.java)

    private val cache = mutableMapOf<String, AircraftDetails?>()

    fun pruneCache(validKeys: Set<String>) {
        val iterator = cache.iterator()
        while (iterator.hasNext()) {
            val (key, _) = iterator.next()
            if (key !in validKeys) {
                if (Config.DEBUG) Log.d("Airplanes", "Pruning adsbdb cache: $key")
                iterator.remove()
            }
        }
        if (Config.DEBUG) Log.d("Airplanes", "adsbdb cache size: ${cache.size}")
    }

    suspend fun lookup(reg: String?, callsign: String?): AircraftDetails? {
        if (reg.isNullOrBlank()) return null
        val cacheKey = "$reg|${callsign ?: ""}"
        if (cache.containsKey(cacheKey)) return cache[cacheKey]

        val details = fetch(reg, callsign)
        cache[cacheKey] = details
        return details
    }

    private suspend fun fetch(reg: String, callsign: String?): AircraftDetails? {
        if (Config.DEBUG) {
            Log.d("Airplanes", "Call adsbdb API, reg: '${reg}', callsign: '${callsign}'")
        }
        val response = try {
            api.getAircraft(reg, callsign)
        } catch (e: Exception) {
            Log.w("Airplanes", "adsbdb API call failed for reg='$reg'", e)
            return null
        }
        if (response.isSuccessful) {
            val body = response.body()
            if (body == null) {
                Log.w("Airplanes", "adsbdb API returned null body for reg='$reg'")
                return null
            }
            val aircraft = body.response.aircraft
            if (aircraft == null) {
                Log.w("Airplanes", "adsbdb: no aircraft data for reg='$reg'")
                return null
            }
            return AircraftDetails(
                photoUrl = null,
                ownerCountryName = aircraft.ownerCountryName,
                origin = body.response.flightroute?.origin,
                destination = body.response.flightroute?.destination,
                airline = body.response.flightroute?.airline,
                callsign = body.response.flightroute?.callsign
            )
        } else {
            Log.w("Airplanes", "adsbdb API returned ${response.code()} ${response.message()} for reg='$reg'")
        }
        return null
    }
}
