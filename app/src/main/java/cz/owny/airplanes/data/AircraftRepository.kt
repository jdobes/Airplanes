package cz.owny.airplanes.data

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import cz.owny.airplanes.Config
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.Locale
import java.util.concurrent.TimeUnit

class AircraftRepository {

    @Volatile
    private var cookie: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36")
            cookie?.let {
                requestBuilder.header("Cookie", it)
            }

            chain.proceed(requestBuilder.build())
        })
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("https://data-cloud.flightradar24.com/")
        .client(client)
        .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(Fr24Api::class.java)

    suspend fun getAircraft(north: Double, south: Double, west: Double, east: Double): List<Aircraft> {
        val bounds = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", north, south, west, east)
        if (Config.DEBUG) {
            Log.d("Airplanes", "Call FR24 API, bounds: '$bounds'")
        }
        val response = try {
            api.getAircraft(bounds)
        } catch (e: Exception) {
            Log.w("Airplanes", "FR24 API call failed for bounds='$bounds'", e)
            return emptyList()
        }
        if (response.isSuccessful) {
            val body = response.body()
            if (body == null) {
                Log.w("Airplanes", "FR24 API returned null body for bounds='$bounds'")
                return emptyList()
            }
            val aircraft = body.entries.mapNotNull { (key, value) ->
                if (key == "full_count" || key == "version") return@mapNotNull null
                if (Config.DEBUG) Log.d("Airplanes", key)
                val arr = value as? JsonArray ?: return@mapNotNull null
                parseAircraft(key, arr)
            }
            if (aircraft.isNotEmpty() && cookie == null) {
                val setCookie = response.headers()["Set-Cookie"]
                if (setCookie != null) {
                    val value = setCookie.substringBefore(";")
                    if (value.isNotBlank()) {
                        cookie = value
                        if (Config.DEBUG) {
                            Log.d("Airplanes", "FR24 cookie stored: $value")
                        }
                    }
                }
            }
            return aircraft
        } else {
            Log.w("Airplanes", "FR24 API returned ${response.code()} ${response.message()} for bounds='$bounds'")
        }
        return emptyList()
    }

    private fun parseAircraft(hex: String, arr: JsonArray): Aircraft? {
        val lat = arr.getOrNull(1)?.jsonPrimitive?.doubleOrNull
        val lon = arr.getOrNull(2)?.jsonPrimitive?.doubleOrNull
        if (lat == null || lon == null) return null

        return Aircraft(
            hex = hex,
            lat = lat,
            lon = lon,
            track = arr.getOrNull(3)?.jsonPrimitive?.floatOrNull,
            gs = arr.getOrNull(5)?.jsonPrimitive?.floatOrNull,
            t = arr.getOrNull(8)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
            r = arr.getOrNull(9)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
            alt_baro = arr.getOrNull(4),
            flight = arr.getOrNull(16)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                ?: arr.getOrNull(13)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
            origin = arr.getOrNull(11)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
            destination = arr.getOrNull(12)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() },
            airline = arr.getOrNull(18)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        )
    }
}
