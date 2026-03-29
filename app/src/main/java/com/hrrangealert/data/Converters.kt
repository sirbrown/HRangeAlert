
// You'll need a TypeConverter for complex types like List<Int> if storing in Room
// Create a Converters.kt file
package com.hrrangealert.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromHeartRateDataPoints(value: List<Int>?): String? {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toHeartRateDataPoints(value: String?): List<Int>? {
        val listType = object : TypeToken<List<Int>?>() {}.type
        return Gson().fromJson(value, listType)
    }
}