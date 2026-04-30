package com.hrrangealert.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Measurement::class, UserSettings::class, SavedBleDevice::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun savedBleDeviceDao(): SavedBleDeviceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            @Suppress("DEPRECATION")
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hr_range_alert_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = false) // Not for production apps!
                .allowMainThreadQueries() // Allow queries on the main thread
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
