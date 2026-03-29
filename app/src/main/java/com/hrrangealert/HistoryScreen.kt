package com.hrrangealert // Or your UI package for HistoryScreen

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
import androidx.compose.ui.unit.dp
import com.hrrangealert.data.Measurement
import com.hrrangealert.history.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onLoadMeasurementClicked: (Int) -> Unit, // Callback to navigate with measurement ID
    modifier: Modifier = Modifier
) {
    val measurements by viewModel.measurements.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Measurement History", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (measurements.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No saved measurements yet.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(measurements, key = { it.id }) { measurement ->
                    MeasurementItem(
                        measurement = measurement,
                        onClick = {
                            onLoadMeasurementClicked(measurement.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MeasurementItem(
    measurement: Measurement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Measured on: ${formatTimestamp(measurement.timestamp)}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Avg HR: ${measurement.averageHeartRate ?: "--"} bpm",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Max HR: ${measurement.maxHeartRate ?: "--"} bpm",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Min HR: ${measurement.minHeartRate ?: "--"} bpm",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Duration: ${formatDuration(measurement.durationMillis)}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60)) % 24
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
