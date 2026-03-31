package com.hrrangealert.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hrrangealert.data.UserSettingsDao

class SettingsViewModelFactory(private val userSettingsDao: UserSettingsDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(userSettingsDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
