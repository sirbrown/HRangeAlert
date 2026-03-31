package com.hrrangealert.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrrangealert.data.UserSettings
import com.hrrangealert.data.UserSettingsDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val userSettingsDao: UserSettingsDao) : ViewModel() {

    val userSettings = userSettingsDao.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
}
