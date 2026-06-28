package cz.owny.airplanes

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cz.owny.airplanes.data.Aircraft
import cz.owny.airplanes.data.AircraftDetails
import cz.owny.airplanes.data.AircraftRepository
import cz.owny.airplanes.data.Airline
import cz.owny.airplanes.data.AirlineDatabase
import cz.owny.airplanes.data.Airport
import cz.owny.airplanes.data.AircraftPrefixDatabase
import cz.owny.airplanes.data.AirportDatabase
import cz.owny.airplanes.data.PlanespottersRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(application: Application) : AndroidViewModel(application) {

    init {
        AirportDatabase.init(application)
        AirlineDatabase.init(application)
        AircraftPrefixDatabase.init(application)
    }

    private val repository = AircraftRepository()
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
            val p = planespottersRepository.lookup(reg)
            val details = if (p != null || aircraft.origin != null || aircraft.destination != null || aircraft.airline != null) {
                AircraftDetails(
                    photoUrl = p?.photoUrl,
                    photographer = p?.photographer,
                    photoLink = p?.photoLink,
                    ownerCountryName = AircraftPrefixDatabase.lookup(reg),
                    origin = aircraft.origin?.let { iata ->
                        val entry = AirportDatabase.lookup(iata)
                        Airport(iataCode = iata, municipality = entry?.city, countryIso = entry?.country_code)
                    },
                    destination = aircraft.destination?.let { iata ->
                        val entry = AirportDatabase.lookup(iata)
                        Airport(iataCode = iata, municipality = entry?.city, countryIso = entry?.country_code)
                    },
                    airline = aircraft.airline?.let { icao ->
                        AirlineDatabase.lookup(icao)?.let { name -> Airline(name = name, icao = icao) }
                    }
                )
            } else {
                null
            }
            _selectedDetails.value = details
        }
    }

    fun fetchAircraft(north: Double, south: Double, west: Double, east: Double) {
        viewModelScope.launch {
            try {
                val result = repository.getAircraft(north, south, west, east)
                startInterpolation(result)

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
