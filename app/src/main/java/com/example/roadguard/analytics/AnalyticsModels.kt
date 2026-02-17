package com.example.roadguard.analytics

import com.google.firebase.firestore.GeoPoint

/**
 * A geographic zone with computed maintenance priority.
 *
 * @param zoneId Unique zone identifier (geohash prefix)
 * @param center Center point of the zone
 * @param reportCount Total reports in this zone
 * @param avgSeverity Average fused score across reports
 * @param priorityScore Computed priority = avgSeverity × reportCount × decay
 * @param lastReportDaysAgo Days since the most recent report
 * @param confirmedCount Number of operator-confirmed reports
 */
data class PriorityZone(
    val zoneId: String,
    val center: GeoPoint,
    val reportCount: Int,
    val avgSeverity: Float,
    val priorityScore: Float,
    val lastReportDaysAgo: Int,
    val confirmedCount: Int
)

/**
 * A spatial cluster of nearby reports.
 *
 * @param id Cluster identifier
 * @param center Centroid position
 * @param reports Report IDs in this cluster
 * @param radiusMeters Effective cluster radius
 * @param avgFusedScore Average fused score of clustered reports
 * @param dominantType Most common damage type
 */
data class DamageCluster(
    val id: Int,
    val center: GeoPoint,
    val reports: List<String>,
    val radiusMeters: Double,
    val avgFusedScore: Float,
    val dominantType: String
)

/**
 * A degradation trend forecast for a zone.
 *
 * @param zoneId Zone identifier
 * @param monthlyTrend List of (month offset, report count) data points
 * @param slope Regression slope (reports/month change rate)
 * @param predictedNextMonth Predicted report count for next month
 * @param trend IMPROVING, STABLE, or DEGRADING
 */
data class DegradationForecast(
    val zoneId: String,
    val monthlyTrend: List<TrendPoint>,
    val slope: Float,
    val predictedNextMonth: Float,
    val trend: TrendDirection
)

data class TrendPoint(
    val monthOffset: Int,    // 0 = current month, -1 = last month, etc.
    val reportCount: Int,
    val avgSeverity: Float
)

enum class TrendDirection {
    IMPROVING,   // slope < -0.5: fewer reports over time
    STABLE,      // -0.5 <= slope <= 0.5
    DEGRADING    // slope > 0.5: more reports over time
}

/**
 * Summary stats for the analytics dashboard.
 */
data class AnalyticsSummary(
    val totalReports: Int,
    val pendingReports: Int,
    val confirmedReports: Int,
    val resolvedReports: Int,
    val avgFusedScore: Float,
    val dualConfirmedPercent: Float,
    val topDamageType: String
)
