// NewMainScreen.kt
package com.hrrangealert.ui.main

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
import com.hrrangealert.data.Measurement
import java.text.SimpleDateFormat
import androidx.compose.ui.platform.LocalLocale

// Define colors used in your image
val DarkBackground = Color(0xFF1F2125)
val TealArcColor = Color(0xFF00C9B8)
val OrangeArcColor = Color(0xFFFF8C00)
val TextYellow = Color(0xFFFFD700)

@Composable
fun NewMainScreen(
    viewModel: IBleViewModel,
    loadedMeasurement: Measurement? = null,
) {
    val heartRate by viewModel.heartRate.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val isMeasuring by viewModel.isMeasuring.collectAsState()
    val hrDataPoints by viewModel.hrDataPoints.collectAsState()
    val avgHr by viewModel.averageHeartRate.collectAsState()
    val maxHr by viewModel.maxHeartRate.collectAsState()
    val caloriesBurned by viewModel.caloriesBurned.collectAsState()
    
    val hrRangeLower by viewModel.hrTargetRangeLower.collectAsState()
    val hrRangeUpper by viewModel.hrTargetRangeUpper.collectAsState()

    val context = LocalContext.current

    val isShowingHistoricalData = loadedMeasurement != null
    val displayHeartRate = if (isShowingHistoricalData) loadedMeasurement.averageHeartRate else heartRate
    val displayAvgHr = if (isShowingHistoricalData) loadedMeasurement.averageHeartRate else avgHr
    val displayMaxHr = if (isShowingHistoricalData) loadedMeasurement.maxHeartRate else maxHr
    val displayDataPoints = if (isShowingHistoricalData) loadedMeasurement.heartRateDataPoints else hrDataPoints
    val displayCalories = if (isShowingHistoricalData) loadedMeasurement.caloriesBurned else caloriesBurned


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HeartRateCircularDisplay(
                heartRate = displayHeartRate, 
                minRange = hrRangeLower,
                maxRange = hrRangeUpper,
                modifier = Modifier.size(220.dp)
            )

            Spacer(Modifier.height(8.dp))

            if (isShowingHistoricalData) {
                val formattedDate = SimpleDateFormat("MMM dd, yyyy - hh:mm a", LocalLocale.current.platformLocale)
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
                avgHr = displayAvgHr,
                hrRangeMin = hrRangeLower,
                hrRangeMax = hrRangeUpper,
                maxHr = displayMaxHr,
                calories = displayCalories
            )

            Spacer(Modifier.height(24.dp))

            HeartRateGraphPlaceholder(
                dataPoints = displayDataPoints,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
            Text(
                text = if (isShowingHistoricalData) "Historical data" else if (isMeasuring) "Live heart rate" else if (hrDataPoints.isNotEmpty()) "Last measurement" else "Connect to see live heart rate",
                color = Color.Gray,
                fontSize = 12.sp
            )

        }

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
    strokeWidth: Dp = 18.dp,
    backgroundColor: Color = Color.DarkGray,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        val sweepAngle = 270f
        val startAngle = -225f

        val currentHrFloat = heartRate?.toFloat() ?: 0f
        val overallMinHr = 40f
        val overallMaxHr = 180f

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = backgroundColor.copy(alpha = 0.5f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            )

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

            if (currentHrFloat > minRange) {
                val orangeStartValue = minRange.toFloat()
                val orangeEndValue = currentHrFloat

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

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = heartRate?.toString() ?: "--",
                fontSize = 70.sp,
                fontWeight = FontWeight.Light,
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
    calories: Double?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem("Avg HR", avgHr?.toString() ?: "--")
            StatItem("HR Range", "$hrRangeMin-$hrRangeMax")
            StatItem("Max", maxHr?.toString() ?: "--")
        }
        
        if (calories != null) {
            Spacer(Modifier.height(16.dp))
            StatItem(
                label = "Calories Burned",
                value = String.format(LocalLocale.current.platformLocale, "%.1f kcal", calories),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier, icon: @Composable (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
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
            .background(Color.Black.copy(alpha = 0.2f))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (dataPoints.isEmpty()) {
            Text("Graph will appear here", color = Color.Gray)
        } else {
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
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
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
                Text("-10 min", color = Color.Gray, fontSize = 10.sp)
                Text("0 min", color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}
