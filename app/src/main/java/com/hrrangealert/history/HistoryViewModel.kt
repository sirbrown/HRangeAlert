package com.hrrangealert.history // Or your ViewModel package

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrrangealert.data.Measurement
// import com.hrrangealert.data.MeasurementDao // You will uncomment and use this later
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    // private val measurementDao: MeasurementDao // Inject your DAO later
) : ViewModel() {

    private val _measurements = MutableStateFlow<List<Measurement>>(emptyList())
    val measurements: StateFlow<List<Measurement>> = _measurements.asStateFlow()

    private val _selectedMeasurementId = MutableStateFlow<Int?>(null)
    val selectedMeasurementId: StateFlow<Int?> = _selectedMeasurementId.asStateFlow()

    init {
        loadAllMeasurements()
    }

    private fun loadAllMeasurements() {
        viewModelScope.launch {
            // TODO: Replace with actual DAO call
            // _measurements.value = measurementDao.getAllMeasurements().first() // Example if DAO returns Flow

            // Simulate loading data for now
            _measurements.value = listOf(
                Measurement(1, System.currentTimeMillis() - 3600000, 75, 100, 60, List(60) { 70 + (Math.random() * 10).toInt() }, 60000),
                Measurement(2, System.currentTimeMillis() - 7200000, 85, 120, 70, List(120) { 80 + (Math.random() * 15).toInt() },120000),
                Measurement(3, System.currentTimeMillis() - 10800000, 65, 90, 55, List(30) { 60 + (Math.random() * 5).toInt() }, 30000)
            )
        }
    }

    fun loadMeasurementForDisplay(measurementId: Int) {
        _selectedMeasurementId.value = measurementId
    }

    fun doneDisplayingMeasurement() {
        _selectedMeasurementId.value = null // Reset after navigation
    }
}
