package com.hrrangealert

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import com.hrrangealert.ui.theme.HRRangeAlertTheme

class MainActivity : ComponentActivity() {
    private val bleViewModel: BleViewModel by viewModels()

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach {
                if (!it.value) allGranted = false
            }
            if (allGranted) {
                bleViewModel.init(this) // Initialize after permissions granted
                // You might want to automatically start a scan here or let the user click
                bleViewModel.startScan(this)
            } else {
                // Handle permission denial (e.g., show a message to the user)
                bleViewModel.updateConnectionStatus("Permissions denied. Cannot scan for BLE devices.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BleViewModel.ContextUtils.init(applicationContext) // Initialize context util for ViewModel

        enableEdgeToEdge()
        setContent {
            HRRangeAlertTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HeartRateBleScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = bleViewModel,
                        onRequestPermissions = { requestBlePermissions() }
                    )
                }
            }
        }
        // Request permissions when the activity is created
        requestBlePermissions() // Or move this to a button click
    }

    private fun requestBlePermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION // Optional for S+, but good for consistency
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION // Required for scanning pre-S
            )
        }

        val permissionsToRequest = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Permissions are already granted
            bleViewModel.init(this)
            // Optionally auto-start scan or wait for button
        }
    }
}

@Composable
fun HeartRateBleScreen(
    modifier: Modifier = Modifier,
    viewModel: BleViewModel,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val heartRate by viewModel.heartRate.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val showDialog by viewModel.showDeviceSelectionDialog.collectAsState() // collectAsState for flow

    // Check permissions on compose start if not already handled
    LaunchedEffect(Unit) {
        if (!viewModel.hasPermissions(context)) {
            onRequestPermissions()
        } else {
            viewModel.init(context) // Init if permissions were already there
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "BLE Heart Rate Monitor", style = MaterialTheme.typography.headlineSmall)

        Button(onClick = {
            if (viewModel.hasPermissions(context)) {
                viewModel.startScan(context)
            } else {
                onRequestPermissions()
            }
        }) {
            Text("Scan for HRM Devices")
        }

        Button(
            onClick = { viewModel.disconnect(context) },
            enabled = connectionStatus.startsWith("Connected") || connectionStatus.startsWith("Receiving")
        ) {
            Text("Disconnect")
        }

        Text(text = "Status: $connectionStatus")

        Text(
            text = "Heart Rate: ${heartRate ?: "--"} BPM",
            style = MaterialTheme.typography.titleLarge
        )

        if (showDialog && discoveredDevices.isNotEmpty()) {
            DeviceSelectionDialog(
                devices = discoveredDevices,
                onDeviceSelected = { bleDevice ->
                    viewModel.connectToDevice(context, bleDevice.device)
                },
                onDismiss = {
                    viewModel.showDeviceSelectionDialog.value = false // Close dialog
                    if (!connectionStatus.startsWith("Connected") && !connectionStatus.startsWith("Connecting")) {
                        viewModel.updateConnectionStatus("Device selection cancelled.") // Update status = "Device selection cancelled."
                    }
                }
            )
        }
    }
}

@Composable
fun DeviceSelectionDialog(
    devices: List<DiscoveredBleDevice>,
    onDeviceSelected: (DiscoveredBleDevice) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select HRM Device", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                if (devices.isEmpty()) {
                    Text("No devices found yet...")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) { // Limit height
                        items(devices) { device ->
                            Text(
                                text = "${device.name ?: "Unknown"} (${device.address})",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDeviceSelected(device) }
                                    .padding(vertical = 12.dp)
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HeartRateBleScreenPreview() {
    HRRangeAlertTheme {
        HeartRateBleScreen(viewModel = BleViewModel(), onRequestPermissions = {})
    }
}