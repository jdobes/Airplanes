package cz.owny.airplanes.data

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface Fr24Api {
    @GET("zones/fcgi/feed.js")
    suspend fun getAircraft(
        @Query("bounds") bounds: String
    ): Response<JsonObject>
}
