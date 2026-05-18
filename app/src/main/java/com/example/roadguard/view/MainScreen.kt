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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.example.roadguard.tflite.PotholeDetectionHelper
import com.google.firebase.firestore.GeoPoint
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale
import androidx.navigation.NavController
import com.example.roadguard.data.local.RoadGuardDatabase
import com.example.roadguard.ml.FederatedFusionManager
import com.example.roadguard.network.FirebaseFLCoordinator
import com.example.roadguard.ui.FLStatusCard
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Suppress("UNUSED_PARAMETER")
@Composable
fun MainScreen(viewModel: MainViewModel, navController: NavController) {
    val context = LocalContext.current
    // Remember the helper across recompositions and close it when the composable leaves.
    val potholeDetectionHelper = remember(context) { PotholeDetectionHelper(context) }
    DisposableEffect(potholeDetectionHelper) {
        onDispose { potholeDetectionHelper.close() }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = { navController.navigate("settings") },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
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

            // Federated Learning Status Card (Prompt 9B)
            val app = context.applicationContext as com.example.roadguard.RoadGuardApp
            val fusionManager = app.fusionManager
            val coordinator = app.flCoordinator
            
            val sharedPrefs = remember { context.getSharedPreferences("roadguard_fusion_prefs", 0) }
            val isPersonalizationEnabled = sharedPrefs.getBoolean("personalization_enabled", false)

            if (isPersonalizationEnabled) {
                val db = app.database
                FLStatusCard(coordinator, fusionManager, db)
                Spacer(modifier = Modifier.height(16.dp))
            }

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
        }
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
