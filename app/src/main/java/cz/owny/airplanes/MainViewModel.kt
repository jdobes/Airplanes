package cz.owny.airplanes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.owny.airplanes.data.Aircraft
import cz.owny.airplanes.data.AircraftRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val repository = AircraftRepository()

    private val _aircraft = MutableStateFlow<List<Aircraft>>(emptyList())
    val aircraft: StateFlow<List<Aircraft>> = _aircraft

    private var previousAircraft = mapOf<String, Aircraft>()
    private var animationJob: Job? = null

    fun fetchAircraft(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val result = repository.getAircraft(lat, lon)
                startInterpolation(result)
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
                delay(33L)
            }

            previousAircraft = targetMap
        }
    }

    private fun lerp(start: Double, end: Double, fraction: Double): Double {
        return start + (end - start) * fraction
    }
}
