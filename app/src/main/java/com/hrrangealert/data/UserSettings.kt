package com.hrrangealert.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1,
    val age: Int? = null,
    val gender: String? = null,
    val weight: Int? = null,
    val mhrFormula: String = "Fox",
    val targetZoneIndex: Int = 0, // 0 means no target, 1-5 for zones
    
    // Zone 1
    val zone1Min: Int = 0,
    val zone1Max: Int = 0,
    val zone1LowAlarm: Boolean = false,
    val zone1HighAlarm: Boolean = false,

    // Zone 2
    val zone2Min: Int = 0,
    val zone2Max: Int = 0,
    val zone2LowAlarm: Boolean = false,
    val zone2HighAlarm: Boolean = false,

    // Zone 3
    val zone3Min: Int = 0,
    val zone3Max: Int = 0,
    val zone3LowAlarm: Boolean = false,
    val zone3HighAlarm: Boolean = false,

    // Zone 4
    val zone4Min: Int = 0,
    val zone4Max: Int = 0,
    val zone4LowAlarm: Boolean = false,
    val zone4HighAlarm: Boolean = false,

    // Zone 5
    val zone5Min: Int = 0,
    val zone5Max: Int = 0,
    val zone5LowAlarm: Boolean = false,
    val zone5HighAlarm: Boolean = false
)
