package com.hrrangealert

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class BleViewModel : ViewModel() {

    private val _TAG = "BleViewModel"

    // Private MutableStateFlow that can be updated
    private val _connectionStatus = MutableStateFlow("Disconnected")
    // Publicly exposed StateFlow (read-only)
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()


    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredBleDevice>> = _discoveredDevices

    val showDeviceSelectionDialog = MutableStateFlow(false)

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private val scanHandler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000 // Stops scanning after 10 seconds.

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    // Filter for devices advertising the Heart Rate Service
    private val scanFilters: List<ScanFilter> = listOf(
        ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()
    )


    fun init(context: Context) {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _connectionStatus.value = "Bluetooth is not enabled or not supported."
            // Consider prompting user to enable Bluetooth
            return
        }
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    fun hasPermissions(context: Context): Boolean {
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
    fun updateConnectionStatus(newStatus: String) {
        _connectionStatus.value = newStatus
    }

    @SuppressLint("MissingPermission") // Permissions checked by hasPermissions
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

    @SuppressLint("MissingPermission") // Permissions checked by hasPermissions
    fun stopScan(context: Context) {
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
                _discoveredDevices.value = _discoveredDevices.value + discoveredDevice
                Log.d(_TAG, "Found BLE Device: Name: $deviceName, Address: $deviceAddress")
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach { result ->
                val device = result.device
                val deviceName = device.name ?: "Unknown Device"
                val deviceAddress = device.address
                val existingDevice = _discoveredDevices.value.find { it.address == deviceAddress }
                if (existingDevice == null) {
                    _discoveredDevices.value = _discoveredDevices.value + DiscoveredBleDevice(
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
    fun connectToDevice(context: Context, device: BluetoothDevice) {
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
                        this@BleViewModel.disconnect(ContextUtils.getApplicationContext()) // Pass context if your disconnect needs it for some reason
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
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    val descriptorWriteSuccess = gatt.writeDescriptor(descriptor)
                    if (!descriptorWriteSuccess) {
                        Log.e(_TAG, "Failed to write CCCD descriptor to enable notifications")
                        _connectionStatus.value = "Failed to enable HR notifications (write)"
                    } else {
                        Log.i(_TAG, "CCCD descriptor write initiated for HR notifications")
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

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID == characteristic.uuid) {
                val heartRateValue = parseHeartRate(characteristic.value)
                _heartRate.value = heartRateValue
                Log.d(_TAG, "Heart Rate Updated: $heartRateValue")
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID == characteristic.uuid) {
                    val heartRateValue = parseHeartRate(characteristic.value)
                    _heartRate.value = heartRateValue
                     Log.d(_TAG, "Heart Rate Read: $heartRateValue")
                }
            }
        }

        // Callback for descriptor write (confirming notification enabled)
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

    @SuppressLint("MissingPermission") // Permissions checked by hasPermissions
    fun disconnect(context: Context) {
        if (!hasPermissions(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.w(_TAG, "Missing BLUETOOTH_CONNECT permission to disconnect on API 31+")
            // May not be able to disconnect programmatically.
        }
        bluetoothGatt?.disconnect()
        // gatt?.close() will be called in onConnectionStateChange when disconnected
        _connectionStatus.value = "Disconnecting..."
    }

    override fun onCleared() {
        super.onCleared()
        stopScan(ContextUtils.getApplicationContext()) // Requires application context or pass activity context
        bluetoothGatt?.close() // Ensure GATT is closed
        bluetoothGatt = null
        Log.d(_TAG, "ViewModel cleared, BLE resources released.")
    }

    // Helper to get application context if needed, otherwise pass it around.
    // Be careful with static context references.
    object ContextUtils {
        @SuppressLint("StaticFieldLeak")
        private var appContext: Context? = null
        fun init(context: Context) {
            appContext = context.applicationContext
        }
        fun getApplicationContext(): Context {
            return appContext ?: throw IllegalStateException("ContextUtils not initialized. Call init() first.")
        }
    }
}