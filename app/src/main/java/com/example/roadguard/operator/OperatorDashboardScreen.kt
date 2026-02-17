package com.example.roadguard.operator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roadguard.model.DetectionSource
import com.example.roadguard.model.Report
import com.example.roadguard.model.ReportStatus
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Operator dashboard showing reports filtered by status.
 *
 * Displays a filter bar (PENDING / CONFIRMED / REJECTED / RESOLVED)
 * and a scrollable list of report cards with fusion metadata.
 */
@Composable
fun OperatorDashboardScreen(
    operatorViewModel: OperatorViewModel = viewModel()
) {
    val reports by operatorViewModel.reports.collectAsState()
    val currentFilter by operatorViewModel.currentFilter.collectAsState()
    val isLoading by operatorViewModel.isLoading.collectAsState()
    val selectedReport by operatorViewModel.selectedReport.collectAsState()
    val operationMessage by operatorViewModel.operationMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar for operation messages
    LaunchedEffect(operationMessage) {
        operationMessage?.let {
            snackbarHostState.showSnackbar(it)
            operatorViewModel.clearMessage()
        }
    }

    // Show detail screen if a report is selected
    if (selectedReport != null) {
        OperatorReportDetailScreen(
            report = selectedReport!!,
            onConfirm = { notes -> operatorViewModel.confirmReport(selectedReport!!.id, notes) },
            onReject = { notes -> operatorViewModel.rejectReport(selectedReport!!.id, notes) },
            onResolve = { notes -> operatorViewModel.resolveReport(selectedReport!!.id, notes) },
            onBack = { operatorViewModel.clearSelection() }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Text(
                text = "Operator Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )

            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportStatus.values().forEach { status ->
                    FilterChip(
                        selected = currentFilter == status,
                        onClick = { operatorViewModel.loadReports(status) },
                        label = {
                            Text(
                                text = status.name,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Report count
            Text(
                text = "${reports.size} report(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (reports.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No ${currentFilter.name.lowercase()} reports",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(reports) { report ->
                        ReportCard(
                            report = report,
                            onClick = { operatorViewModel.selectReport(report) }
                        )
                    }
                    // Bottom padding
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * A card displaying a report's key info: type, source, severity, time, location.
 */
@Composable
private fun ReportCard(report: Report, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Severity indicator dot
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(severityColor(report.fusedScore)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${(report.fusedScore * 100).toInt()}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Report info
            Column(modifier = Modifier.weight(1f)) {
                // Damage type + detection source
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = report.damageType.replaceFirstChar { it.uppercase() }.ifEmpty { "Unknown" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    SourceBadge(source = report.detectionSource)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Confidence scores
                Text(
                    text = "CV: %.0f%%  Sensor: %.0f%%  Fused: %.0f%%".format(
                        report.cvConfidence * 100,
                        report.sensorConfidence * 100,
                        report.fusedScore * 100
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Timestamp
                report.timestamp?.let { date ->
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Location
                report.location?.let {
                    Text(
                        text = "📍 %.4f, %.4f".format(it.latitude, it.longitude),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Small badge showing the detection source with a color indicator.
 */
@Composable
private fun SourceBadge(source: String) {
    val (color, label) = when (source) {
        DetectionSource.DUAL_CONFIRMED.name -> Pair(Color(0xFF4CAF50), "DUAL")
        DetectionSource.CV_ONLY.name -> Pair(Color(0xFF2196F3), "CV")
        DetectionSource.SENSOR_ONLY.name -> Pair(Color(0xFFFF9800), "SENSOR")
        else -> Pair(Color(0xFF9E9E9E), "MANUAL")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/**
 * Map severity (0.0 - 1.0) to a color.
 */
private fun severityColor(score: Float): Color {
    return when {
        score >= 0.75f -> Color(0xFFE53935)  // Red — severe
        score >= 0.50f -> Color(0xFFFF9800)  // Orange — moderate
        score >= 0.25f -> Color(0xFFFFC107)  // Yellow — low
        else -> Color(0xFF9E9E9E)             // Grey — minimal
    }
}
