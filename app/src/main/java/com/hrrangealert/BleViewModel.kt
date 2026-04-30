package com.hrrangealert

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hrrangealert.data.AppDatabase
import com.hrrangealert.data.Measurement
import com.hrrangealert.data.SavedBleDevice
import com.hrrangealert.data.UserSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

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
    val caloriesBurned: StateFlow<Double?>
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
    fun renameDevice(address: String, newName: String)
    fun deleteDevice(address: String)
    fun getBluetoothDevice(address: String): BluetoothDevice?
}

class BleViewModel(application: Application) : AndroidViewModel(application), IBleViewModel {

    private val _TAG = "BleViewModel"
    private val database by lazy { AppDatabase.getDatabase(application) }
    private val measurementDao by lazy { database.measurementDao() }
    private val savedBleDeviceDao by lazy { database.savedBleDeviceDao() }
    private val userSettingsDao by lazy { database.userSettingsDao() }

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)

    private val _connectionStatus = MutableStateFlow("Disconnected")
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
    private val SCAN_PERIOD: Long = 10000

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val _isMeasuring = MutableStateFlow(false)
    override val isMeasuring: StateFlow<Boolean> = _isMeasuring.asStateFlow()

    private val _hrDataPoints = MutableStateFlow<List<Int>>(emptyList())
    override val hrDataPoints: StateFlow<List<Int>> = _hrDataPoints.asStateFlow()

    private val _averageHeartRate = MutableStateFlow<Int?>(null)
    override val averageHeartRate: StateFlow<Int?> = _averageHeartRate.asStateFlow()

    private val _maxHeartRate = MutableStateFlow<Int?>(null)
    override val maxHeartRate: StateFlow<Int?> = _maxHeartRate.asStateFlow()

    private val _caloriesBurned = MutableStateFlow<Double?>(null)
    override val caloriesBurned: StateFlow<Double?> = _caloriesBurned.asStateFlow()

    private val _hrTargetRangeLower = MutableStateFlow(0)
    override val hrTargetRangeLower: StateFlow<Int> = _hrTargetRangeLower.asStateFlow()

    private val _hrTargetRangeUpper = MutableStateFlow(220)
    override val hrTargetRangeUpper: StateFlow<Int> = _hrTargetRangeUpper.asStateFlow()

    private var measurementJob: Job? = null
    private var measurementStartTimeMillis: Long = 0L
    private var currentMeasurementData = mutableListOf<Int>()

    private var currentUserSettings: UserSettings? = null

    private val scanFilters: List<ScanFilter> = listOf(
        ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()
    )

    init {
        viewModelScope.launch {
            savedBleDeviceDao.getSavedDevices().collect { devices ->
                _savedDevices.value = devices
            }
        }
        viewModelScope.launch {
            userSettingsDao.getSettings().collect { settings ->
                currentUserSettings = settings
                updateTargetRange()
            }
        }
    }

    private fun updateTargetRange() {
        val settings = currentUserSettings ?: return
        when (settings.targetZoneIndex) {
            1 -> { _hrTargetRangeLower.value = settings.zone1Min; _hrTargetRangeUpper.value = settings.zone1Max }
            2 -> { _hrTargetRangeLower.value = settings.zone2Min; _hrTargetRangeUpper.value = settings.zone2Max }
            3 -> { _hrTargetRangeLower.value = settings.zone3Min; _hrTargetRangeUpper.value = settings.zone3Max }
            4 -> { _hrTargetRangeLower.value = settings.zone4Min; _hrTargetRangeUpper.value = settings.zone4Max }
            5 -> { _hrTargetRangeLower.value = settings.zone5Min; _hrTargetRangeUpper.value = settings.zone5Max }
            else -> { _hrTargetRangeLower.value = 0; _hrTargetRangeUpper.value = 220 }
        }
    }


    override fun init(context: Context) {
        Log.d(_TAG, "Initializing BleViewModel...")
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _connectionStatus.value = "Bluetooth is not enabled or not supported."
            Log.w(_TAG, "Bluetooth adapter is null or not enabled.")
            return
        }
        Log.d(_TAG, "Bluetooth adapter obtained. Initializing scanner.")
        bleScanner = bluetoothAdapter?.bluetoothLeScanner

        viewModelScope.launch {
            autoConnect(context)
        }
    }

    private suspend fun autoConnect(context: Context) {
        val devices = savedBleDeviceDao.getSavedDevices().first()
        if (devices.isNotEmpty()) {
            val deviceToConnect = devices.first()
            try {
                val bluetoothDevice =
                    bluetoothAdapter?.getRemoteDevice(deviceToConnect.address)
                if (bluetoothDevice != null) {
                    connectToDevice(context, bluetoothDevice)
                }
            } catch (e: IllegalArgumentException) {
                Log.e(_TAG, "Invalid Bluetooth address", e)
            }
        }
    }


    override fun hasPermissions(context: Context): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        return requiredPermissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun updateConnectionStatus(newStatus: String) {
        _connectionStatus.value = newStatus
    }

    @SuppressLint("MissingPermission")
    override
    fun startScan(context: Context) {
        if (!hasPermissions(context)) {
            _connectionStatus.value = "Permissions missing for BLE scan."
            return
        }
        if (bleScanner == null) {
            _connectionStatus.value = "BLE Scanner not available."
            init(context)
            if (bleScanner == null) return
        }

        _connectionStatus.value = "Scanning for HRMs..."
        _discoveredDevices.value = emptyList()
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
    }

    @SuppressLint("MissingPermission")
    override fun stopScan(context: Context) {
        scanHandler.removeCallbacksAndMessages(null)
        bleScanner?.stopScan(leScanCallback)
        _connectionStatus.value =
            if (_discoveredDevices.value.isNotEmpty() && !showDeviceSelectionDialog.value) "Scan finished. Select device." else _connectionStatus.value
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            val deviceName = device.name ?: "Unknown Device"
            val deviceAddress = device.address

            val existingDevice = _discoveredDevices.value.find { it.address == deviceAddress }
            if (existingDevice == null) {
                val discoveredDevice = DiscoveredBleDevice(deviceName, deviceAddress, device)
                _discoveredDevices.value += discoveredDevice
            }
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach { result ->
                val device = result.device
                val deviceName = device.name ?: "Unknown Device"
                val deviceAddress = device.address
                val existingDevice =
                    _discoveredDevices.value.find { it.address == deviceAddress }
                if (existingDevice == null) {
                    _discoveredDevices.value += DiscoveredBleDevice(
                        deviceName,
                        deviceAddress,
                        device
                    )
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            _connectionStatus.value = "BLE Scan Failed: $errorCode"
        }
    }

    @SuppressLint("MissingPermission")
    override fun connectToDevice(context: Context, device: BluetoothDevice) {
        if (!hasPermissions(context)) {
            _connectionStatus.value = "Permissions missing to connect."
            return
        }
        _connectionStatus.value = "Connecting to ${device.name ?: device.address}..."
        showDeviceSelectionDialog.value = false
        stopScan(context)

        if (bluetoothAdapter == null) {
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

    override fun renameDevice(address: String, newName: String) {
        viewModelScope.launch {
            val existingDevice = savedBleDeviceDao.getDevice(address)
            val updatedDevice = existingDevice?.copy(name = newName) ?: SavedBleDevice(address, newName)
            savedBleDeviceDao.insertDevice(updatedDevice)
        }
    }

    override fun deleteDevice(address: String) {
        viewModelScope.launch {
            savedBleDeviceDao.deleteDevice(address)
        }
    }

    override fun getBluetoothDevice(address: String): BluetoothDevice? {
        return bluetoothAdapter?.getRemoteDevice(address)
    }


    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address
            val deviceName = gatt.device.name ?: deviceAddress

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _connectionStatus.value = "Connected to $deviceName"
                    bluetoothGatt = gatt
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connectionStatus.value = "Disconnected from $deviceName"
                    _heartRate.value = null
                    gatt.close()
                    bluetoothGatt = null
                }
            } else {
                _connectionStatus.value = "Connection to $deviceName failed"
                _heartRate.value = null
                gatt.close()
                bluetoothGatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionStatus.value = "Services discovered"
                val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
                if (hrService == null) {
                    _connectionStatus.value = "HR Service not found"
                    return
                }
                val hrCharacteristic =
                    hrService.getCharacteristic(HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID)
                if (hrCharacteristic == null) {
                    _connectionStatus.value = "HR Characteristic not found"
                    return
                }

                gatt.setCharacteristicNotification(hrCharacteristic, true)

                val descriptor =
                    hrCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                if (descriptor != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID == characteristic.uuid) {
                handleHrUpdate(parseHeartRate(value))
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID == characteristic.uuid) {
                handleHrUpdate(parseHeartRate(value))
            }
        }
    }

    private fun handleHrUpdate(heartRateValue: Int?) {
        _heartRate.value = heartRateValue
        if (heartRateValue != null) {
            checkAlarms(heartRateValue)
            if (_isMeasuring.value) {
                currentMeasurementData.add(heartRateValue)
                _hrDataPoints.value = currentMeasurementData.toList()
                if (heartRateValue > (_maxHeartRate.value ?: Int.MIN_VALUE)) {
                    _maxHeartRate.value = heartRateValue
                }
            }
        }
    }

    private fun checkAlarms(hr: Int) {
        val settings = currentUserSettings ?: return
        val zoneIndex = settings.targetZoneIndex
        if (zoneIndex == 0) return

        val (min, max, lowAlarm, highAlarm) = when (zoneIndex) {
            1 -> listOf(settings.zone1Min, settings.zone1Max, settings.zone1LowAlarm, settings.zone1HighAlarm)
            2 -> listOf(settings.zone2Min, settings.zone2Max, settings.zone2LowAlarm, settings.zone2HighAlarm)
            3 -> listOf(settings.zone3Min, settings.zone3Max, settings.zone3LowAlarm, settings.zone3HighAlarm)
            4 -> listOf(settings.zone4Min, settings.zone4Max, settings.zone4LowAlarm, settings.zone4HighAlarm)
            5 -> listOf(settings.zone5Min, settings.zone5Max, settings.zone5LowAlarm, settings.zone5HighAlarm)
            else -> return
        }

        if (lowAlarm as Boolean && hr < (min as Int)) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP)
        } else if (highAlarm as Boolean && hr > (max as Int)) {
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_HIGH_L)
        }
    }

    private fun parseHeartRate(data: ByteArray?): Int? {
        if (data == null || data.isEmpty()) return null
        val flags = data[0].toInt()
        val format = if ((flags and 0x01) != 0) BluetoothGattCharacteristic.FORMAT_UINT16 else BluetoothGattCharacteristic.FORMAT_UINT8
        return if (format == BluetoothGattCharacteristic.FORMAT_UINT16) {
            if (data.size >= 3) ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF) else null
        } else {
            if (data.size >= 2) data[1].toInt() and 0xFF else null
        }
    }

    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        // Handled by the other overload in newer APIs, or this one in older ones if manually called
    }

    override fun toggleMeasurement(context: Context) {
        if (_isMeasuring.value) {
            stopMeasurementSession()
        } else {
            resetMeasurementData()
            _isMeasuring.value = true
            measurementStartTimeMillis = System.currentTimeMillis()
            _connectionStatus.value = "Measurement started..."

            measurementJob = viewModelScope.launch {
                heartRate.collect { hr ->
                    if (hr != null && _isMeasuring.value) {
                        // handleHrUpdate already adds to currentMeasurementData
                        if (currentMeasurementData.isNotEmpty()) {
                            _averageHeartRate.value = currentMeasurementData.average().toInt()
                            _maxHeartRate.value = currentMeasurementData.maxOrNull()
                        }
                    }
                }
            }
        }
    }

    private fun resetMeasurementData() {
        _hrDataPoints.value = emptyList()
        _averageHeartRate.value = null
        _maxHeartRate.value = null
        _heartRate.value = null
        _caloriesBurned.value = null
        currentMeasurementData.clear()
        measurementStartTimeMillis = 0L
    }

    private fun stopMeasurementSession() {
        _isMeasuring.value = false
        val duration = if (measurementStartTimeMillis > 0) System.currentTimeMillis() - measurementStartTimeMillis else 0L
        measurementJob?.cancel()
        measurementJob = null

        if (currentMeasurementData.isNotEmpty()) {
            _connectionStatus.value = "Measurement stopped"
            val avgHr = currentMeasurementData.average()
            val timeMinutes = duration / 60000.0

            viewModelScope.launch {
                val settings = userSettingsDao.getSettings().first()
                val calories = calculateCalories(settings, avgHr, timeMinutes)
                _caloriesBurned.value = calories

                val measurementToSave = Measurement(
                    timestamp = System.currentTimeMillis(),
                    heartRateDataPoints = currentMeasurementData.toList(),
                    averageHeartRate = avgHr.toInt(),
                    maxHeartRate = currentMeasurementData.maxOrNull() ?: 0,
                    minHeartRate = currentMeasurementData.minOrNull() ?: 0,
                    durationMillis = duration,
                    caloriesBurned = calories
                )
                measurementDao.insertMeasurement(measurementToSave)
            }
        } else {
            resetMeasurementData()
            _connectionStatus.value = bluetoothGatt?.let { "Connected" } ?: "Disconnected"
        }
    }

    private fun calculateCalories(settings: UserSettings?, avgHr: Double, timeMinutes: Double): Double? {
        if (settings == null || settings.age == null || settings.weight == null || settings.gender == null) return null
        val age = settings.age.toDouble()
        val weight = settings.weight.toDouble()
        return if (settings.gender == "Male") {
            ((age * 0.2017) - (weight * 0.09036) + (avgHr * 0.6309) - 55.0969) * (timeMinutes / 4.184)
        } else {
            ((age * 0.074) - (weight * 0.05741) + (avgHr * 0.4472) - 20.4022) * (timeMinutes / 4.184)
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect(context: Context) {
        if (_isMeasuring.value) stopMeasurementSession()
        if (bluetoothGatt != null) {
            bluetoothGatt?.disconnect()
        } else {
            _connectionStatus.value = "Not connected."
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCleared() {
        super.onCleared()
        bluetoothGatt?.close()
        bluetoothGatt = null
        toneGenerator.release()
    }

    override fun loadHistoricData(measurement: Measurement) {
        if (_isMeasuring.value) stopMeasurementSession()
        _isMeasuring.value = false
        _hrDataPoints.value = measurement.heartRateDataPoints
        _averageHeartRate.value = measurement.averageHeartRate
        _maxHeartRate.value = measurement.maxHeartRate
        _caloriesBurned.value = measurement.caloriesBurned
        _heartRate.value = null
        _connectionStatus.value = "Displaying saved measurement"
    }
}
