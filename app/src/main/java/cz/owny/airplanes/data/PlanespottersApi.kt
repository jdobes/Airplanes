package cz.owny.airplanes.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface PlanespottersApi {
    @GET("pub/photos/reg/{reg}")
    suspend fun getPhoto(@Path("reg") reg: String): Response<PhotosEnvelope>
}