package com.example.roadguard.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Analytics dashboard screen.
 *
 * Sections:
 * 1. Summary stats (cards)
 * 2. Monthly trend chart (Compose Canvas line chart)
 * 3. Top priority zones
 * 4. Damage clusters
 */
@Composable
fun AnalyticsScreen(analyticsViewModel: AnalyticsViewModel = viewModel()) {
    val isLoading by analyticsViewModel.isLoading.collectAsState()
    val summary by analyticsViewModel.summary.collectAsState()
    val priorityZones by analyticsViewModel.priorityZones.collectAsState()
    val clusters by analyticsViewModel.clusters.collectAsState()
    val forecast by analyticsViewModel.forecast.collectAsState()
    val error by analyticsViewModel.error.collectAsState()

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Analytics",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { analyticsViewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        // Error
        error?.let { errMsg ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = errMsg,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Summary stats
        summary?.let { s ->
            item { SummaryCards(s) }
        }

        // Trend chart
        forecast?.let { f ->
            item { TrendChart(f) }
        }

        // Priority zones
        if (priorityZones.isNotEmpty()) {
            item {
                Text(
                    text = "Priority Zones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(priorityZones.take(5)) { zone ->
                PriorityZoneCard(zone)
            }
        }

        // Clusters
        if (clusters.isNotEmpty()) {
            item {
                Text(
                    text = "Damage Clusters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(clusters.take(5)) { cluster ->
                ClusterCard(cluster)
            }
        }

        // Bottom padding
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ==================== Summary Cards ====================

@Composable
private fun SummaryCards(summary: AnalyticsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1: Total + Avg Score
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "Total Reports",
                value = "${summary.totalReports}",
                color = Color(0xFF2196F3),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Avg Fused Score",
                value = "%.0f%%".format(summary.avgFusedScore * 100),
                color = Color(0xFFFF9800),
                modifier = Modifier.weight(1f)
            )
        }
        // Row 2: Pending + Confirmed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "Pending",
                value = "${summary.pendingReports}",
                color = Color(0xFFFFC107),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Confirmed",
                value = "${summary.confirmedReports}",
                color = Color(0xFF4CAF50),
                modifier = Modifier.weight(1f)
            )
        }
        // Row 3: Resolved + Dual %
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "Resolved",
                value = "${summary.resolvedReports}",
                color = Color(0xFF9E9E9E),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Dual Confirmed",
                value = "%.0f%%".format(summary.dualConfirmedPercent),
                color = Color(0xFF673AB7),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ==================== Trend Chart ====================

@Composable
private fun TrendChart(forecast: DegradationForecast) {
    val trendColor = when (forecast.trend) {
        TrendDirection.IMPROVING -> Color(0xFF4CAF50)
        TrendDirection.STABLE -> Color(0xFF2196F3)
        TrendDirection.DEGRADING -> Color(0xFFE53935)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly Trend",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(trendColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = forecast.trend.name,
                        color = trendColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Predicted next month: %.0f reports".format(forecast.predictedNextMonth),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas line chart
            val data = forecast.monthlyTrend
            if (data.isNotEmpty()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    drawTrendChart(data, trendColor)
                }
            }
        }
    }
}

private fun DrawScope.drawTrendChart(data: List<TrendPoint>, lineColor: Color) {
    val padding = 32f
    val chartWidth = size.width - padding * 2
    val chartHeight = size.height - padding * 2

    val maxCount = (data.maxOfOrNull { it.reportCount } ?: 1).coerceAtLeast(1)
    val stepX = if (data.size > 1) chartWidth / (data.size - 1) else chartWidth

    // Grid lines
    for (i in 0..3) {
        val y = padding + chartHeight * (1f - i / 3f)
        drawLine(
            color = Color.LightGray.copy(alpha = 0.5f),
            start = Offset(padding, y),
            end = Offset(size.width - padding, y),
            strokeWidth = 1f
        )
    }

    // Data points and line
    if (data.size >= 2) {
        val path = Path()
        data.forEachIndexed { index, point ->
            val x = padding + index * stepX
            val y = padding + chartHeight * (1f - point.reportCount.toFloat() / maxCount)

            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)

            // Draw dot
            drawCircle(
                color = lineColor,
                radius = 4f,
                center = Offset(x, y)
            )
        }

        // Draw line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )
    }

    // X-axis labels
    data.forEachIndexed { index, point ->
        val x = padding + index * stepX
        drawContext.canvas.nativeCanvas.drawText(
            "${point.monthOffset}",
            x,
            size.height - 4f,
            android.graphics.Paint().apply {
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 24f
                color = android.graphics.Color.GRAY
            }
        )
    }
}

// ==================== Priority Zone Card ====================

@Composable
private fun PriorityZoneCard(zone: PriorityZone) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority score indicator
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(priorityColor(zone.priorityScore)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "%.1f".format(zone.priorityScore),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Zone ${zone.zoneId.take(8)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = "${zone.reportCount} reports · ${zone.confirmedCount} confirmed",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Avg severity: %.0f%% · Last: %dd ago".format(
                        zone.avgSeverity * 100,
                        zone.lastReportDaysAgo
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== Cluster Card ====================

@Composable
private fun ClusterCard(cluster: DamageCluster) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7B1FA2)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${cluster.reports.size}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cluster.dominantType.replaceFirstChar { it.uppercase() },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = "${cluster.reports.size} reports · Radius: %.0fm".format(cluster.radiusMeters),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Avg score: %.0f%% · 📍%.4f, %.4f".format(
                        cluster.avgFusedScore * 100,
                        cluster.center.latitude,
                        cluster.center.longitude
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== Helpers ====================

private fun priorityColor(score: Float): Color {
    return when {
        score >= 2.0f -> Color(0xFFE53935)
        score >= 1.0f -> Color(0xFFFF9800)
        score >= 0.5f -> Color(0xFFFFC107)
        else -> Color(0xFF4CAF50)
    }
}
