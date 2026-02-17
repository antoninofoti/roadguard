package com.example.roadguard.sensor

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Unit tests for [KalmanFilter1D] and [KalmanFilter3D].
 *
 * Validates that the Kalman filter:
 * 1. Reduces noise in sensor readings
 * 2. Preserves sharp spikes (e.g., from potholes)
 * 3. Converges to the true value for constant input
 */
class KalmanFilter3DTest {

    private lateinit var filter1D: KalmanFilter1D
    private lateinit var filter3D: KalmanFilter3D

    @Before
    fun setup() {
        filter1D = KalmanFilter1D(q = 0.01f, r = 0.5f)
        filter3D = KalmanFilter3D(q = 0.01f, r = 0.5f)
    }

    @Test
    fun `1D filter converges to constant value`() {
        val trueValue = 9.81f
        var lastFiltered = 0f

        // Feed constant value with noise for 100 iterations
        repeat(100) {
            val noisy = trueValue + (Math.random().toFloat() - 0.5f) * 2f
            lastFiltered = filter1D.update(noisy)
        }

        // Should be close to the true value
        assertTrue(
            "Filter should converge near $trueValue, got $lastFiltered",
            abs(lastFiltered - trueValue) < 0.5f
        )
    }

    @Test
    fun `1D filter reduces noise variance`() {
        val trueValue = 5.0f
        val rawValues = mutableListOf<Float>()
        val filteredValues = mutableListOf<Float>()

        repeat(200) {
            val noisy = trueValue + (Math.random().toFloat() - 0.5f) * 4f
            val filtered = filter1D.update(noisy)
            rawValues.add(noisy)
            filteredValues.add(filtered)
        }

        val rawVariance = variance(rawValues)
        val filteredVariance = variance(filteredValues.drop(20)) // Skip initial transient

        assertTrue(
            "Filtered variance ($filteredVariance) should be less than raw variance ($rawVariance)",
            filteredVariance < rawVariance
        )
    }

    @Test
    fun `1D filter preserves spike events`() {
        // Normal baseline for 50 samples
        repeat(50) {
            filter1D.update(9.81f + (Math.random().toFloat() - 0.5f) * 0.2f)
        }

        // Inject spike (pothole impact)
        val spikeValue = 18.0f
        val filteredSpike = filter1D.update(spikeValue)

        // The filtered value should show significant deviation from baseline
        // (won't match the exact spike but should be noticeably elevated)
        assertTrue(
            "Filtered spike ($filteredSpike) should be above baseline (>10.0)",
            filteredSpike > 10.0f
        )
    }

    @Test
    fun `1D filter reset returns to initial state`() {
        // Feed some data
        repeat(50) { filter1D.update(10f) }
        filter1D.reset()

        // After reset, first update should be influenced by the measurement
        val result = filter1D.update(5f)
        // With p=1 after reset, Kalman gain k = 1/(1+0.5) ≈ 0.67
        // x = 0 + 0.67 * 5 ≈ 3.33
        assertTrue("After reset, filter should respond to new data", result > 2f && result < 5f)
    }

    @Test
    fun `3D filter processes all axes independently`() {
        val (fx, fy, fz) = filter3D.update(1.0f, 2.0f, 3.0f)

        // All outputs should be non-zero (filter has processed them)
        assertTrue("X should be filtered", fx > 0f)
        assertTrue("Y should be filtered", fy > 0f)
        assertTrue("Z should be filtered", fz > 0f)

        // Values should differ (different inputs, independently filtered)
        assertTrue("X != Y", abs(fx - fy) > 0.01f)
        assertTrue("Y != Z", abs(fy - fz) > 0.01f)
    }

    @Test
    fun `3D filter magnitude calculation is correct`() {
        // After convergence, magnitude should approximate sqrt(x² + y² + z²)
        repeat(100) {
            filter3D.update(3.0f, 4.0f, 0.0f)  // Expected magnitude: 5.0
        }

        val magnitude = filter3D.updateAndGetMagnitude(3.0f, 4.0f, 0.0f)
        assertTrue(
            "Magnitude should be close to 5.0, got $magnitude",
            abs(magnitude - 5.0f) < 0.5f
        )
    }

    @Test
    fun `3D filter reset clears all axes`() {
        repeat(50) { filter3D.update(10f, 10f, 10f) }
        filter3D.reset()

        val (fx, fy, fz) = filter3D.update(1f, 1f, 1f)
        // After reset, values should be small (filter starts fresh)
        val magnitude = sqrt(fx * fx + fy * fy + fz * fz)
        assertTrue("After reset, magnitude should be small, got $magnitude", magnitude < 3f)
    }

    private fun variance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.sum() / values.size
        return values.map { (it - mean) * (it - mean) }.sum() / values.size
    }
}
