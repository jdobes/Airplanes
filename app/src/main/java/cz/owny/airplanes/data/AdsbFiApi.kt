package cz.owny.airplanes.data

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface AdsbFiApi {
    @GET("v2/registration/{reg}")
    suspend fun getByRegistration(@Path("reg") reg: String): Response<AdsbFiResponse>
}
