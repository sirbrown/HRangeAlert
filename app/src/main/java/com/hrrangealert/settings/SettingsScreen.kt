package com.hrrangealert.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hrrangealert.data.SavedBleDevice

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val userSettings by viewModel.userSettings.collectAsState()
    val savedDevices by viewModel.savedDevices.collectAsState()

    var age by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf<String?>(null) }
    val genderOptions = listOf("Male", "Female", "Other")

    var deviceToRename by remember { mutableStateOf<SavedBleDevice?>(null) }
    var newDeviceName by remember { mutableStateOf("") }

    LaunchedEffect(userSettings) {
        userSettings?.let {
            age = it.age?.toString() ?: ""
            weight = it.weight?.toString() ?: ""
            selectedGender = it.gender
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("User Settings", style = MaterialTheme.typography.headlineMedium)

            OutlinedTextField(
                value = age,
                onValueChange = { age = it },
                label = { Text("Age") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Gender", style = MaterialTheme.typography.bodyLarge)
                Row {
                    genderOptions.forEach { gender ->
                        Row(
                            Modifier
                                .selectable(
                                    selected = (gender == selectedGender),
                                    onClick = { selectedGender = gender }
                                )
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (gender == selectedGender),
                                onClick = { selectedGender = gender }
                            )
                            Text(text = gender, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.saveSettings(age, selectedGender ?: "", weight)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Profile")
            }

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
                        Column(modifier = Modifier.weight(1f)) {
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
