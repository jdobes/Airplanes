package cz.owny.airplanes.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

class AircraftRepository {

    private val api = Retrofit.Builder()
        //.baseUrl("https://api.airplanes.live/")
        .baseUrl("https://opendata.adsb.fi/")
        .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AirplanesApi::class.java)

    suspend fun getAircraft(lat: Double, lon: Double, radiusNm: Int): List<Aircraft> {
        return api.getAircraft(lat, lon, radiusNm).aircraft.filter { it.hasPosition() }
    }
}
