package com.example.roadguard.analytics

import com.example.roadguard.model.Report
import com.example.roadguard.model.ReportStatus
import com.example.roadguard.model.DetectionSource
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * Unit tests for [PredictiveAnalytics].
 *
 * Tests: priority scoring, spatial clustering, trend analysis,
 * summary stats, and math helpers.
 */
class PredictiveAnalyticsTest {

    private lateinit var analytics: PredictiveAnalytics

    @Before
    fun setup() {
        analytics = PredictiveAnalytics()
    }

    // ==================== Priority Scoring ====================

    @Test
    fun `priority score is higher for more severe zones`() {
        val reports = listOf(
            createReport(lat = 45.0, lng = 7.0, fusedScore = 0.9f),
            createReport(lat = 45.0, lng = 7.0, fusedScore = 0.8f),
            createReport(lat = 46.0, lng = 8.0, fusedScore = 0.2f),
            createReport(lat = 46.0, lng = 8.0, fusedScore = 0.1f)
        )

        val zones = analytics.calculateMaintenancePriority(reports)
        assertTrue("Should have 2 zones", zones.size == 2)
        assertTrue("Higher severity zone should rank first",
            zones[0].avgSeverity > zones[1].avgSeverity)
    }

    @Test
    fun `priority returns empty for no reports`() {
        val zones = analytics.calculateMaintenancePriority(emptyList())
        assertTrue(zones.isEmpty())
    }

    @Test
    fun `priority includes confirmed count`() {
        val reports = listOf(
            createReport(lat = 45.0, lng = 7.0, status = ReportStatus.CONFIRMED.name),
            createReport(lat = 45.0, lng = 7.0, status = ReportStatus.PENDING.name)
        )

        val zones = analytics.calculateMaintenancePriority(reports)
        assertEquals(1, zones[0].confirmedCount)
    }

    // ==================== Spatial Clustering ====================

    @Test
    fun `nearby reports form a cluster`() {
        // Two very close reports (~11m apart)
        val reports = listOf(
            createReport(lat = 45.0000, lng = 7.0000),
            createReport(lat = 45.0001, lng = 7.0001)
        )

        val clusters = analytics.identifyDamageClusters(reports, radiusMeters = 100.0)
        assertEquals("Should form 1 cluster", 1, clusters.size)
        assertEquals(2, clusters[0].reports.size)
    }

    @Test
    fun `distant reports do not cluster`() {
        // Two reports 1km+ apart
        val reports = listOf(
            createReport(lat = 45.0, lng = 7.0),
            createReport(lat = 45.01, lng = 7.01)
        )

        val clusters = analytics.identifyDamageClusters(reports, radiusMeters = 50.0)
        assertTrue("Should form 0 clusters (too far apart)", clusters.isEmpty())
    }

    @Test
    fun `clustering returns empty for no reports`() {
        val clusters = analytics.identifyDamageClusters(emptyList())
        assertTrue(clusters.isEmpty())
    }

    @Test
    fun `cluster dominant type is most common`() {
        val reports = listOf(
            createReport(lat = 45.0000, lng = 7.0000, damageType = "pothole"),
            createReport(lat = 45.0001, lng = 7.0001, damageType = "pothole"),
            createReport(lat = 45.0001, lng = 7.0000, damageType = "bump")
        )

        val clusters = analytics.identifyDamageClusters(reports, radiusMeters = 100.0)
        assertEquals("pothole", clusters[0].dominantType)
    }

    // ==================== Trend Analysis ====================

    @Test
    fun `trend analysis returns valid forecast`() {
        val cal = Calendar.getInstance()
        val reports = (0..5).map { monthsAgo ->
            val date = Calendar.getInstance().apply {
                time = cal.time
                add(Calendar.MONTH, -monthsAgo)
            }.time
            createReport(timestamp = date)
        }

        val forecast = analytics.predictDegradationTrend(reports)
        assertNotNull(forecast)
        assertEquals(6, forecast.monthlyTrend.size)
        assertNotNull(forecast.trend)
    }

    @Test
    fun `empty reports produce stable trend`() {
        val forecast = analytics.predictDegradationTrend(emptyList())
        assertEquals(TrendDirection.STABLE, forecast.trend)
        assertEquals(0f, forecast.predictedNextMonth)
    }

    // ==================== Summary Stats ====================

    @Test
    fun `summary counts statuses correctly`() {
        val reports = listOf(
            createReport(status = ReportStatus.PENDING.name),
            createReport(status = ReportStatus.PENDING.name),
            createReport(status = ReportStatus.CONFIRMED.name),
            createReport(status = ReportStatus.RESOLVED.name)
        )

        val summary = analytics.computeSummary(reports)
        assertEquals(4, summary.totalReports)
        assertEquals(2, summary.pendingReports)
        assertEquals(1, summary.confirmedReports)
        assertEquals(1, summary.resolvedReports)
    }

    @Test
    fun `summary handles empty list`() {
        val summary = analytics.computeSummary(emptyList())
        assertEquals(0, summary.totalReports)
        assertEquals(0f, summary.avgFusedScore)
    }

    @Test
    fun `summary calculates dual confirmed percentage`() {
        val reports = listOf(
            createReport(detectionSource = DetectionSource.DUAL_CONFIRMED.name),
            createReport(detectionSource = DetectionSource.CV_ONLY.name),
            createReport(detectionSource = DetectionSource.CV_ONLY.name),
            createReport(detectionSource = DetectionSource.CV_ONLY.name)
        )

        val summary = analytics.computeSummary(reports)
        assertEquals(25f, summary.dualConfirmedPercent)
    }

    // ==================== Math Helpers ====================

    @Test
    fun `linear regression on known line`() {
        // y = 2x + 1
        val x = listOf(1f, 2f, 3f, 4f, 5f)
        val y = listOf(3f, 5f, 7f, 9f, 11f)

        val (slope, intercept) = analytics.linearRegression(x, y)
        assertEquals(2f, slope, 0.01f)
        assertEquals(1f, intercept, 0.01f)
    }

    @Test
    fun `haversine distance is approximately correct`() {
        // Torino Porta Nuova to Torino Lingotto (~3.5km)
        val a = GeoPoint(45.0619, 7.6782)
        val b = GeoPoint(45.0324, 7.6504)

        val distance = analytics.haversineDistance(a, b)
        assertTrue("Distance should be ~3.5km", distance > 3000 && distance < 4000)
    }

    @Test
    fun `haversine same point is zero`() {
        val a = GeoPoint(45.0, 7.0)
        val distance = analytics.haversineDistance(a, a)
        assertEquals(0.0, distance, 0.01)
    }

    @Test
    fun `geohash groups nearby points`() {
        val a = GeoPoint(45.0001, 7.0001)
        val b = GeoPoint(45.0002, 7.0002)

        assertEquals(analytics.geohash(a, 3), analytics.geohash(b, 3))
    }

    // ==================== Helpers ====================

    private fun createReport(
        lat: Double = 45.0,
        lng: Double = 7.0,
        fusedScore: Float = 0.5f,
        status: String = ReportStatus.PENDING.name,
        damageType: String = "pothole",
        detectionSource: String = DetectionSource.CV_ONLY.name,
        timestamp: Date = Date()
    ): Report {
        return Report(
            id = "test_${System.nanoTime()}",
            location = GeoPoint(lat, lng),
            fusedScore = fusedScore,
            status = status,
            damageType = damageType,
            detectionSource = detectionSource,
            timestamp = timestamp,
            severity = fusedScore
        )
    }
}
