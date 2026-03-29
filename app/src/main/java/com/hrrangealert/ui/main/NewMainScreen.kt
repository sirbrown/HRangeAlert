// NewMainScreen.kt
package com.hrrangealert.ui.main // Adjust package as needed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrrangealert.IBleViewModel
import com.hrrangealert.data.Measurement // If you create a model for loaded data
import java.text.SimpleDateFormat // For formatting date/time of loaded measurement
import java.util.Locale

// Define colors used in your image
val DarkBackground = Color(0xFF1F2125) // Approx
val TealArcColor = Color(0xFF00C9B8) // Approx
val OrangeArcColor = Color(0xFFFF8C00) // Approx
val TextYellow = Color(0xFFFFD700) // Approx High/Low text

@Composable
fun NewMainScreen(
    viewModel: IBleViewModel,
    loadedMeasurement: Measurement? = null, // For displaying historic data
) {
    val heartRate by viewModel.heartRate.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isMeasuring by viewModel.isMeasuring.collectAsState()
    val hrDataPoints by viewModel.hrDataPoints.collectAsState() // For the graph
    val avgHr by viewModel.averageHeartRate.collectAsState()
    val maxHr by viewModel.maxHeartRate.collectAsState()
    // You'll need to define how hrRange is determined. For now, a placeholder.
    val hrRangeLower by viewModel.hrTargetRangeLower.collectAsState()
    val hrRangeUpper by viewModel.hrTargetRangeUpper.collectAsState()

    val context = LocalContext.current

    // --- STEP 2: Determine if we are in historical view mode ---
    val isShowingHistoricalData = loadedMeasurement != null
    // If showing historical data, there is no single "current" HR, so we show the average.
    val displayHeartRate = if (isShowingHistoricalData) loadedMeasurement.averageHeartRate else heartRate


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground) // Use the dark background color
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top section: Circular HR display and stats
        Column(
            modifier = Modifier.weight(1f), // Takes up available space pushing button down
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center // Center content vertically in this section
        ) {
            HeartRateCircularDisplay(
                heartRate = heartRate, // Use live HR for now
                minRange = hrRangeLower, // Example min for color coding
                maxRange = hrRangeUpper,  // Example max for color coding
                modifier = Modifier.size(220.dp) // Adjust size as needed
            )

            Spacer(Modifier.height(8.dp)) // Reduced spacer

            if (isShowingHistoricalData) {
                val formattedDate = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
                    .format(loadedMeasurement.timestamp)
                Text(
                    text = "Saved: $formattedDate",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                val hrStatusText = when {
                    heartRate == null -> ""
                    heartRate!! > hrRangeUpper -> "High"
                    heartRate!! < hrRangeLower -> "Low"
                    else -> "Normal"
                }
                if (hrStatusText.isNotEmpty()) {
                    Text(
                        text = hrStatusText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextYellow
                    )
                }
            }


            Spacer(Modifier.height(24.dp))

            StatsRow(
                avgHr = avgHr, // Use live/session avg for now
                hrRangeMin = hrRangeLower,
                hrRangeMax = hrRangeUpper,
                maxHr = maxHr // Use live/session max for now
            )

            Spacer(Modifier.height(24.dp))

            // Graph Placeholder
            HeartRateGraphPlaceholder(
                dataPoints = hrDataPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp) // Adjust height as needed
            )
            Text(
                text = if (isMeasuring) "Live heart rate" else if (hrDataPoints.isNotEmpty()) "Last measurement" else "Connect to see live heart rate",
                color = Color.Gray,
                fontSize = 12.sp
            )

        }

        // --- STEP 4: Hide the button in historical view ---
        // You cannot start/stop a measurement that has already been recorded.
        if (!isShowingHistoricalData) {
            Button(
                onClick = { viewModel.toggleMeasurement(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMeasuring) OrangeArcColor else TealArcColor
                )
            ) {
                Text(
                    text = if (isMeasuring) "Stop Measurement" else "Start Measurement",
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        }

        // Connection status (optional, could be in top bar or less prominent)
        Text(
            text = "Status: $connectionStatus",
            color = Color.LightGray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
fun HeartRateCircularDisplay(
    heartRate: Int?,
    minRange: Int,
    maxRange: Int,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 18.dp, // Thickness of the arcs
    backgroundColor: Color = Color.DarkGray, // Color of the background circle arc
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        val sweepAngle = 270f // For the 3/4 circle look
        val startAngle = -225f // Start from bottom-left part of circle

        val currentHrFloat = heartRate?.toFloat() ?: 0f
        // Calculate progress within a broader typical HR range for the arc (e.g., 40-180 bpm)
        // The color segmentation (teal/orange) is based on minRange/maxRange
        val overallMinHr = 40f
        val overallMaxHr = 180f
        val progress = ((currentHrFloat - overallMinHr) / (overallMaxHr - overallMinHr)).coerceIn(0f, 1f)
        val progressAngle = sweepAngle * progress

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Background Arc
            drawArc(
                color = backgroundColor.copy(alpha = 0.5f), // Slightly transparent background
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )

            // Teal part (up to minRange or current HR if below minRange)
            val tealEndValue = minOf(currentHrFloat, minRange.toFloat())
            val tealProgress = ((tealEndValue - overallMinHr) / (overallMaxHr - overallMinHr)).coerceIn(0f, 1f)
            val tealSweep = sweepAngle * tealProgress
            if (tealSweep > 0) {
                drawArc(
                    color = TealArcColor,
                    startAngle = startAngle,
                    sweepAngle = tealSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                )
            }

            // Orange part (from minRange to current HR if above minRange, up to maxRange or current HR)
            if (currentHrFloat > minRange) {
                val orangeStartValue = minRange.toFloat()
                val orangeEndValue = currentHrFloat // We want the orange to go up to current HR
                // val orangeEndValue = minOf(currentHrFloat, maxRange.toFloat()) // if you want orange to cap at maxRange visually

                val orangeStartProgress = ((orangeStartValue - overallMinHr) / (overallMaxHr - overallMinHr)).coerceIn(0f, 1f)
                val orangeEndProgress = ((orangeEndValue - overallMinHr) / (overallMaxHr - overallMinHr)).coerceIn(0f, 1f)

                val orangeStartDrawAngle = sweepAngle * orangeStartProgress
                val orangeSweep = (sweepAngle * orangeEndProgress) - orangeStartDrawAngle

                if (orangeSweep > 0) {
                    drawArc(
                        color = OrangeArcColor,
                        startAngle = startAngle + orangeStartDrawAngle,
                        sweepAngle = orangeSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Text in the center
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = heartRate?.toString() ?: "--",
                fontSize = 70.sp, // Large text for HR
                fontWeight = FontWeight.Light, // As in image
                color = Color.White
            )
            Text(
                text = "HEART RATE",
                fontSize = 14.sp,
                color = Color.LightGray
            )
        }
    }
}


@Composable
fun StatsRow(
    avgHr: Int?,
    hrRangeMin: Int,
    hrRangeMax: Int,
    maxHr: Int?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatItem("Avg HR", avgHr?.toString() ?: "--")
        StatItem("HR Range", "$hrRangeMin-$hrRangeMax")
        StatItem("Max", maxHr?.toString() ?: "--")
    }
}

@Composable
fun StatItem(label: String, value: String, icon: @Composable (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Icon (Optional, if you want to add icons like in the image)
        // e.g. Icon(Icons.Filled.FavoriteBorder, contentDescription = "Average Heart Rate", tint = Color.LightGray)
        if (icon != null) {
            icon()
            Spacer(Modifier.height(4.dp))
        }
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(text = label, fontSize = 12.sp, color = Color.LightGray)
    }
}

@Composable
fun HeartRateGraphPlaceholder(dataPoints: List<Int>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.2f)) // Darker placeholder background
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (dataPoints.isEmpty()) {
            Text("Graph will appear here", color = Color.Gray)
        } else {
            // Basic line graph placeholder - A real graph is more complex
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (dataPoints.size > 1) {
                    val xStep = size.width / (dataPoints.size - 1)
                    val minVal = dataPoints.minOrNull()?.toFloat() ?: 50f
                    val maxVal = dataPoints.maxOrNull()?.toFloat() ?: 150f
                    val valueRange = (maxVal - minVal).takeIf { it > 0 } ?: 1f

                    for (i in 0 until dataPoints.size - 1) {
                        val x1 = i * xStep
                        val y1 = size.height - ((dataPoints[i] - minVal) / valueRange * size.height)
                        val x2 = (i + 1) * xStep
                        val y2 = size.height - ((dataPoints[i+1] - minVal) / valueRange * size.height)
                        drawLine(
                            color = OrangeArcColor,
                            start = Offset(x1, y1.toFloat()),
                            end = Offset(x2, y2.toFloat()),
                            strokeWidth = 3f
                        )
                    }
                } else if (dataPoints.isNotEmpty()){
                    drawCircle(
                        color = OrangeArcColor,
                        radius = 5f,
                        center = Offset(size.width / 2, size.height / 2)
                    )
                }
            }
            Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter), horizontalArrangement = Arrangement.SpaceBetween){
                Text("-10 min", color = Color.Gray, fontSize = 10.sp) // Placeholder time labels
                Text("0 min", color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}
