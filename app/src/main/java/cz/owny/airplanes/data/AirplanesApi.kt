package cz.owny.airplanes.data

import retrofit2.http.GET
import retrofit2.http.Path

interface AirplanesApi {
    @GET("v2/point/{lat}/{lon}/{radius}")
    suspend fun getAircraft(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Path("radius") radius: Int
    ): AircraftResponse
}
