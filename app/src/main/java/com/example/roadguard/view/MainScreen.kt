package com.example.roadguard.view

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.roadguard.tflite.PotholeDetectionHelper
import com.google.firebase.firestore.GeoPoint
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.navigation.NavController

@Suppress("UNUSED_PARAMETER")
@Composable
fun MainScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    val potholeDetectionHelper = PotholeDetectionHelper(context)

    // Observe severity in real-time
    val severity by viewModel.currentSeverity?.collectAsState(initial = 0f) ?: mutableStateOf(0f)


    // Initialize sensor helper
    LaunchedEffect(Unit) {
        viewModel.initializeSensorHelper(context)
    }

    // Start/stop sensor listening based on lifecycle
    DisposableEffect(Unit) {
        viewModel.startSensorListening()
        onDispose {
            viewModel.stopSensorListening()
        }
    }


    // Observe report saving state
    val isSaving = viewModel.isSavingReport.value
    val saveError = viewModel.saveReportError.value
    val saveSuccess = viewModel.saveReportSuccess.value

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            Toast.makeText(context, "Report saved successfully!", Toast.LENGTH_SHORT).show()
            viewModel.saveReportSuccess.value = false // Reset state
        }
    }

    LaunchedEffect(saveError) {
        saveError?.let {
            Toast.makeText(context, "Error: $it", Toast.LENGTH_LONG).show()
            viewModel.saveReportError.value = null // Reset state
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.wrapContentSize()) {
            viewModel.imageBitmap.value?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Selected Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
                Canvas(modifier = Modifier.matchParentSize()) {
                    viewModel.detections.value.forEach { detection ->
                        drawRect(
                            color = Color.Red,
                            topLeft = androidx.compose.ui.geometry.Offset(detection.boundingBox.left, detection.boundingBox.top),
                            size = androidx.compose.ui.geometry.Size(detection.boundingBox.width(), detection.boundingBox.height()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display real-time severity
        SeverityIndicator(severity = severity)

        Spacer(modifier = Modifier.height(16.dp))

        // Button for live detection
        Button(onClick = {
            context.startActivity(Intent(context, com.example.roadguard.ui.LivePotholeDetectionActivity::class.java))
        }) {
            Text("Live Pothole Detection")
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.detections.value.isNotEmpty()) {
            Button(
                onClick = {
                    viewModel.imageBitmap.value?.let {
                        viewModel.addReport(it, context)
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Save Report")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Remove the old image picker button and the unused intent-based detection button
        // (Commented out for reference)
        // Button(onClick = { launcher.launch("image/*") }) {
        //     Text("Select Image")
        // }
        // Spacer(modifier = Modifier.height(16.dp))
        // Button(onClick = {
        //     context.startActivity(Intent(context, com.example.roadguard.PotholeDetectionActivity::class.java))
        // }) {
        //     Text("Open Pothole Detection")
        // }
    }
}

@Composable
fun SeverityIndicator(severity: Float) {
    val severityColor = when {
        severity < 2f -> Color.Green
        severity < 3f -> Color.Yellow
        severity < 4f -> Color(0xFFFFA500) // Orange
        else -> Color.Red
    }
    val progress = (severity / 5f).coerceIn(0f, 1f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Current Severity: ${String.format(Locale.US, "%.2f", severity)}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(100.dp),
                color = Color.LightGray,
                strokeWidth = 8.dp
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(100.dp),
                color = severityColor,
                strokeWidth = 8.dp,
                strokeCap = StrokeCap.Round
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
