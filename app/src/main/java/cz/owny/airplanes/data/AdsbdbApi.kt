package cz.owny.airplanes.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AdsbdbApi {
    @GET("v0/aircraft/{reg}")
    suspend fun getAircraft(
        @Path("reg") reg: String,
        @Query("callsign") callsign: String? = null
    ): Response<AdsbdbEnvelope>
}
