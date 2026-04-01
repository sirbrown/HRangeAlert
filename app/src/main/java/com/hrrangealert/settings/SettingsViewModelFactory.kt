package com.hrrangealert.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hrrangealert.data.SavedBleDeviceDao
import com.hrrangealert.data.UserSettingsDao

class SettingsViewModelFactory(
    private val userSettingsDao: UserSettingsDao,
    private val savedBleDeviceDao: SavedBleDeviceDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(userSettingsDao, savedBleDeviceDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
