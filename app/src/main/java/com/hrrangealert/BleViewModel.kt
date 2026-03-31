package com.hrrangealert

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import com.hrrangealert.data.Measurement
import com.hrrangealert.data.AppDatabase
import com.hrrangealert.data.MeasurementDao
import com.hrrangealert.data.SavedBleDevice
import com.hrrangealert.data.SavedBleDeviceDao

// Standard BLE UUIDs
val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
val HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID: UUID =
    UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")
val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // For enabling notifications

data class DiscoveredBleDevice(
    val name: String?,
    val address: String,
    val device: BluetoothDevice
)

interface IBleViewModel {
    val heartRate: StateFlow<Int?>
    val connectionStatus: StateFlow<String>
    val isMeasuring: StateFlow<Boolean>
    val hrDataPoints: StateFlow<List<Int>>
    val averageHeartRate: StateFlow<Int?>
    val maxHeartRate: StateFlow<Int?>
    val hrTargetRangeLower: StateFlow<Int>
    val hrTargetRangeUpper: StateFlow<Int>
    val showDeviceSelectionDialog: MutableStateFlow<Boolean>
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>>
    val savedDevices: StateFlow<List<SavedBleDevice>>


    fun toggleMeasurement(context: Context)
    fun disconnect(context: Context)
    fun init(context: Context)
    fun hasPermissions(context: Context): Boolean
    fun startScan(context: Context)
    fun stopScan(context: Context)
    fun connectToDevice(context: Context, device: BluetoothDevice)
    fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic)
    fun updateConnectionStatus(newStatus: String)
    fun onCleared()
    fun loadHistoricData(measurement: Measurement)
    fun saveDevice(device: DiscoveredBleDevice)
}

class BleViewModel(application: Application) : AndroidViewModel(application), IBleViewModel {

    private val _TAG = "BleViewModel"
    private val measurementDao: MeasurementDao = AppDatabase.getDatabase(application).measurementDao()
    private val savedBleDeviceDao: SavedBleDeviceDao = AppDatabase.getDatabase(application).savedBleDeviceDao()


    // Private MutableStateFlow that can be updated
    private val _connectionStatus = MutableStateFlow("Disconnected")
    // Publicly exposed StateFlow (read-only)
    override val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()


    private val _heartRate = MutableStateFlow<Int?>(null)
    override val heartRate: StateFlow<Int?> = _heartRate

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices

    override val showDeviceSelectionDialog = MutableStateFlow(false)

    private val _savedDevices = MutableStateFlow<List<SavedBleDevice>>(emptyList())
    override val savedDevices: StateFlow<List<SavedBleDevice>> = _savedDevices.asStateFlow()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private val scanHandler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000 // Stops scanning after 10 seconds.

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val _isMeasuring = MutableStateFlow(false)
    override val isMeasuring: StateFlow<Boolean> = _isMeasuring.asStateFlow()

    // For storing HR data points during a measurement session for the graph
    private val _hrDataPoints = MutableStateFlow<List<Int>>(emptyList())
    override val hrDataPoints: StateFlow<List<Int>> = _hrDataPoints.asStateFlow()

    // Example stats - you might want to make these more sophisticated
    private val _averageHeartRate = MutableStateFlow<Int?>(null)
    override val averageHeartRate: StateFlow<Int?> = _averageHeartRate.asStateFlow()

    private val _maxHeartRate = MutableStateFlow<Int?>(null)
    override val maxHeartRate: StateFlow<Int?> = _maxHeartRate.asStateFlow()

    // Example range (could be user-configurable later)
    override val hrTargetRangeLower = MutableStateFlow(60) // Example
    override val hrTargetRangeUpper = MutableStateFlow(100) // Example

    private var measurementJob: Job? = null
    private var measurementStartTimeMillis: Long = 0L
    private var currentMeasurementData = mutableListOf<Int>()

    // Filter for devices advertising the Heart Rate Service
    private val scanFilters: List<ScanFilter> = listOf(
        ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()
    )


    override fun init(context: Context) {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _connectionStatus.value = "Bluetooth is not enabled or not supported."
            // Consider prompting user to enable Bluetooth
            return
        }
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
        loadSavedDevices()
        autoConnect(context)
    }

    private fun loadSavedDevices() {
        viewModelScope.launch {
            savedBleDeviceDao.getSavedDevices().collect { devices ->
                _savedDevices.value = devices
            }
        }
    }

    private fun autoConnect(context: Context) {
        viewModelScope.launch {
            savedBleDeviceDao.getSavedDevices().collect { devices ->
                if (devices.isNotEmpty()) {
                    for (device in devices) {
                        try {
                            val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                            if (bluetoothDevice != null) {
                                connectToDevice(context, bluetoothDevice)
                                // If connection is successful, break the loop
                                // This is a simplification. A more robust solution would involve
                                // waiting for the connection state to change to connected.
                                break
                            }
                        } catch (e: IllegalArgumentException) {
                            Log.e(_TAG, "Invalid Bluetooth address: ${device.address}", e)
                        }
                    }
                }
            }
        }
    }


    override fun hasPermissions(context: Context): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION // Still good to have for scan results
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION // Required for scanning pre-Android 12
            )
        }
        return requiredPermissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Function within your ViewModel to update the status
    override fun updateConnectionStatus(newStatus: String) {
        _connectionStatus.value = newStatus
    }

    @SuppressLint("MissingPermission")
    override // Permissions checked by hasPermissions
    fun startScan(context: Context) {
        if (!hasPermissions(context)) {
            _connectionStatus.value = "Permissions missing for BLE scan."
            Log.e(_TAG, "Permissions missing for BLE scan.")
            return
        }
        if (bleScanner == null) {
            _connectionStatus.value = "BLE Scanner not available."
            Log.e(_TAG, "BLE Scanner not available.")
            init(context) // Try to re-initialize
            if (bleScanner == null) return
        }

        _connectionStatus.value = "Scanning for HRMs..."
        _discoveredDevices.value = emptyList() // Clear previous results
        showDeviceSelectionDialog.value = false

        scanHandler.postDelayed({
            stopScan(context)
            if (_discoveredDevices.value.isEmpty()) {
                _connectionStatus.value = "No HRM devices found."
            } else {
                showDeviceSelectionDialog.value = true
                _connectionStatus.value = "Select a device."
            }
        }, SCAN_PERIOD)

        bleScanner?.startScan(scanFilters, scanSettings, leScanCallback)
        Log.d(_TAG, "BLE Scan Started")
    }

    @SuppressLint("MissingPermission")
    override fun stopScan(context: Context) {
        if (!hasPermissions(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // SCAN permission needed for stopScan on API 31+
            Log.w(_TAG, "Missing BLUETOOTH_SCAN permission to stop scan on API 31+")
            // Potentially can't stop scan without permission, which is an issue.
            // However, scan should also stop due to SCAN_PERIOD timeout.
        }
        bleScanner?.stopScan(leScanCallback)
        _connectionStatus.value =
            if (_discoveredDevices.value.isNotEmpty() && !showDeviceSelectionDialog.value) "Scan finished. Select device." else _connectionStatus.value
        Log.d(_TAG, "BLE Scan Stopped")
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")// Permissions checked by hasPermissions
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            val deviceName = device.name ?: "Unknown Device"
            val deviceAddress = device.address

            val existingDevice = _discoveredDevices.value.find { it.address == deviceAddress }
            if (existingDevice == null) {
                val discoveredDevice = DiscoveredBleDevice(deviceName, deviceAddress, device)
                _discoveredDevices.value += discoveredDevice
                Log.d(_TAG, "Found BLE Device: Name: $deviceName, Address: $deviceAddress")
            }
        }

        @SuppressLint("MissingPermission")// Permissions checked by hasPermissions
        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach { result ->
                val device = result.device
                val deviceName = device.name ?: "Unknown Device"
                val deviceAddress = device.address
                val existingDevice = _discoveredDevices.value.find { it.address == deviceAddress }
                if (existingDevice == null) {
                    _discoveredDevices.value += DiscoveredBleDevice(
                                            deviceName,
                                            deviceAddress,
                                            device
                                        )
                }
            }
            Log.d(_TAG, "Batch Scan Results: ${_discoveredDevices.value.size} devices")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            _connectionStatus.value = "BLE Scan Failed: $errorCode"
            Log.e(_TAG, "BLE Scan Failed: $errorCode")
        }
    }

    @SuppressLint("MissingPermission") // Permissions checked by hasPermissions
    override fun connectToDevice(context: Context, device: BluetoothDevice) {
        if (!hasPermissions(context)) {
            _connectionStatus.value = "Permissions missing to connect."
            return
        }
        _connectionStatus.value = "Connecting to ${device.name ?: device.address}..."
        showDeviceSelectionDialog.value = false
        stopScan(context) // Stop scanning before connecting

        // Ensure BluetoothAdapter is available
        if (bluetoothAdapter == null) {
            Log.e(_TAG, "BluetoothAdapter not initialized for connect")
            _connectionStatus.value = "Bluetooth not initialized."
            return
        }

        bluetoothGatt =
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    override fun saveDevice(device: DiscoveredBleDevice) {
        viewModelScope.launch {
            val savedBleDevice = SavedBleDevice(
                address = device.address,
                name = device.name
            )
            savedBleDeviceDao.insertDevice(savedBleDevice)
        }
    }


    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission") // Permissions were checked before connectToDevice initiated this callback sequence
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            val deviceName = gatt.device.name ?: deviceAddress

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionStatus.value = "Connected to $deviceName"
                bluetoothGatt = gatt // Store the gatt instance
                viewModelScope.launch {
                    // Assuming BLUETOOTH_CONNECT was granted before connectToDevice was called
                    val discoverServicesSuccess = gatt.discoverServices()
                    if (!discoverServicesSuccess) {
                        Log.e(_TAG, "Failed to initiate service discovery.")
                        _connectionStatus.value = "Failed to start service discovery"
                        // Optionally disconnect or handle error
                        this@BleViewModel.disconnect(getApplication<Application>().applicationContext) // Pass context if your disconnect needs it for some reason
                    } else {
                        Log.d(_TAG, "Initiated service discovery...")
                    }
                }
                Log.d(_TAG, "Successfully connected to $deviceAddress")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionStatus.value = "Disconnected from $deviceName"
                _heartRate.value = null
                gatt.close() // Close GATT when disconnected
                bluetoothGatt = null // Clear the GATT instance
                Log.d(_TAG, "Disconnected from $deviceAddress")
            }
        }

        @SuppressLint("MissingPermission") // Permissions were checked before connectToDevice initiated this callback sequence
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionStatus.value = "Services discovered"
                Log.d(_TAG, "Services discovered for ${gatt.device.address}")
                val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
                if (hrService == null) {
                    Log.e(_TAG, "Heart Rate Service not found!")
                    _connectionStatus.value = "HR Service not found"
                    return
                }
                val hrCharacteristic =
                    hrService.getCharacteristic(HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID)
                if (hrCharacteristic == null) {
                    Log.e(_TAG, "Heart Rate Characteristic not found!")
                    _connectionStatus.value = "HR Characteristic not found"
                    return
                }

                // Enable notifications for the HR characteristic
                // Assuming BLUETOOTH_CONNECT was granted before connectToDevice
                val notificationSet = gatt.setCharacteristicNotification(hrCharacteristic, true)
                if (!notificationSet) {
                    Log.e(_TAG, "Failed to enable characteristic notification.")
                    _connectionStatus.value = "Failed to request HR notifications"
                    return
                }

                val descriptor =
                    hrCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                if (descriptor != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val result = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        if (result != BluetoothGatt.GATT_SUCCESS) {
                            Log.e(_TAG, "Failed to write CCCD descriptor to enable notifications, result: $result")
                            _connectionStatus.value = "Failed to enable HR notifications (write)"
                        } else {
                            Log.i(_TAG, "CCCD descriptor write initiated for HR notifications")
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        val descriptorWriteSuccess = gatt.writeDescriptor(descriptor)
                        if (!descriptorWriteSuccess) {
                            Log.e(_TAG, "Failed to write CCCD descriptor to enable notifications")
                            _connectionStatus.value = "Failed to enable HR notifications (write)"
                        } else {
                            Log.i(_TAG, "CCCD descriptor write initiated for HR notifications")
                        }
                    }
                } else {
                    Log.e(
                        _TAG,
                        "CLIENT_CHARACTERISTIC_CONFIG_UUID descriptor not found for HR characteristic"
                    )
                    _connectionStatus.value = "Failed to find descriptor for HR notifications"
                }

            } else {
                _connectionStatus.value = "Service discovery failed: $status"
                Log.w(_TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID == characteristic.uuid) {
                val heartRateValue = parseHeartRate(value)
                _heartRate.value = heartRateValue
                Log.d(_TAG, "Heart Rate Updated: $heartRateValue")
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray ,status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID == characteristic.uuid) {
                    val heartRateValue = parseHeartRate(value)
                    _heartRate.value = heartRateValue
                     Log.d(_TAG, "Heart Rate Read: $heartRateValue")
                }
            }
        }

        // Callback for descriptor write (confirming notification enabled)
        @Suppress("DEPRECATION")
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (CLIENT_CHARACTERISTIC_CONFIG_UUID == descriptor.uuid) {
                    if (Arrays.equals(descriptor.value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                        Log.i(_TAG, "Successfully enabled notifications for ${descriptor.characteristic.uuid}")
                        _connectionStatus.value = "Receiving HR updates..."

                    } else if (Arrays.equals(descriptor.value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                        Log.i(_TAG, "Successfully disabled notifications for ${descriptor.characteristic.uuid}")
                    }
                }
            } else {
                Log.e(_TAG, "Failed to write descriptor: ${descriptor.uuid}, Status: $status")
                _connectionStatus.value = "Error setting up HR notifications"
            }
        }
    }

    /**
     * Parses the heart rate value from the Heart Rate Measurement characteristic.
     * See: https://www.bluetooth.com/specifications/gatt/characteristics/
     */
    private fun parseHeartRate(data: ByteArray?): Int? {
        if (data == null || data.isEmpty()) return null

        val flags = data[0].toInt()
        val format = if ((flags and 0x01) != 0) BluetoothGattCharacteristic.FORMAT_UINT16 else BluetoothGattCharacteristic.FORMAT_UINT8

        return if (format == BluetoothGattCharacteristic.FORMAT_UINT16) {
            if (data.size >= 3) { // Ensure there are enough bytes for UINT16
                // Heart rate is in bytes 1 and 2 (Little Endian)
                ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            } else {
                Log.w(_TAG, "parseHeartRate: UINT16 format but data length is insufficient (${data.size})")
                null // Not enough data for UINT16
            }
        } else {
            if (data.size >= 2) { // Ensure there are enough bytes for UINT8
                 data[1].toInt() and 0xFF // Heart rate is in byte 1
            } else {
                 Log.w(_TAG, "parseHeartRate: UINT8 format but data length is insufficient (${data.size})")
                null // Not enough data for UINT8
            }
        }
    }

    // In onCharacteristicChanged, update data points if measuring
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID == characteristic.uuid) {
            @Suppress("DEPRECATION")
            val heartRateValue = parseHeartRate(characteristic.value)
            _heartRate.value = heartRateValue
            Log.d(_TAG, "Heart Rate Updated: $heartRateValue")

            if (_isMeasuring.value && heartRateValue != null) {
                currentMeasurementData.add(heartRateValue)
                _hrDataPoints.value = currentMeasurementData.toList() // Update for graph

                // Simple live calculation for max (average can be done at the end or periodically)
                if (heartRateValue > (_maxHeartRate.value ?: Int.MIN_VALUE)) {
                    _maxHeartRate.value = heartRateValue
                }
            }
        }
    }


    override fun toggleMeasurement(context: Context) {
        if (_isMeasuring.value) {
            stopMeasurementSession()        } else {
            // --- THIS IS THE CRITICAL FIX ---
            // Before starting a new measurement, reset all data states to clear
            // any previously loaded historical data or a past session's data.
            resetMeasurementData()

            // Now, start the new measurement session.
            _isMeasuring.value = true
            measurementStartTimeMillis = System.currentTimeMillis()
            currentMeasurementData.clear()
            _connectionStatus.value = "Measurement started..." // Provide feedback

            // This job will now correctly collect and update fresh data.
            measurementJob = viewModelScope.launch {
                heartRate.collect { hr ->
                    if (hr != null && _isMeasuring.value) {
                        currentMeasurementData.add(hr)
                        _hrDataPoints.value = currentMeasurementData.toList() // Update graph
                        if (currentMeasurementData.isNotEmpty()) {
                            _averageHeartRate.value = currentMeasurementData.average().toInt()
                            _maxHeartRate.value = currentMeasurementData.maxOrNull()
                        }
                    }
                }
            }
        }
    }

    // A new helper function to centralize the reset logic.
    private fun resetMeasurementData() {
        _hrDataPoints.value = emptyList()
        _averageHeartRate.value = null
        _maxHeartRate.value = null
        _heartRate.value = null // Clear the current HR reading
        currentMeasurementData.clear()
        measurementStartTimeMillis = 0L
    }

    @SuppressLint("MissingPermission") // Ensure connect permissions are checked before calling this
    private fun startMeasurementSession(context: Context) {
        if (bluetoothGatt == null || _connectionStatus.value != "Receiving HR updates...") {
            // Only start if connected and receiving updates.
            // You might want to automatically try to connect if not.
            updateConnectionStatus("Cannot start: Not connected to HRM or not receiving updates.")
            Log.w(_TAG, "Attempted to start measurement without active HR updates.")
            return
        }

        _isMeasuring.value = true
        measurementStartTimeMillis = System.currentTimeMillis()
        currentMeasurementData.clear()
        _hrDataPoints.value = emptyList()
        _averageHeartRate.value = null
        _maxHeartRate.value = null
        _connectionStatus.value = "Measurement Started"
        Log.d(_TAG, "Measurement Started")

        // If you need to perform actions periodically during measurement (e.g., save chunks to DB)
        // measurementJob = viewModelScope.launch {
        //     while (_isMeasuring.value) {
        //         delay(5000) // Example: Do something every 5 seconds
        //         // Add periodic saving or analysis here if needed
        //     }
        // }
    }

    private fun stopMeasurementSession() {
        _isMeasuring.value = false
        val duration = if (measurementStartTimeMillis > 0) System.currentTimeMillis() - measurementStartTimeMillis else 0L
        
        measurementJob?.cancel() // Stop the collection coroutine
        measurementJob = null
        Log.d(_TAG, "Measurement stopped. Data points: ${currentMeasurementData.size}, Duration: ${duration}ms")

        if (currentMeasurementData.isNotEmpty()) {
            _connectionStatus.value = "Measurement stopped"

            // Save the completed measurement
            val measurementToSave = Measurement(
                timestamp = System.currentTimeMillis(),
                heartRateDataPoints = currentMeasurementData.toList(),
                averageHeartRate = currentMeasurementData.average().toInt(),
                maxHeartRate = currentMeasurementData.maxOrNull() ?: 0,
                minHeartRate = currentMeasurementData.minOrNull() ?: 0,
                durationMillis = duration
            )

            // Launch a coroutine to save to the database
            viewModelScope.launch {
                try {
                    measurementDao.insertMeasurement(measurementToSave)
                    Log.d(_TAG, "Successfully saved measurement to database: $measurementToSave")
                } catch (e: Exception) {
                    Log.e(_TAG, "Failed to save measurement to database", e)
                }
            }
        } else {
            // If we stop without data, clear everything.
            resetMeasurementData()
            _connectionStatus.value = bluetoothGatt?.let { "Connected" } ?: "Disconnected"
        }
    }

    // You'll need to call this from MainActivity or your UI when appropriate
    @SuppressLint("MissingPermission")
    override fun disconnect(context: Context) {
        if (_isMeasuring.value) {
            stopMeasurementSession() // Stop measurement before disconnecting
        }
        if (bluetoothGatt != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(_TAG, "BLUETOOTH_CONNECT permission missing for disconnect")
                _connectionStatus.value = "Connect permission missing to disconnect."
                return
            }
            bluetoothGatt?.disconnect() // This will trigger onConnectionStateChange with STATE_DISCONNECTED
            // Gatt will be closed in onConnectionStateChange
        } else {
            _connectionStatus.value = "Not connected."
        }
    }

    @SuppressLint("MissingPermission")// Permissions checked by hasPermissions
    override fun onCleared() {
        super.onCleared()
        stopScan(getApplication<Application>().applicationContext) // Requires application context or pass activity context
        bluetoothGatt?.close() // Ensure GATT is closed
        bluetoothGatt = null
        Log.d(_TAG, "ViewModel cleared, BLE resources released.")
    }

    override fun loadHistoricData(measurement: Measurement) {
        // Stop any ongoing live measurement session.
        if (_isMeasuring.value) {
            stopMeasurementSession()
        }

        // Set isMeasuring to false as we are viewing historical data, not recording live.
        _isMeasuring.value = false

        // Update the state flows with the data from the historical measurement.
        // The UI (NewMainScreen) is already observing these flows and will update automatically.
        _hrDataPoints.value = measurement.heartRateDataPoints
        _averageHeartRate.value = measurement.averageHeartRate
        _maxHeartRate.value = measurement.maxHeartRate

        // Set heartRate to null or average, as there's no single "live" HR value.
        // Setting it to null or the average prevents the display from showing a stale live value.
        _heartRate.value = null

        // Update the connection status to reflect that historical data is being shown.
        _connectionStatus.value = "Displaying saved measurement"

        Log.d(_TAG, "Loaded historic data for measurement from timestamp: ${measurement.timestamp}")
    }

}
