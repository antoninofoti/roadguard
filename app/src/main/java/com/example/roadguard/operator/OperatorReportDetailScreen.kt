package com.example.roadguard.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.roadguard.model.DetectionSource
import com.example.roadguard.model.Report
import com.example.roadguard.model.ReportStatus
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Detailed report view for operators with action buttons.
 *
 * Shows:
 * - Report image
 * - Fusion metadata (CV, sensor, fused scores, detection source)
 * - Map with location
 * - Action buttons: Confirm / Reject / Resolve
 * - Notes field for operator comments
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorReportDetailScreen(
    report: Report,
    onConfirm: (String) -> Unit,
    onReject: (String) -> Unit,
    onResolve: (String) -> Unit,
    onBack: () -> Unit
) {
    var notes by remember { mutableStateOf(report.notes) }
    val currentStatus = try {
        ReportStatus.valueOf(report.status)
    } catch (e: Exception) {
        ReportStatus.PENDING
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Report image
            if (report.imageUrl.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    AsyncImage(
                        model = report.imageUrl,
                        contentDescription = "Road damage image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Fusion data card
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Detection Info",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    InfoRow("Damage Type", report.damageType.replaceFirstChar { it.uppercase() }.ifEmpty { "Unknown" })
                    InfoRow("Detection Source", formatSource(report.detectionSource))
                    InfoRow("CV Confidence", "%.1f%%".format(report.cvConfidence * 100))
                    InfoRow("Sensor Confidence", "%.1f%%".format(report.sensorConfidence * 100))
                    InfoRow("Fused Score", "%.1f%%".format(report.fusedScore * 100))
                    InfoRow("Severity", "%.1f%%".format(report.severity * 100))
                    InfoRow("Status", currentStatus.name)

                    report.timestamp?.let { date ->
                        InfoRow("Reported", SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(date))
                    }
                    report.resolvedAt?.let { date ->
                        InfoRow("Resolved", SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(date))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Map
            report.location?.let { geoPoint ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val position = LatLng(geoPoint.latitude, geoPoint.longitude)
                    val cameraPositionState = rememberCameraPositionState {
                        this.position = CameraPosition.fromLatLngZoom(position, 16f)
                    }
                    GoogleMap(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        cameraPositionState = cameraPositionState
                    ) {
                        Marker(
                            state = MarkerState(position = position),
                            title = report.damageType.replaceFirstChar { it.uppercase() }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Operator notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Operator Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            when (currentStatus) {
                ReportStatus.PENDING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onConfirm(notes) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Text("✓ Confirm")
                        }
                        Button(
                            onClick = { onReject(notes) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE53935)
                            )
                        ) {
                            Text("✕ Reject")
                        }
                    }
                }
                ReportStatus.CONFIRMED -> {
                    Button(
                        onClick = { onResolve(notes) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3)
                        )
                    ) {
                        Text("Mark as Resolved")
                    }
                }
                ReportStatus.REJECTED, ReportStatus.RESOLVED -> {
                    Text(
                        text = "This report has been ${currentStatus.name.lowercase()}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatSource(source: String): String {
    return when (source) {
        DetectionSource.DUAL_CONFIRMED.name -> "Dual Confirmed ✓✓"
        DetectionSource.CV_ONLY.name -> "CV Only"
        DetectionSource.SENSOR_ONLY.name -> "Sensor Only"
        DetectionSource.MANUAL.name -> "Manual"
        else -> source
    }
}
