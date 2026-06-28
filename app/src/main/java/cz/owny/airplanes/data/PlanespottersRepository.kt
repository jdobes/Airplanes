package cz.owny.airplanes.data

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import cz.owny.airplanes.Config
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class PlanespottersRepository {

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "AirplanesApp/1.0 (airplanes@owny.cz)")
                    .build()
            )
        }
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("https://api.planespotters.net/")
        .client(client)
        .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PlanespottersApi::class.java)

    private val cache = mutableMapOf<String, CachedPhoto?>()

    data class CachedPhoto(
        val photoUrl: String?,
        val photographer: String?,
        val photoLink: String?
    )

    fun pruneCache(validRegs: Set<String>) {
        val iterator = cache.iterator()
        while (iterator.hasNext()) {
            val (key, _) = iterator.next()
            if (key !in validRegs) {
                if (Config.DEBUG) Log.d("Airplanes", "Pruning planespotters cache: $key")
                iterator.remove()
            }
        }
        if (Config.DEBUG) Log.d("Airplanes", "planespotters cache size: ${cache.size}")
    }

    suspend fun lookup(reg: String?): CachedPhoto? {
        if (reg.isNullOrBlank()) return null
        if (cache.containsKey(reg)) return cache[reg]

        val photo = fetch(reg)
        cache[reg] = photo
        return photo
    }

    private suspend fun fetch(reg: String): CachedPhoto? {
        if (Config.DEBUG) {
            Log.d("Airplanes", "Call planespotters API, reg: '$reg'")
        }
        val response = try {
            api.getPhoto(reg)
        } catch (e: Exception) {
            Log.w("Airplanes", "planespotters API call failed for reg='$reg'", e)
            return null
        }
        if (response.isSuccessful) {
            val body = response.body()
            if (body == null) {
                Log.w("Airplanes", "planespotters API returned null body for reg='$reg'")
                return null
            }
            val photo = body.photos.firstOrNull()
            if (photo == null) {
                Log.w("Airplanes", "planespotters: no photo for reg='$reg'")
                return null
            }
            return CachedPhoto(
                photoUrl = photo.thumbnailLarge?.src ?: photo.thumbnail?.src,
                photographer = photo.photographer,
                photoLink = photo.link
            )
        } else {
            Log.w("Airplanes", "planespotters API returned ${response.code()} ${response.message()} for reg='$reg'")
        }
        return null
    }
}