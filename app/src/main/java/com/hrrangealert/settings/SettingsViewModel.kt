package com.hrrangealert.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrrangealert.data.SavedBleDevice
import com.hrrangealert.data.SavedBleDeviceDao
import com.hrrangealert.data.UserSettings
import com.hrrangealert.data.UserSettingsDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userSettingsDao: UserSettingsDao,
    private val savedBleDeviceDao: SavedBleDeviceDao
) : ViewModel() {

    val userSettings = userSettingsDao.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val savedDevices = savedBleDeviceDao.getSavedDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveSettings(age: String, gender: String, weight: String) {
        viewModelScope.launch {
            val settings = UserSettings(
                age = age.toIntOrNull(),
                gender = gender,
                weight = weight.toIntOrNull()
            )
            userSettingsDao.insertOrUpdate(settings)
        }
    }

    fun renameDevice(address: String, newName: String) {
        viewModelScope.launch {
            val existingDevice = savedBleDeviceDao.getDevice(address)
            val updatedDevice = existingDevice?.copy(name = newName) ?: SavedBleDevice(address, newName)
            savedBleDeviceDao.insertDevice(updatedDevice)
        }
    }

    fun deleteDevice(address: String) {
        viewModelScope.launch {
            savedBleDeviceDao.deleteDevice(address)
        }
    }
}
