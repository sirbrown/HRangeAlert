package com.hrrangealert.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "measurements")
@TypeConverters(Converters::class)
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val averageHeartRate: Int?,
    val maxHeartRate: Int?,
    val minHeartRate: Int?,
    val heartRateDataPoints: List<Int>,
    val durationMillis: Long,
    val caloriesBurned: Double? = null
)
