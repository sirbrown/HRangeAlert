package com.hrrangealert.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val userSettings by viewModel.userSettings.collectAsState()
    val scrollState = rememberScrollState()

    var age by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf<String?>(null) }
    var selectedFormula by remember { mutableStateOf("Fox") }
    var targetZoneIndex by remember { mutableIntStateOf(0) }

    // Zone states
    var z1Min by remember { mutableStateOf("") }
    var z1Max by remember { mutableStateOf("") }
    var z1LowAlarm by remember { mutableStateOf(false) }
    var z1HighAlarm by remember { mutableStateOf(false) }

    var z2Min by remember { mutableStateOf("") }
    var z2Max by remember { mutableStateOf("") }
    var z2LowAlarm by remember { mutableStateOf(false) }
    var z2HighAlarm by remember { mutableStateOf(false) }

    var z3Min by remember { mutableStateOf("") }
    var z3Max by remember { mutableStateOf("") }
    var z3LowAlarm by remember { mutableStateOf(false) }
    var z3HighAlarm by remember { mutableStateOf(false) }

    var z4Min by remember { mutableStateOf("") }
    var z4Max by remember { mutableStateOf("") }
    var z4LowAlarm by remember { mutableStateOf(false) }
    var z4HighAlarm by remember { mutableStateOf(false) }

    var z5Min by remember { mutableStateOf("") }
    var z5Max by remember { mutableStateOf("") }
    var z5LowAlarm by remember { mutableStateOf(false) }
    var z5HighAlarm by remember { mutableStateOf(false) }

    LaunchedEffect(userSettings) {
        userSettings?.let {
            age = it.age?.toString() ?: ""
            weight = it.weight?.toString() ?: ""
            selectedGender = it.gender
            selectedFormula = it.mhrFormula
            targetZoneIndex = it.targetZoneIndex
            
            z1Min = it.zone1Min.toString(); z1Max = it.zone1Max.toString()
            z1LowAlarm = it.zone1LowAlarm; z1HighAlarm = it.zone1HighAlarm
            
            z2Min = it.zone2Min.toString(); z2Max = it.zone2Max.toString()
            z2LowAlarm = it.zone2LowAlarm; z2HighAlarm = it.zone2HighAlarm

            z3Min = it.zone3Min.toString(); z3Max = it.zone3Max.toString()
            z3LowAlarm = it.zone3LowAlarm; z3HighAlarm = it.zone3HighAlarm

            z4Min = it.zone4Min.toString(); z4Max = it.zone4Max.toString()
            z4LowAlarm = it.zone4LowAlarm; z4HighAlarm = it.zone4HighAlarm

            z5Min = it.zone5Min.toString(); z5Max = it.zone5Max.toString()
            z5LowAlarm = it.zone5LowAlarm; z5HighAlarm = it.zone5HighAlarm
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("User Profile", style = MaterialTheme.typography.headlineMedium)

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

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Gender: ", style = MaterialTheme.typography.bodyLarge)
                RadioButton(selected = selectedGender == "Male", onClick = { selectedGender = "Male" })
                Text("Male")
                RadioButton(selected = selectedGender == "Female", onClick = { selectedGender = "Female" })
                Text("Female")
            }

            HorizontalDivider()

            Text("MHR Formula", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = selectedFormula == "Fox", onClick = { selectedFormula = "Fox" })
                Text("Fox (220-Age)")
                Spacer(Modifier.width(16.dp))
                RadioButton(selected = selectedFormula == "Tanaka", onClick = { selectedFormula = "Tanaka" })
                Text("Tanaka")
            }

            Button(onClick = {
                val ageInt = age.toIntOrNull() ?: 30
                val zones = viewModel.calculateZones(ageInt, selectedFormula)
                z1Min = zones[0].first.toString(); z1Max = zones[0].second.toString()
                z2Min = zones[1].first.toString(); z2Max = zones[1].second.toString()
                z3Min = zones[2].first.toString(); z3Max = zones[2].second.toString()
                z4Min = zones[3].first.toString(); z4Max = zones[3].second.toString()
                z5Min = zones[4].first.toString(); z5Max = zones[4].second.toString()
            }) {
                Text("Auto-Calculate Zones")
            }

            HorizontalDivider()

            Text("Select Target Zone", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                (0..5).forEach { index ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RadioButton(selected = targetZoneIndex == index, onClick = { targetZoneIndex = index })
                        Text(if (index == 0) "None" else "Z$index", fontSize = 12.sp)
                    }
                }
            }

            HorizontalDivider()

            Text("Heart Rate Zones & Alarms", style = MaterialTheme.typography.titleLarge)

            ZoneEditRow("Zone 1 (Warm up)", z1Min, { z1Min = it }, z1Max, { z1Max = it }, z1LowAlarm, { z1LowAlarm = it }, z1HighAlarm, { z1HighAlarm = it })
            ZoneEditRow("Zone 2 (Fat Burn)", z2Min, { z2Min = it }, z2Max, { z2Max = it }, z2LowAlarm, { z2LowAlarm = it }, z2HighAlarm, { z2HighAlarm = it })
            ZoneEditRow("Zone 3 (Aerobic)", z3Min, { z3Min = it }, z3Max, { z3Max = it }, z3LowAlarm, { z3LowAlarm = it }, z3HighAlarm, { z3HighAlarm = it })
            ZoneEditRow("Zone 4 (Anaerobic)", z4Min, { z4Min = it }, z4Max, { z4Max = it }, z4LowAlarm, { z4LowAlarm = it }, z4HighAlarm, { z4HighAlarm = it })
            ZoneEditRow("Zone 5 (Red Line)", z5Min, { z5Min = it }, z5Max, { z5Max = it }, z5LowAlarm, { z5LowAlarm = it }, z5HighAlarm, { z5HighAlarm = it })

            Button(
                onClick = {
                    viewModel.saveSettings(
                        age, selectedGender ?: "Male", weight, selectedFormula,
                        targetZoneIndex,
                        z1Min.toIntOrNull() ?: 0, z1Max.toIntOrNull() ?: 0, z1LowAlarm, z1HighAlarm,
                        z2Min.toIntOrNull() ?: 0, z2Max.toIntOrNull() ?: 0, z2LowAlarm, z2HighAlarm,
                        z3Min.toIntOrNull() ?: 0, z3Max.toIntOrNull() ?: 0, z3LowAlarm, z3HighAlarm,
                        z4Min.toIntOrNull() ?: 0, z4Max.toIntOrNull() ?: 0, z4LowAlarm, z4HighAlarm,
                        z5Min.toIntOrNull() ?: 0, z5Max.toIntOrNull() ?: 0, z5LowAlarm, z5HighAlarm
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
            ) {
                Text("Save Settings")
            }
        }
    }
}

@Composable
fun ZoneEditRow(
    label: String,
    minVal: String, onMinChange: (String) -> Unit,
    maxVal: String, onMaxChange: (String) -> Unit,
    lowAlarm: Boolean, onLowAlarmChange: (Boolean) -> Unit,
    highAlarm: Boolean, onHighAlarmChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = minVal,
                onValueChange = onMinChange,
                label = { Text("Min") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = maxVal,
                onValueChange = onMaxChange,
                label = { Text("Max") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = lowAlarm, onCheckedChange = onLowAlarmChange)
            Text("Low Alarm", fontSize = 12.sp)
            Spacer(Modifier.width(16.dp))
            Checkbox(checked = highAlarm, onCheckedChange = onHighAlarmChange)
            Text("High Alarm", fontSize = 12.sp)
        }
    }
}
