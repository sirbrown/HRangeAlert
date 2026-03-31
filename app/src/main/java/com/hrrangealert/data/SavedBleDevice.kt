package com.hrrangealert.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "saved_ble_devices")
data class SavedBleDevice(
    @PrimaryKey
    val address: String,
    val name: String?,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SavedBleDeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: SavedBleDevice)

    @Query("SELECT * FROM saved_ble_devices ORDER BY timestamp DESC")
    fun getSavedDevices(): Flow<List<SavedBleDevice>>

    @Query("DELETE FROM saved_ble_devices WHERE address = :address")
    suspend fun deleteDevice(address: String)
}
