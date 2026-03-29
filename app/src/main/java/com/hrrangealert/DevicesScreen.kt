package com.hrrangealert // Or your appropriate package

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog


@Composable
fun DevicesScreen(
    viewModel: IBleViewModel,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val showDialog by viewModel.showDeviceSelectionDialog.collectAsState()

    // Re-use the relevant parts of your original HeartRateBleScreen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "Manage HRM Devices", style = MaterialTheme.typography.headlineSmall)

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

        if (showDialog && discoveredDevices.isNotEmpty()) {
            DeviceSelectionDialog( // Your existing dialog
                devices = discoveredDevices,
                onDeviceSelected = { bleDevice ->
                    viewModel.connectToDevice(context, bleDevice.device)
                },
                onDismiss = {
                    viewModel.showDeviceSelectionDialog.value = false
                    if (!connectionStatus.startsWith("Connected") && !connectionStatus.startsWith("Connecting")) {
                        viewModel.updateConnectionStatus("Device selection cancelled.")
                    }
                }
            )
        } else if (showDialog) {
            Text("Scanning for devices...") // Or handle empty discoveredDevices in dialog
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