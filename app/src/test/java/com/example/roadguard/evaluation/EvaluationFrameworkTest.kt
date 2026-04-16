package com.example.roadguard.evaluation

import com.example.roadguard.calibration.GroundTruthEntry
import com.example.roadguard.detection.FusionContext
import com.example.roadguard.detection.FusionEngine
import com.example.roadguard.detection.FusionMode
import com.example.roadguard.sensor.AnomalyEvent
import com.example.roadguard.sensor.AnomalyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the Phase E evaluation framework.
 *
 * Tests cover:
 * - DetectionMode weight assignments
 * - FusionEngine DetectionMode integration (single-modality gating)
 * - EvaluationEvent CSV serialization round-trip
 * - MetricsCalculator precision/recall computation
 * - End-to-end: session → events → metrics
 */
class EvaluationFrameworkTest {

    private lateinit var engine: FusionEngine
    private val calculator = MetricsCalculator()

    @Before
    fun setup() {
        engine = FusionEngine()
    }

    // ── DetectionMode enum ──────────────────────────────────────

    @Test
    fun `CV_ONLY mode has alpha 1 beta 0 gamma 0`() {
        assertEquals(1.0f, DetectionMode.CV_ONLY.alpha, 0.001f)
        assertEquals(0.0f, DetectionMode.CV_ONLY.beta, 0.001f)
        assertEquals(0.0f, DetectionMode.CV_ONLY.gamma, 0.001f)
    }

    @Test
    fun `SENSOR_ONLY mode has alpha 0 beta 1 gamma 0`() {
        assertEquals(0.0f, DetectionMode.SENSOR_ONLY.alpha, 0.001f)
        assertEquals(1.0f, DetectionMode.SENSOR_ONLY.beta, 0.001f)
        assertEquals(0.0f, DetectionMode.SENSOR_ONLY.gamma, 0.001f)
    }

    @Test
    fun `FIXED_FUSION mode has correct weights`() {
        assertEquals(0.55f, DetectionMode.FIXED_FUSION.alpha, 0.001f)
        assertEquals(0.30f, DetectionMode.FIXED_FUSION.beta, 0.001f)
        assertEquals(0.15f, DetectionMode.FIXED_FUSION.gamma, 0.001f)
    }

    @Test
    fun `evaluation order has all 4 modes`() {
        val order = DetectionMode.evaluationOrder()
        assertEquals(4, order.size)
        assertTrue(order.contains(DetectionMode.CV_ONLY))
        assertTrue(order.contains(DetectionMode.SENSOR_ONLY))
        assertTrue(order.contains(DetectionMode.FIXED_FUSION))
        assertTrue(order.contains(DetectionMode.ADAPTIVE_FUSION))
    }

    @Test
    fun `fromName is case-insensitive`() {
        assertEquals(DetectionMode.CV_ONLY, DetectionMode.fromName("cv_only"))
        assertEquals(DetectionMode.ADAPTIVE_FUSION, DetectionMode.fromName("ADAPTIVE_FUSION"))
        assertEquals(null, DetectionMode.fromName("nonexistent"))
    }

    // ── FusionEngine DetectionMode Integration ──────────────────

    @Test
    fun `CV_ONLY mode makes sensor signal irrelevant`() {
        engine.detectionMode = DetectionMode.CV_ONLY
        val (alpha, beta, gamma) = engine.computeEffectiveWeights()

        assertEquals("Alpha should be 1.0 for CV_ONLY", 1.0f, alpha, 0.001f)
        assertEquals("Beta should be 0.0 for CV_ONLY", 0.0f, beta, 0.001f)
        assertEquals("Gamma should be 0.0 for CV_ONLY", 0.0f, gamma, 0.001f)
    }

    @Test
    fun `SENSOR_ONLY mode makes CV signal irrelevant`() {
        engine.detectionMode = DetectionMode.SENSOR_ONLY
        val (alpha, beta, gamma) = engine.computeEffectiveWeights()

        assertEquals("Alpha should be 0.0 for SENSOR_ONLY", 0.0f, alpha, 0.001f)
        assertEquals("Beta should be 1.0 for SENSOR_ONLY", 1.0f, beta, 0.001f)
        assertEquals("Gamma should be 0.0 for SENSOR_ONLY", 0.0f, gamma, 0.001f)
    }

    @Test
    fun `FIXED_FUSION uses base weights`() {
        engine.detectionMode = DetectionMode.FIXED_FUSION
        val (alpha, beta, gamma) = engine.computeEffectiveWeights()

        assertEquals(0.55f, alpha, 0.001f)
        assertEquals(0.30f, beta, 0.001f)
        assertEquals(0.15f, gamma, 0.001f)
    }

    @Test
    fun `ADAPTIVE_FUSION applies context modulation`() {
        engine.detectionMode = DetectionMode.ADAPTIVE_FUSION
        engine.setFusionContext(FusionContext(
            speedKmh = 100f, ambientLightLux = 20f, isNightTime = true
        ))
        val (alpha, beta, gamma) = engine.computeEffectiveWeights()

        // Weights sum to 1.0
        assertEquals(1.0f, alpha + beta + gamma, 0.002f)
        // Night + high speed: beta should dominate
        assertTrue("Beta should be > alpha at night+high speed", beta > alpha)
    }

    @Test
    fun `null detectionMode falls through to fusionMode`() {
        engine.detectionMode = null
        engine.fusionMode = FusionMode.FIXED
        val (alpha, beta, gamma) = engine.computeEffectiveWeights()

        assertEquals(0.55f, alpha, 0.001f)
        assertEquals(0.30f, beta, 0.001f)
        assertEquals(0.15f, gamma, 0.001f)
    }

    @Test
    fun `CV_ONLY detection scores only from CV confidence`() {
        engine.detectionMode = DetectionMode.CV_ONLY
        val result = engine.onCvDetection(0.8f, "pothole")

        // In CV_ONLY, score = 1.0 * 0.8 + 0 + 0 = 0.8
        assertEquals(0.8f, result.fusedScore, 0.01f)
    }

    @Test
    fun `SENSOR_ONLY detection scores only from sensor confidence`() {
        engine.detectionMode = DetectionMode.SENSOR_ONLY
        val sensorEvent = AnomalyEvent(
            type = AnomalyType.POTHOLE, severity = 0.7f, confidence = 0.75f,
            timestamp = System.nanoTime(), accelPeak = 12f, gyroPeak = 2.5f
        )
        val result = engine.onSensorAnomaly(sensorEvent)

        // In SENSOR_ONLY, score = 0 + 1.0 * 0.75 + 0 = 0.75
        assertEquals(0.75f, result.fusedScore, 0.01f)
    }

    @Test
    fun `CV_ONLY high sensor confidence does not affect score`() {
        engine.detectionMode = DetectionMode.CV_ONLY
        val cv_result_low  = engine.onCvDetection(0.5f, "pothole")

        // Add a high-confidence sensor event to the buffer
        val strongSensor = AnomalyEvent(
            type = AnomalyType.POTHOLE, severity = 1.0f, confidence = 1.0f,
            timestamp = System.nanoTime(), accelPeak = 30f, gyroPeak = 5f
        )
        engine.onSensorAnomaly(strongSensor)

        // Now a second CV detection at same confidence should still give ~0.5
        engine.reset()
        engine.detectionMode = DetectionMode.CV_ONLY
        val cv_result = engine.onCvDetection(0.5f, "pothole")
        assertEquals("Sensor should not affect CV_ONLY score",
            0.5f, cv_result.fusedScore, 0.01f)
    }

    // ── EvaluationEvent CSV Round-Trip ──────────────────────────

    @Test
    fun `EvaluationEvent serializes and deserializes correctly`() {
        val original = EvaluationEvent(
            sessionId = "abc12345",
            mode = DetectionMode.FIXED_FUSION,
            timestampMs = 1_700_000_000_000L,
            lat = 41.8902,
            lng = 12.4922,
            speedKmh = 45.5f,
            cvConfidence = 0.82f,
            sensorConfidence = 0.65f,
            fusedScore = 0.76f,
            effectiveAlpha = 0.55f,
            effectiveBeta = 0.30f,
            effectiveGamma = 0.15f,
            damageType = "pothole",
            action = "AUTO_REPORT",
            ambientLightLux = 600f,
            isNight = false,
            isGroundTruth = false
        )

        val csvLine = original.toCsvLine()
        val parsed = EvaluationEvent.fromCsvLine(csvLine)

        assertNotNull("Parsed event should not be null", parsed)
        assertEquals(original.sessionId, parsed!!.sessionId)
        assertEquals(original.mode, parsed.mode)
        assertEquals(original.timestampMs, parsed.timestampMs)
        assertEquals(original.lat!!, parsed.lat!!, 0.0001)
        assertEquals(original.lng!!, parsed.lng!!, 0.0001)
        assertEquals(original.fusedScore, parsed.fusedScore, 0.001f)
        assertEquals(original.action, parsed.action)
    }

    @Test
    fun `EvaluationEvent with null GPS serializes correctly`() {
        val event = EvaluationEvent(
            sessionId = "test01",
            mode = DetectionMode.CV_ONLY,
            timestampMs = 1000L,
            lat = null, lng = null,
            speedKmh = 0f,
            cvConfidence = 0.7f, sensorConfidence = 0f, fusedScore = 0.7f,
            effectiveAlpha = 1.0f, effectiveBeta = 0f, effectiveGamma = 0f,
            damageType = "pothole", action = "PROMPT_USER",
            ambientLightLux = -1f, isNight = false
        )

        val line = event.toCsvLine()
        val parsed = EvaluationEvent.fromCsvLine(line)

        assertNotNull(parsed)
        assertEquals(null, parsed!!.lat)
        assertEquals(null, parsed.lng)
    }

    @Test
    fun `fromCsvLine returns null for malformed line`() {
        assertEquals(null, EvaluationEvent.fromCsvLine("too,few,columns"))
        assertEquals(null, EvaluationEvent.fromCsvLine(""))
    }

    // ── MetricsCalculator ───────────────────────────────────────

    @Test
    fun `perfect precision and recall with all detections matching GT`() {
        val gt = listOf(
            GroundTruthEntry(lat = 41.89, lng = 12.49, type = "pothole", radiusMeters = 15.0)
        )
        val events = listOf(
            makeEvent(lat = 41.89001, lng = 12.49001, action = "AUTO_REPORT")
        )
        val metrics = calculator.computeMetrics(events, gt, "Day+Dry", DetectionMode.FIXED_FUSION)

        assertEquals(1, metrics.truePositives)
        assertEquals(0, metrics.falsePositives)
        assertEquals(0, metrics.falseNegatives)
        assertEquals(1.0f, metrics.precision, 0.001f)
        assertEquals(1.0f, metrics.recall, 0.001f)
        assertEquals(1.0f, metrics.f1, 0.001f)
    }

    @Test
    fun `detection far from GT is false positive`() {
        val gt = listOf(
            GroundTruthEntry(lat = 41.89, lng = 12.49, type = "pothole", radiusMeters = 15.0)
        )
        val events = listOf(
            // 200m away — outside radius
            makeEvent(lat = 41.891, lng = 12.495, action = "AUTO_REPORT")
        )
        val metrics = calculator.computeMetrics(events, gt, "Day+Dry", DetectionMode.FIXED_FUSION)

        assertEquals(0, metrics.truePositives)
        assertEquals(1, metrics.falsePositives)
        assertEquals(1, metrics.falseNegatives)
        assertEquals(0.0f, metrics.precision, 0.001f)
        assertEquals(0.0f, metrics.recall, 0.001f)
    }

    @Test
    fun `no detections gives zero precision and recall`() {
        val gt = listOf(
            GroundTruthEntry(lat = 41.89, lng = 12.49, type = "pothole")
        )
        val metrics = calculator.computeMetrics(
            emptyList(), gt, "Night+Dry", DetectionMode.CV_ONLY
        )

        assertEquals(0, metrics.truePositives)
        assertEquals(0, metrics.falsePositives)
        assertEquals(1, metrics.falseNegatives)
        assertEquals(0.0f, metrics.precision, 0.001f)
        assertEquals(0.0f, metrics.recall, 0.001f)
        assertEquals(0.0f, metrics.f1, 0.001f)
    }

    @Test
    fun `DISCARD events are excluded from metrics`() {
        val gt = listOf(
            GroundTruthEntry(lat = 41.89, lng = 12.49, type = "pothole")
        )
        val events = listOf(
            // This detection is DISCARD — should not be counted
            makeEvent(lat = 41.89, lng = 12.49, action = "DISCARD")
        )
        val metrics = calculator.computeMetrics(events, gt, "Day+Dry", DetectionMode.FIXED_FUSION)

        // DISCARD filtered out → FN = 1, TP = 0
        assertEquals(0, metrics.truePositives)
        assertEquals(1, metrics.falseNegatives)
    }

    @Test
    fun `computeAllModes returns one result per mode`() {
        val gt = listOf(GroundTruthEntry(lat = 41.89, lng = 12.49, type = "pothole"))
        val eventsByMode = mapOf(
            DetectionMode.CV_ONLY to listOf(makeEvent(lat = 41.89, lng = 12.49)),
            DetectionMode.SENSOR_ONLY to emptyList(),
            DetectionMode.FIXED_FUSION to emptyList(),
            DetectionMode.ADAPTIVE_FUSION to emptyList()
        )
        val results = calculator.computeAllModes(eventsByMode, gt, "Day+Dry")

        assertEquals(4, results.size)
        assertEquals(DetectionMode.evaluationOrder(), results.map { it.mode })
    }

    @Test
    fun `markdown table contains all modes`() {
        val metrics = DetectionMode.evaluationOrder().map { mode ->
            EvaluationMetrics(
                mode = mode, truePositives = 5, falsePositives = 2, falseNegatives = 1,
                precision = 0.71f, recall = 0.83f, f1 = 0.77f,
                avgFusedScore = 0.65f, avgLatencyMs = 150f, totalEvents = 7, condition = "Test"
            )
        }
        val table = calculator.toMarkdownTable(metrics)

        assertTrue(table.contains("CV Only"))
        assertTrue(table.contains("Sensor Only"))
        assertTrue(table.contains("Fixed Fusion"))
        assertTrue(table.contains("Adaptive Fusion"))
        assertTrue(table.contains("|"))
    }

    @Test
    fun `haversine distance within same point is zero`() {
        val dist = calculator.haversineDistance(41.89, 12.49, 41.89, 12.49)
        assertEquals(0.0, dist, 0.1)
    }

    @Test
    fun `haversine distance 15 meters is within tolerance`() {
        // ~0.000135 degrees latitude ≈ 15m
        val dist = calculator.haversineDistance(41.89, 12.49, 41.89014, 12.49)
        assertTrue("15m distance, got $dist", dist in 5.0..25.0)
    }

    // ── CSV parsing from logger output ──────────────────────────

    @Test
    fun `parseEventsFromCsv skips header and parses data`() {
        val csv = buildString {
            appendLine(EvaluationEvent.CSV_HEADER)
            appendLine(makeEvent(lat = 41.89, lng = 12.49).toCsvLine())
            appendLine(makeEvent(lat = 41.90, lng = 12.50).toCsvLine())
        }
        val events = calculator.parseEventsFromCsv(csv)
        assertEquals(2, events.size)
    }

    @Test
    fun `EvaluationMetrics CSV header matches field order`() {
        val metrics = EvaluationMetrics(
            mode = DetectionMode.FIXED_FUSION, truePositives = 3, falsePositives = 1,
            falseNegatives = 2, precision = 0.75f, recall = 0.6f, f1 = 0.67f,
            avgFusedScore = 0.7f, avgLatencyMs = 200f, totalEvents = 4, condition = "Day+Dry"
        )
        val line = metrics.toCsvLine()
        val parts = line.split(",")
        // Header has 11 columns
        val headerParts = EvaluationMetrics.CSV_HEADER.split(",")
        assertEquals(headerParts.size, parts.size)
    }

    // ── Helpers ─────────────────────────────────────────────────

    private fun makeEvent(
        lat: Double? = 41.89,
        lng: Double? = 12.49,
        action: String = "AUTO_REPORT",
        mode: DetectionMode = DetectionMode.FIXED_FUSION
    ) = EvaluationEvent(
        sessionId = "test",
        mode = mode,
        timestampMs = System.currentTimeMillis(),
        lat = lat, lng = lng,
        speedKmh = 30f,
        cvConfidence = 0.8f, sensorConfidence = 0.7f, fusedScore = 0.75f,
        effectiveAlpha = 0.55f, effectiveBeta = 0.30f, effectiveGamma = 0.15f,
        damageType = "pothole",
        action = action,
        ambientLightLux = 500f, isNight = false
    )
}
