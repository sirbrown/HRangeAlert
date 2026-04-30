package com.hrrangealert.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrrangealert.data.UserSettings
import com.hrrangealert.data.UserSettingsDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userSettingsDao: UserSettingsDao
) : ViewModel() {

    val userSettings = userSettingsDao.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveSettings(
        age: String,
        gender: String,
        weight: String,
        mhrFormula: String,
        targetZoneIndex: Int,
        zone1Min: Int, zone1Max: Int, zone1LowAlarm: Boolean, zone1HighAlarm: Boolean,
        zone2Min: Int, zone2Max: Int, zone2LowAlarm: Boolean, zone2HighAlarm: Boolean,
        zone3Min: Int, zone3Max: Int, zone3LowAlarm: Boolean, zone3HighAlarm: Boolean,
        zone4Min: Int, zone4Max: Int, zone4LowAlarm: Boolean, zone4HighAlarm: Boolean,
        zone5Min: Int, zone5Max: Int, zone5LowAlarm: Boolean, zone5HighAlarm: Boolean
    ) {
        viewModelScope.launch {
            val settings = UserSettings(
                age = age.toIntOrNull(),
                gender = gender,
                weight = weight.toIntOrNull(),
                mhrFormula = mhrFormula,
                targetZoneIndex = targetZoneIndex,
                zone1Min = zone1Min, zone1Max = zone1Max, zone1LowAlarm = zone1LowAlarm, zone1HighAlarm = zone1HighAlarm,
                zone2Min = zone2Min, zone2Max = zone2Max, zone2LowAlarm = zone2LowAlarm, zone2HighAlarm = zone2HighAlarm,
                zone3Min = zone3Min, zone3Max = zone3Max, zone3LowAlarm = zone3LowAlarm, zone3HighAlarm = zone3HighAlarm,
                zone4Min = zone4Min, zone4Max = zone4Max, zone4LowAlarm = zone4LowAlarm, zone4HighAlarm = zone4HighAlarm,
                zone5Min = zone5Min, zone5Max = zone5Max, zone5LowAlarm = zone5LowAlarm, zone5HighAlarm = zone5HighAlarm
            )
            userSettingsDao.insertOrUpdate(settings)
        }
    }

    fun calculateZones(age: Int, formula: String): List<Pair<Int, Int>> {
        val mhr = if (formula == "Fox") {
            220 - age
        } else {
            (208 - (0.7 * age)).toInt()
        }

        return listOf(
            (mhr * 0.50).toInt() to (mhr * 0.60).toInt(),
            (mhr * 0.60).toInt() to (mhr * 0.70).toInt(),
            (mhr * 0.70).toInt() to (mhr * 0.80).toInt(),
            (mhr * 0.80).toInt() to (mhr * 0.90).toInt(),
            (mhr * 0.90).toInt() to mhr
        )
    }
}
