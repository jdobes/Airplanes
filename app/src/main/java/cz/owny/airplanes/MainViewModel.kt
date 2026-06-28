package cz.owny.airplanes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.owny.airplanes.data.Aircraft
import cz.owny.airplanes.data.AircraftDetails
import cz.owny.airplanes.data.AircraftRepository
import cz.owny.airplanes.data.AdsbdbRepository
import cz.owny.airplanes.data.PlanespottersRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel : ViewModel() {

    private val repository = AircraftRepository()
    private val adsbdbRepository = AdsbdbRepository()
    private val planespottersRepository = PlanespottersRepository()

    private val _aircraft = MutableStateFlow<List<Aircraft>>(emptyList())
    val aircraft: StateFlow<List<Aircraft>> = _aircraft

    private val _selectedDetails = MutableStateFlow<AircraftDetails?>(null)
    val selectedDetails: StateFlow<AircraftDetails?> = _selectedDetails

    private var previousAircraft = mapOf<String, Aircraft>()
    private var animationJob: Job? = null

    fun fetchDetails(aircraft: Aircraft?) {
        viewModelScope.launch {
            if (aircraft?.r == null) {
                _selectedDetails.value = null
                return@launch
            }
            val reg = aircraft.r
            val callsign = aircraft.flight?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
            val details = coroutineScope {
                val metadata = async { adsbdbRepository.lookup(reg, callsign) }
                val photo = async { planespottersRepository.lookup(reg) }
                val m = metadata.await()
                val p = photo.await()
                if (m != null || p != null) {
                    AircraftDetails(
                        photoUrl = p?.photoUrl,
                        photographer = p?.photographer,
                        photoLink = p?.photoLink,
                        ownerCountryName = m?.ownerCountryName,
                        origin = m?.origin,
                        destination = m?.destination,
                        airline = m?.airline,
                        callsign = m?.callsign
                    )
                } else {
                    null
                }
            }
            _selectedDetails.value = details
        }
    }

    fun fetchAircraft(lat: Double, lon: Double, radiusNm: Int) {
        viewModelScope.launch {
            try {
                val result = repository.getAircraft(lat, lon, radiusNm)
                startInterpolation(result)

                val activeKeys = result.mapNotNull { aircraft ->
                    aircraft.r?.let { reg ->
                        val cs = aircraft.flight?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
                        "$reg|${cs ?: ""}"
                    }
                }.toSet()
            adsbdbRepository.pruneCache(activeKeys)
            planespottersRepository.pruneCache(
                result.mapNotNull { it.r }.toSet()
            )
        } catch (e: Exception) {
                Log.w("Airplanes", "Failed to fetch aircraft", e)
            }
        }
    }

    private fun startInterpolation(target: List<Aircraft>) {
        animationJob?.cancel()
        previousAircraft = _aircraft.value.associateBy { it.hex }
        val targetMap = target.associateBy { it.hex }

        animationJob = viewModelScope.launch {
            val durationMs = 5000L
            val startTime = System.nanoTime()

            while (isActive) {
                val elapsed = (System.nanoTime() - startTime) / 1_000_000.0
                val fraction = (elapsed / durationMs).coerceIn(0.0, 1.0)

                val interpolated = target.map { targetPlane ->
                    val prev = previousAircraft[targetPlane.hex]
                    if (prev != null && prev.hasPosition() && targetPlane.hasPosition()) {
                        targetPlane.copy(
                            lat = lerp(prev.lat!!, targetPlane.lat!!, fraction),
                            lon = lerp(prev.lon!!, targetPlane.lon!!, fraction)
                        )
                    } else {
                        targetPlane
                    }
                }

                _aircraft.value = interpolated

                if (fraction >= 1.0) break
                delay(33L.milliseconds)
            }

            previousAircraft = targetMap
        }
    }

    private fun lerp(start: Double, end: Double, fraction: Double): Double {
        return start + (end - start) * fraction
    }
}
