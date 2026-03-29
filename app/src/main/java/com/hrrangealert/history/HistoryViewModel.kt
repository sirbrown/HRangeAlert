package com.hrrangealert.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrrangealert.data.Measurement
import com.hrrangealert.data.MeasurementDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val measurementDao: MeasurementDao
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
            measurementDao.getAllMeasurements().collect { measurementList ->
                _measurements.value = measurementList
            }
        }
    }

    fun loadMeasurementForDisplay(measurementId: Int) {
        _selectedMeasurementId.value = measurementId
    }

    fun doneDisplayingMeasurement() {
        _selectedMeasurementId.value = null // Reset after navigation
    }

    fun deleteMeasurement(measurement: Measurement) {
        viewModelScope.launch {
            // If you add a delete method to DAO
            // measurementDao.deleteMeasurement(measurement)
        }
    }
}
