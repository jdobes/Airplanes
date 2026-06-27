package cz.owny.airplanes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.owny.airplanes.data.Aircraft
import cz.owny.airplanes.data.AircraftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val repository = AircraftRepository()

    private val _aircraft = MutableStateFlow<List<Aircraft>>(emptyList())
    val aircraft: StateFlow<List<Aircraft>> = _aircraft

    fun fetchAircraft(lat: Double, lon: Double) {
        viewModelScope.launch {
            try {
                val result = repository.getAircraft(lat, lon)
                _aircraft.value = result
                //Log.d("Airplanes", "Fetched ${result.size} aircraft at ${lat},${lon}")
            } catch (e: Exception) {
                Log.w("Airplanes", "Failed to fetch aircraft", e)
            }
        }
    }
}
