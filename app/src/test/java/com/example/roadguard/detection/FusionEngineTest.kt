package com.example.roadguard.detection

import com.example.roadguard.sensor.AnomalyEvent
import com.example.roadguard.sensor.AnomalyType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [FusionEngine].
 *
 * Tests the 8 core scenarios validated in the Python prototype:
 * - CV only, sensor only, both, neither
 * - With and without temporal correlation
 * - Threshold behavior (AUTO, PROMPT, DISCARD)
 */
class FusionEngineTest {

    private lateinit var engine: FusionEngine

    @Before
    fun setup() {
        engine = FusionEngine(
            cvWeight = 0.55f,
            sensorWeight = 0.30f,
            temporalWeight = 0.15f,
            autoThreshold = 0.75f,
            promptThreshold = 0.50f,
            temporalWindowMs = 2000L
        )
    }

    // ========== Core fusion scenarios ==========

    @Test
    fun `both high with temporal correlation results in AUTO_REPORT`() {
        // Sensor anomaly first
        val sensorEvent = createAnomalyEvent(confidence = 0.9f, type = AnomalyType.POTHOLE)
        engine.onSensorAnomaly(sensorEvent)

        // CV detection within temporal window
        val result = engine.onCvDetection(confidence = 0.95f, label = "pothole")

        assertEquals(FusionAction.AUTO_REPORT, result.action)
        assertTrue("Fused score should be > 0.75", result.fusedScore > 0.75f)
        assertEquals("DUAL_CONFIRMED", result.detectionSource)
    }

    @Test
    fun `both medium with temporal correlation results in PROMPT_USER`() {
        val sensorEvent = createAnomalyEvent(confidence = 0.5f, type = AnomalyType.BUMP)
        engine.onSensorAnomaly(sensorEvent)

        val result = engine.onCvDetection(confidence = 0.6f, label = "pothole")

        assertEquals(FusionAction.PROMPT_USER, result.action)
        assertTrue("Fused score should be >= 0.50", result.fusedScore >= 0.50f)
        assertEquals("DUAL_CONFIRMED", result.detectionSource)
    }

    @Test
    fun `CV only no sensor results in PROMPT_USER`() {
        // No sensor events registered; need cv * 0.55 >= 0.50 → cv >= 0.91
        val result = engine.onCvDetection(confidence = 0.95f, label = "pothole")

        assertEquals(FusionAction.PROMPT_USER, result.action)
        assertEquals("CV_ONLY", result.detectionSource)
        assertEquals(0f, result.sensorConfidence)
    }

    @Test
    fun `sensor only no CV results in DISCARD`() {
        // No CV events registered
        val sensorEvent = createAnomalyEvent(confidence = 0.9f, type = AnomalyType.POTHOLE)
        val result = engine.onSensorAnomaly(sensorEvent)

        assertEquals(FusionAction.DISCARD, result.action)
        assertEquals("SENSOR_ONLY", result.detectionSource)
        assertEquals(0f, result.cvConfidence)
    }

    @Test
    fun `both low no correlation results in DISCARD`() {
        val sensorEvent = createAnomalyEvent(confidence = 0.2f, type = AnomalyType.ROUGHNESS)
        engine.onSensorAnomaly(sensorEvent)

        val result = engine.onCvDetection(confidence = 0.3f, label = "pothole")

        assertEquals(FusionAction.DISCARD, result.action)
        assertTrue("Fused score should be < 0.50", result.fusedScore < 0.50f)
    }

    // ========== Scoring tests ==========

    @Test
    fun `fusion score equals weighted sum`() {
        val sensorEvent = createAnomalyEvent(confidence = 0.8f, type = AnomalyType.POTHOLE)
        engine.onSensorAnomaly(sensorEvent)

        val result = engine.onCvDetection(confidence = 0.7f, label = "pothole")

        // Expected: 0.55*0.7 + 0.30*0.8 + 0.15*1.0 = 0.385 + 0.24 + 0.15 = 0.775
        val expected = 0.55f * 0.7f + 0.30f * 0.8f + 0.15f * 1.0f
        assertEquals(expected, result.fusedScore, 0.05f)
    }

    @Test
    fun `temporal bonus is zero without matching signal`() {
        // CV only — no sensor events
        val result = engine.onCvDetection(confidence = 0.8f, label = "pothole")

        // Expected: 0.55*0.8 + 0.30*0.0 + 0.15*0.0 = 0.44
        assertEquals(0f, result.temporalBonus)
        val expected = 0.55f * 0.8f
        assertEquals(expected, result.fusedScore, 0.05f)
    }

    // ========== Type and source classification ==========

    @Test
    fun `damage type comes from CV when available`() {
        val sensorEvent = createAnomalyEvent(confidence = 0.8f, type = AnomalyType.BUMP)
        engine.onSensorAnomaly(sensorEvent)

        val result = engine.onCvDetection(confidence = 0.7f, label = "pothole")

        assertEquals("pothole", result.damageType)
    }

    @Test
    fun `damage type comes from sensor when CV not available`() {
        val sensorEvent = createAnomalyEvent(confidence = 0.8f, type = AnomalyType.SPEED_BUMP)
        val result = engine.onSensorAnomaly(sensorEvent)

        assertEquals("speed_bump", result.damageType)
    }

    // ========== Reset ==========

    @Test
    fun `reset clears all buffers`() {
        val sensorEvent = createAnomalyEvent(confidence = 0.9f, type = AnomalyType.POTHOLE)
        engine.onSensorAnomaly(sensorEvent)

        engine.reset()

        // After reset, CV detection should not find any sensor matches
        val result = engine.onCvDetection(confidence = 0.9f, label = "pothole")
        assertEquals("CV_ONLY", result.detectionSource)
        assertEquals(0f, result.sensorConfidence)
    }

    // ========== Helper ==========

    private fun createAnomalyEvent(
        confidence: Float,
        type: AnomalyType,
        severity: Float = 0.5f
    ): AnomalyEvent {
        return AnomalyEvent(
            type = type,
            severity = severity,
            confidence = confidence,
            timestamp = System.nanoTime(),
            accelPeak = 15.0f,
            gyroPeak = 1.5f,
            location = null
        )
    }
}
