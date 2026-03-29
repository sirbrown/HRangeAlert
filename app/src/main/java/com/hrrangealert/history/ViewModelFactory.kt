package com.hrrangealert.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hrrangealert.data.MeasurementDao

class HistoryViewModelFactory(private val measurementDao: MeasurementDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(measurementDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
