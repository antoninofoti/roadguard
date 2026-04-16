package com.example.roadguard.detection

import com.example.roadguard.sensor.AnomalyEvent
import com.example.roadguard.sensor.AnomalyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the adaptive fusion engine (Phase D — original thesis contribution).
 *
 * Verifies:
 * - Weight normalization (α + β + γ = 1.0 always)
 * - FIXED mode backward compatibility
 * - ADAPTIVE mode context-aware weight modulation
 * - Graceful degradation when context is unknown
 * - FusionResult includes effective weights and mode
 */
class AdaptiveFusionEngineTest {

    private lateinit var engine: FusionEngine

    @Before
    fun setup() {
        engine = FusionEngine()
    }

    // ── FIXED Mode Backward Compatibility ──

    @Test
    fun `FIXED mode uses base weights`() {
        engine.fusionMode = FusionMode.FIXED
        val (alpha, beta, gamma) = engine.computeEffectiveWeights()
        assertEquals(0.55f, alpha, 0.001f)
        assertEquals(0.30f, beta, 0.001f)
        assertEquals(0.15f, gamma, 0.001f)
    }

    @Test
    fun `FIXED mode weights sum to 1`() {
        engine.fusionMode = FusionMode.FIXED
        val (alpha, beta, gamma) = engine.computeEffectiveWeights()
        assertEquals(1.0f, alpha + beta + gamma, 0.001f)
    }

    @Test
    fun `FIXED mode result reports mode correctly`() {
        engine.fusionMode = FusionMode.FIXED
        val result = engine.onCvDetection(0.8f, "pothole")
        assertEquals("FIXED", result.fusionMode)
    }

    // ── ADAPTIVE Mode: Weight Normalization ──

    @Test
    fun `ADAPTIVE mode weights always sum to 1 with daytime context`() {
        engine.fusionMode = FusionMode.ADAPTIVE
        engine.setFusionContext(FusionContext(
            speedKmh = 50f,
            ambientLightLux = 800f,
            isNightTime = false
        ))
        val (alpha, beta, gamma) = engine.computeEffectiveWeights()
        assertEquals(1.0f, alpha + beta + gamma, 0.001f)
    }

    @Test
    fun `ADAPTIVE mode weights always sum to 1 with nighttime context`() {
        engine.fusionMode = FusionMode.ADAPTIVE
        engine.setFusionContext(FusionContext(
            speedKmh = 30f,
            ambientLightLux = 10f,
            isNightTime = true
        ))
        val (alpha, beta, gamma) = engine.computeEffectiveWeights()
        assertEquals(1.0f, alpha + beta + gamma, 0.001f)
    }

    @Test
    fun `ADAPTIVE mode weights always sum to 1 with high speed context`() {
        engine.fusionMode = FusionMode.ADAPTIVE
        engine.setFusionContext(FusionContext(
            speedKmh = 130f,
            ambientLightLux = 600f,
            isNightTime = false
        ))
        val (alpha, beta, gamma) = engine.computeEffectiveWeights()
        assertEquals(1.0f, alpha + beta + gamma, 0.001f)
    }

    @Test
    fun `ADAPTIVE mode weights sum to 1 across 100 random contexts`() {
        engine.fusionMode = FusionMode.ADAPTIVE
        val speeds = (0..200 step 2).map { it.toFloat() }

        for (speed in speeds) {
            for (lux in listOf(0f, 50f, 100f, 300f, 500f, 1000f)) {
                engine.setFusionContext(FusionContext(
                    speedKmh = speed,
                    ambientLightLux = lux,
                    isNightTime = lux < 100f
                ))
                val (alpha, beta, gamma) = engine.computeEffectiveWeights()
                assertEquals(
                    "Weights must sum to 1.0 at speed=$speed, lux=$lux",
                    1.0f, alpha + beta + gamma, 0.002f
                )
                assertTrue("Alpha must be positive", alpha > 0f)
                assertTrue("Beta must be positive", beta > 0f)
                assertTrue("Gamma must be positive", gamma > 0f)
            }
        }
    }

    // ── ADAPTIVE Mode: Context-Aware Weight Changes ──

    @Test
    fun `nighttime reduces CV weight relative to FIXED`() {
        engine.fusionMode = FusionMode.ADAPTIVE
        engine.setFusionContext(FusionContext(
            speedKmh = 50f,
            ambientLightLux = 5f,
            isNightTime = true
        ))
        val (alpha, _, _) = engine.computeEffectiveWeights()
        assertTrue("Adaptive CV weight at night ($alpha) should be < fixed CV weight (0.55)",
            alpha < 0.55f)
    }

    @Test
    fun `nighttime increases sensor weight relative to FIXED`() {
        engine.fusionMode = FusionMode.ADAPTIVE
        engine.setFusionContext(FusionContext(
            speedKmh = 50f,
            ambientLightLux = 5f,
            isNightTime = true
        ))
        val (_, beta, _) = engine.computeEffectiveWeights()
        assertTrue("Adaptive sensor weight at night ($beta) should be > fixed sensor weight (0.30)",
            beta > 0.30f)
    }

    @Test
    fun `high speed reduces CV weight`() {
        engine.fusionMode = FusionMode.ADAPTIVE
        engine.setFusionContext(FusionContext(
            speedKmh = 130f,
            ambientLightLux = 800f,
            isNightTime = false
        ))
        val (alpha, _, _) = engine.computeEffectiveWeights()
        assertTrue("CV weight at high speed ($alpha) should be < 0.55",
            alpha < 0.55f)
    }

    @Test
    fun `high speed increases sensor weight`() {
        engine.fusionMode = FusionMode.ADAPTIVE
        engine.setFusionContext(FusionContext(
            speedKmh = 130f,
            ambientLightLux = 800f,
            isNightTime = false
        ))
        val (_, beta, _) = engine.computeEffectiveWeights()
        assertTrue("Sensor weight at high speed ($beta) should be > 0.30",
            beta > 0.30f)
    }

    @Test
    fun `low speed boosts CV weight`() {
        engine.fusionMode = FusionMode.ADAPTIVE
        engine.setFusionContext(FusionContext(
            speedKmh = 5f,
            ambientLightLux = 800f,
            isNightTime = false
        ))
        val (alpha, _, _) = engine.computeEffectiveWeights()
        assertTrue("CV weight at low speed ($alpha) should be > 0.55",
            alpha > 0.55f)
    }

    // ── Graceful Degradation ──

    @Test
    fun `ADAPTIVE mode with unknown context matches FIXED-like behavior`() {
        engine.fusionMode = FusionMode.ADAPTIVE
        engine.setFusionContext(FusionContext.UNKNOWN)
        val (alpha, beta, gamma) = engine.computeEffectiveWeights()
        // With UNKNOWN context (speed=0, light=-1, isNight=false):
        // Speed is 0 → neutral. Light sensor unavailable, isNight=false → day modifier.
        // Should be very close to FIXED weights.
        assertEquals(1.0f, alpha + beta + gamma, 0.001f)
    }

    // ── FusionResult Tracks Weights ──

    @Test
    fun `FusionResult for ADAPTIVE mode includes effective weights`() {
        engine.fusionMode = FusionMode.ADAPTIVE
        engine.setFusionContext(FusionContext(
            speedKmh = 50f,
            ambientLightLux = 10f,
            isNightTime = true
        ))
        val result = engine.onCvDetection(0.8f, "pothole")

        assertEquals("ADAPTIVE", result.fusionMode)
        assertEquals(1.0f,
            result.effectiveAlpha + result.effectiveBeta + result.effectiveGamma,
            0.002f)
    }

    @Test
    fun `FusionResult for FIXED mode has default weights`() {
        engine.fusionMode = FusionMode.FIXED
        val result = engine.onCvDetection(0.8f, "pothole")

        assertEquals("FIXED", result.fusionMode)
        assertEquals(0.55f, result.effectiveAlpha, 0.001f)
        assertEquals(0.30f, result.effectiveBeta, 0.001f)
        assertEquals(0.15f, result.effectiveGamma, 0.001f)
    }

    // ── Determinism ──

    @Test
    fun `same context produces same weights`() {
        engine.fusionMode = FusionMode.ADAPTIVE
        val context = FusionContext(speedKmh = 45f, ambientLightLux = 250f, isNightTime = false)

        engine.setFusionContext(context)
        val weights1 = engine.computeEffectiveWeights()

        engine.setFusionContext(context)
        val weights2 = engine.computeEffectiveWeights()

        assertEquals(weights1, weights2)
    }

    // ── Fusion Score Impact ──

    @Test
    fun `ADAPTIVE nighttime produces lower score for CV-only detection`() {
        // CV-only: score depends heavily on alpha
        val dayEngine = FusionEngine(fusionMode = FusionMode.ADAPTIVE)
        dayEngine.setFusionContext(FusionContext(
            speedKmh = 50f, ambientLightLux = 800f, isNightTime = false
        ))
        val dayResult = dayEngine.onCvDetection(0.8f)

        val nightEngine = FusionEngine(fusionMode = FusionMode.ADAPTIVE)
        nightEngine.setFusionContext(FusionContext(
            speedKmh = 50f, ambientLightLux = 5f, isNightTime = true
        ))
        val nightResult = nightEngine.onCvDetection(0.8f)

        assertTrue("CV-only detection should score lower at night (${nightResult.fusedScore}) " +
                "than day (${dayResult.fusedScore})",
            nightResult.fusedScore < dayResult.fusedScore)
    }

    @Test
    fun `ADAPTIVE nighttime produces higher score for sensor-only detection`() {
        val dayEngine = FusionEngine(fusionMode = FusionMode.ADAPTIVE)
        dayEngine.setFusionContext(FusionContext(
            speedKmh = 50f, ambientLightLux = 800f, isNightTime = false
        ))
        val sensorEvent = createTestAnomalyEvent(0.9f)
        val dayResult = dayEngine.onSensorAnomaly(sensorEvent)

        val nightEngine = FusionEngine(fusionMode = FusionMode.ADAPTIVE)
        nightEngine.setFusionContext(FusionContext(
            speedKmh = 50f, ambientLightLux = 5f, isNightTime = true
        ))
        val nightResult = nightEngine.onSensorAnomaly(sensorEvent)

        assertTrue("Sensor-only detection should score higher at night (${nightResult.fusedScore}) " +
                "than day (${dayResult.fusedScore})",
            nightResult.fusedScore > dayResult.fusedScore)
    }

    // ── Reset ──

    @Test
    fun `reset clears context to UNKNOWN`() {
        engine.fusionMode = FusionMode.ADAPTIVE
        engine.setFusionContext(FusionContext(speedKmh = 100f, ambientLightLux = 500f))
        engine.reset()
        assertEquals(FusionContext.UNKNOWN, engine.getFusionContext())
    }

    // ── Helper ──

    private fun createTestAnomalyEvent(confidence: Float): AnomalyEvent {
        return AnomalyEvent(
            type = AnomalyType.POTHOLE,
            severity = 0.8f,
            confidence = confidence,
            timestamp = System.nanoTime(),
            accelPeak = 15.0f,
            gyroPeak = 3.0f
        )
    }
}
