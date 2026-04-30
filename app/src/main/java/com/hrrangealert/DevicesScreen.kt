package com.hrrangealert

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hrrangealert.data.SavedBleDevice


@Composable
fun DevicesScreen(
    viewModel: IBleViewModel,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val savedDevices by viewModel.savedDevices.collectAsState()
    val showDialog by viewModel.showDeviceSelectionDialog.collectAsState()

    var deviceToRename by remember { mutableStateOf<SavedBleDevice?>(null) }
    var newDeviceName by remember { mutableStateOf("") }

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Saved Devices", style = MaterialTheme.typography.titleLarge)

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(savedDevices) { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewModel.getBluetoothDevice(device.address)?.let { bluetoothDevice ->
                                    viewModel.connectToDevice(context, bluetoothDevice)
                                }
                            }
                    ) {
                        Text(text = device.name ?: "Unknown", style = MaterialTheme.typography.bodyLarge)
                        Text(text = device.address, style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        IconButton(onClick = {
                            deviceToRename = device
                            newDeviceName = device.name ?: ""
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename")
                        }
                        IconButton(onClick = { viewModel.deleteDevice(device.address) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
                HorizontalDivider()
            }
        }

        if (showDialog && discoveredDevices.isNotEmpty()) {
            DeviceSelectionDialog(
                devices = discoveredDevices,
                onDeviceSelected = { bleDevice ->
                    viewModel.saveDevice(bleDevice)
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
            Text("Scanning for devices...")
        }
    }

    if (deviceToRename != null) {
        AlertDialog(
            onDismissRequest = { deviceToRename = null },
            title = { Text("Rename Device") },
            text = {
                OutlinedTextField(
                    value = newDeviceName,
                    onValueChange = { newDeviceName = it },
                    label = { Text("New Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deviceToRename?.let {
                        viewModel.renameDevice(it.address, newDeviceName)
                    }
                    deviceToRename = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRename = null }) {
                    Text("Cancel")
                }
            }
        )
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
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
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
