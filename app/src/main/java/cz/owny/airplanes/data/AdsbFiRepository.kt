package cz.owny.airplanes.data

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import cz.owny.airplanes.Config
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class AdsbFiRepository {

    private val client = OkHttpClient()

    private val api = Retrofit.Builder()
        .baseUrl("https://opendata.adsb.fi/api/")
        .client(client)
        .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AdsbFiApi::class.java)

    private val cache = mutableMapOf<String, CachedDesc?>()

    data class CachedDesc(
        val desc: String?,
        val type: String?
    )

    fun pruneCache(validRegs: Set<String>) {
        val iterator = cache.iterator()
        while (iterator.hasNext()) {
            val (key, _) = iterator.next()
            if (key !in validRegs) {
                if (Config.DEBUG) Log.d("Airplanes", "Pruning adsbfi cache: $key")
                iterator.remove()
            }
        }
        if (Config.DEBUG) Log.d("Airplanes", "adsbfi cache size: ${cache.size}")
    }

    suspend fun lookup(reg: String?): CachedDesc? {
        if (reg.isNullOrBlank()) return null
        if (cache.containsKey(reg)) return cache[reg]

        val desc = fetch(reg)
        cache[reg] = desc
        return desc
    }

    private suspend fun fetch(reg: String): CachedDesc? {
        if (Config.DEBUG) {
            Log.d("Airplanes", "Call adsbfi API, reg: '$reg'")
        }
        val response = try {
            api.getByRegistration(reg)
        } catch (e: Exception) {
            Log.w("Airplanes", "adsbfi API call failed for reg='$reg'", e)
            return null
        }
        if (response.isSuccessful) {
            val body = response.body()
            if (body == null) {
                Log.w("Airplanes", "adsbfi API returned null body for reg='$reg'")
                return null
            }
            val ac = body.ac.firstOrNull()
            if (ac == null) {
                Log.w("Airplanes", "adsbfi: no aircraft for reg='$reg'")
                return null
            }
            return CachedDesc(
                desc = ac.desc,
                type = ac.t
            )
        } else {
            Log.w("Airplanes", "adsbfi API returned ${response.code()} ${response.message()} for reg='$reg'")
        }
        return null
    }
}
