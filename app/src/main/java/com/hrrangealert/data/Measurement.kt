package com.hrrangealert.data // Or your data model package

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "measurements")
@TypeConverters(Converters::class) // For converting List<Int> to String and back
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long, // When the measurement was started/saved
    val averageHeartRate: Int?,
    val maxHeartRate: Int?,
    val minHeartRate: Int?, // Added min for completeness
    val heartRateDataPoints: List<Int>, // All HR readings during the session
    val durationMillis: Long // Duration of the measurement
)
