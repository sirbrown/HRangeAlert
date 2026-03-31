package com.hrrangealert.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 1, // Use a fixed ID for a single settings entry
    val age: Int?,
    val gender: String?,
    val weight: Int?
)
