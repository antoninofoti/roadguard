package com.example.roadguard.analytics

import com.example.roadguard.model.DetectionSource
import com.example.roadguard.model.Report
import com.example.roadguard.model.ReportStatus
import com.google.firebase.firestore.GeoPoint
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Predictive analytics engine for road damage reports.
 *
 * Computes:
 * - Maintenance priority scoring per geographic zone
 * - Spatial clustering of nearby reports
 * - Temporal degradation trend analysis with linear regression
 *
 * All computations are done locally on the device from cached reports.
 * No external ML frameworks required.
 */
class PredictiveAnalytics {

    companion object {
        /** Geohash precision for zone grouping (~1.2km × 0.6km cells) */
        private const val GEOHASH_PRECISION = 4

        /** Default cluster radius in meters */
        private const val DEFAULT_CLUSTER_RADIUS = 100.0

        /** Months of history for trend analysis */
        private const val TREND_MONTHS = 6

        /** Earth's radius in meters for distance calculations */
        private const val EARTH_RADIUS_M = 6_371_000.0
    }

    // ==================== Maintenance Priority ====================

    /**
     * Calculate maintenance priority for each zone.
     *
     * Priority = avgSeverity × log2(reportCount + 1) × decay(daysSinceLatest)
     *
     * - Higher severity → higher priority
     * - More reports → higher priority (logarithmic to avoid domination)
     * - More recent → higher priority (exponential decay, halflife = 30 days)
     */
    fun calculateMaintenancePriority(reports: List<Report>): List<PriorityZone> {
        if (reports.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()

        // Group reports by geohash zone
        val zones = reports
            .filter { it.location != null }
            .groupBy { geohash(it.location!!, GEOHASH_PRECISION) }

        return zones.map { (zoneId, zoneReports) ->
            val avgSeverity = zoneReports.map { it.fusedScore }.average().toFloat()
            val reportCount = zoneReports.size
            val confirmedCount = zoneReports.count { it.status == ReportStatus.CONFIRMED.name }

            // Days since most recent report
            val latestTimestamp = zoneReports.mapNotNull { it.timestamp?.time }.maxOrNull() ?: now
            val daysSince = TimeUnit.MILLISECONDS.toDays(now - latestTimestamp).toInt()

            // Decay factor: halflife of 30 days
            val decay = Math.pow(0.5, daysSince / 30.0).toFloat()

            // Priority formula
            val priority = avgSeverity *
                    (Math.log(reportCount.toDouble() + 1.0) / Math.log(2.0)).toFloat() *
                    decay

            // Zone center = centroid of all reports
            val center = centroid(zoneReports.mapNotNull { it.location })

            PriorityZone(
                zoneId = zoneId,
                center = center,
                reportCount = reportCount,
                avgSeverity = avgSeverity,
                priorityScore = priority,
                lastReportDaysAgo = daysSince,
                confirmedCount = confirmedCount
            )
        }.sortedByDescending { it.priorityScore }
    }

    // ==================== Spatial Clustering ====================

    /**
     * Count reports within a search radius of a target report.
     * Used to detect high-density regions for adaptive clustering.
     *
     * @param target The reference report
     * @param candidates List of candidate reports (typically all reports)
     * @param searchRadiusMeters Search diameter in meters
     * @return Number of candidates within searchRadiusMeters (excluding target)
     */
    private fun localDensity(
        target: Report,
        candidates: List<Report>,
        searchRadiusMeters: Double = 100.0
    ): Int {
        if (target.location == null) return 0
        return candidates.count { candidate ->
            candidate.id != target.id &&
                    candidate.location != null &&
                    haversineDistance(target.location!!, candidate.location!!) <= searchRadiusMeters
        }
    }

    /**
     * Compute adaptive clustering radius based on spatial density of reports.
     *
     * High density (many reports close) → reduce radius to 70% of base (finer clusters)
     * Low density → use base radius (faster convergence)
     * Very sparse → increase to max (avoid singletons)
     *
     * This prevents loss of correlated reports in dense urban areas and premature
     * clustering in rural/sparse areas.
     *
     * @param reports List of reports to analyze
     * @param baseRadiusMeters Starting radius in meters
     * @return Adaptive radius in meters, clamped to [30m, 500m]
     */
    private fun computeAdaptiveRadius(
        reports: List<Report>,
        baseRadiusMeters: Double
    ): Double {
        if (reports.size < 3) return baseRadiusMeters

        val minRadius = 30.0
        val maxRadius = 500.0
        val densityThreshold = 2

        // Sample density: count neighbors within 100m for first 5 reports
        val sampleSize = minOf(5, reports.size)
        val avgNeighbors = (0 until sampleSize)
            .map { i -> localDensity(reports[i], reports, 100.0) }
            .average()

        // If average > 2 neighbors within 100m → dense region → reduce radius
        return when {
            avgNeighbors >= densityThreshold ->
                maxOf(minRadius, baseRadiusMeters * 0.7)
            avgNeighbors < 0.5 ->
                minOf(maxRadius, baseRadiusMeters * 1.3)
            else ->
                baseRadiusMeters
        }
    }

    /**
     * Identify clusters of nearby reports using density-adaptive distance-based grouping.
     *
     * Uses a greedy nearest-neighbor approach with adaptive radius:
     * 1. Compute effective radius based on report spatial density
     * 2. Start with the first unassigned report as a cluster seed
     * 3. Add all reports within effective radius to the cluster
     * 4. Repeat until all reports are assigned
     *
     * Prevents under-clustering in dense areas (e.g., urban pothole hotspots)
     * and over-clustering in sparse areas.
     */
    fun identifyDamageClusters(
        reports: List<Report>,
        radiusMeters: Double = DEFAULT_CLUSTER_RADIUS
    ): List<DamageCluster> {
        val geoReports = reports.filter { it.location != null }
        if (geoReports.isEmpty()) return emptyList()

        // Compute adaptive radius based on spatial density
        val effectiveRadius = computeAdaptiveRadius(geoReports, radiusMeters)

        val unassigned = geoReports.toMutableList()
        val clusters = mutableListOf<DamageCluster>()
        var clusterId = 0

        while (unassigned.isNotEmpty()) {
            val seed = unassigned.removeAt(0)
            val clusterReports = mutableListOf(seed)

            // Find all reports within adaptive radius of seed
            val iterator = unassigned.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                val distance = haversineDistance(seed.location!!, candidate.location!!)
                if (distance <= effectiveRadius) {
                    clusterReports.add(candidate)
                    iterator.remove()
                }
            }

            // Only keep clusters with 2+ reports (single reports are not clusters)
            if (clusterReports.size >= 2) {
                val center = centroid(clusterReports.mapNotNull { it.location })
                val avgScore = clusterReports.map { it.fusedScore }.average().toFloat()
                val dominantType = clusterReports
                    .map { it.damageType.ifEmpty { "unknown" } }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }?.key ?: "unknown"

                // Effective radius = max distance from centroid
                val effectiveClusterRadius = clusterReports
                    .mapNotNull { it.location }
                    .maxOfOrNull { haversineDistance(center, it) } ?: radiusMeters

                clusters.add(
                    DamageCluster(
                        id = clusterId++,
                        center = center,
                        reports = clusterReports.map { it.id },
                        radiusMeters = effectiveClusterRadius,
                        avgFusedScore = avgScore,
                        dominantType = dominantType
                    )
                )
            }
        }

        return clusters.sortedByDescending { it.reports.size }
    }

    // ==================== Temporal Trend Analysis ====================

    /**
     * Analyze the temporal trend of reports using simple linear regression.
     *
     * Groups reports by month, fits a line, and classifies the trend as
     * IMPROVING (slope < -0.5), STABLE, or DEGRADING (slope > 0.5).
     */
    fun predictDegradationTrend(reports: List<Report>): DegradationForecast {
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.YEAR) * 12 + now.get(Calendar.MONTH)

        // Build monthly data for the last TREND_MONTHS
        val monthlyData = (-TREND_MONTHS + 1..0).map { offset ->
            val targetMonth = currentMonth + offset
            val monthReports = reports.filter { report ->
                report.timestamp?.let { date ->
                    val cal = Calendar.getInstance().apply { time = date }
                    val reportMonth = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
                    reportMonth == targetMonth
                } ?: false
            }

            TrendPoint(
                monthOffset = offset,
                reportCount = monthReports.size,
                avgSeverity = if (monthReports.isNotEmpty())
                    monthReports.map { it.fusedScore }.average().toFloat()
                else 0f
            )
        }

        // Linear regression on report counts
        val (slope, intercept) = linearRegression(
            monthlyData.map { it.monthOffset.toFloat() },
            monthlyData.map { it.reportCount.toFloat() }
        )

        // Predict next month
        val predictedNext = (slope * 1f + intercept).coerceAtLeast(0f)

        // Classify trend
        val trend = when {
            slope < -0.5f -> TrendDirection.IMPROVING
            slope > 0.5f -> TrendDirection.DEGRADING
            else -> TrendDirection.STABLE
        }

        return DegradationForecast(
            zoneId = "global",
            monthlyTrend = monthlyData,
            slope = slope,
            predictedNextMonth = predictedNext,
            trend = trend
        )
    }

    // ==================== Summary Stats ====================

    /**
     * Compute aggregate statistics for the analytics dashboard.
     */
    fun computeSummary(reports: List<Report>): AnalyticsSummary {
        if (reports.isEmpty()) {
            return AnalyticsSummary(0, 0, 0, 0, 0f, 0f, "N/A")
        }

        val pending = reports.count { it.status == ReportStatus.PENDING.name }
        val confirmed = reports.count { it.status == ReportStatus.CONFIRMED.name }
        val resolved = reports.count { it.status == ReportStatus.RESOLVED.name }
        val avgFused = reports.map { it.fusedScore }.average().toFloat()
        val dualPercent = if (reports.isNotEmpty())
            reports.count { it.detectionSource == DetectionSource.DUAL_CONFIRMED.name } * 100f / reports.size
        else 0f
        val topType = reports
            .map { it.damageType.ifEmpty { "unknown" } }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key ?: "N/A"

        return AnalyticsSummary(
            totalReports = reports.size,
            pendingReports = pending,
            confirmedReports = confirmed,
            resolvedReports = resolved,
            avgFusedScore = avgFused,
            dualConfirmedPercent = dualPercent,
            topDamageType = topType
        )
    }

    // ==================== Math Helpers ====================

    /**
     * Simple linear regression: y = slope * x + intercept.
     * Returns Pair(slope, intercept).
     */
    internal fun linearRegression(x: List<Float>, y: List<Float>): Pair<Float, Float> {
        val n = x.size
        if (n < 2) return Pair(0f, y.firstOrNull() ?: 0f)

        val xMean = x.average().toFloat()
        val yMean = y.average().toFloat()

        var numerator = 0f
        var denominator = 0f
        for (i in x.indices) {
            val dx = x[i] - xMean
            numerator += dx * (y[i] - yMean)
            denominator += dx * dx
        }

        val slope = if (denominator != 0f) numerator / denominator else 0f
        val intercept = yMean - slope * xMean

        return Pair(slope, intercept)
    }

    /**
     * Simplified geohash for grouping locations into zones.
     * Uses grid-based quantization (not full geohash encoding).
     */
    internal fun geohash(point: GeoPoint, precision: Int): String {
        // Each precision level divides by 10
        val factor = Math.pow(10.0, precision.toDouble())
        val latKey = (point.latitude * factor).toLong()
        val lonKey = (point.longitude * factor).toLong()
        return "${latKey}_${lonKey}"
    }

    /**
     * Haversine distance between two GeoPoints in meters.
     */
    internal fun haversineDistance(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)

        val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        return 2 * EARTH_RADIUS_M * Math.asin(sqrt(h))
    }

    /**
     * Compute the centroid of a list of GeoPoints.
     */
    private fun centroid(points: List<GeoPoint>): GeoPoint {
        if (points.isEmpty()) return GeoPoint(0.0, 0.0)
        val avgLat = points.map { it.latitude }.average()
        val avgLon = points.map { it.longitude }.average()
        return GeoPoint(avgLat, avgLon)
    }
}
