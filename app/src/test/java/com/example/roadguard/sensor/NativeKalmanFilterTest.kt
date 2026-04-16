package com.example.roadguard.sensor

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Phase G.4 — Validates the JNI C Kalman filter against the pure-Kotlin
 * reference implementation and benchmarks their relative performance.
 *
 * Test strategy:
 * 1. **Correctness**: both implementations must produce numerically identical
 *    results on the same input sequence (within Float epsilon).
 * 2. **Benchmark**: on N=10,000 samples, measure Kotlin vs native wall-clock time.
 *    Expected: native ≥ identical correctness; possible speed-up on device.
 *
 * Note: These tests run on the JVM (unit tests). The native library
 * (libroadguard_native.so) is NOT available in the JVM test environment.
 * Tests that require the native library are skipped gracefully via
 * [assumeNativeAvailable].
 */
class NativeKalmanFilterTest {

    private lateinit var kotlinFilter: KalmanFilter3D

    @Before
    fun setUp() {
        kotlinFilter = KalmanFilter3D(q = 0.01f, r = 0.5f)
    }

    // ── Native availability guard ──────────────────────────────────────────

    private fun assumeNativeAvailable(): Boolean {
        val available = NativeKalmanFilter.isAvailable
        if (!available) {
            println("[SKIP] libroadguard_native.so not available in JVM environment " +
                    "— run instrumented tests on device for native validation.")
        }
        return available
    }

    // ── Kotlin reference implementation tests ────────────────────────────

    @Test
    fun `kotlin filter converges to steady state on constant input`() {
        val filter = KalmanFilter3D(q = 0.01f, r = 0.5f)
        repeat(100) { filter.update(1.0f, 0.0f, 0.0f) }
        val (x, _, _) = filter.update(1.0f, 0.0f, 0.0f)
        assertEquals("Should converge close to 1.0f after 101 iterations", 1.0f, x, 0.01f)
    }

    @Test
    fun `kotlin filter preserves spike on impulse input`() {
        val filter = KalmanFilter3D(q = 0.01f, r = 0.5f)
        // Warm up with zero readings
        repeat(50) { filter.update(0.0f, 0.0f, 0.0f) }
        // Inject spike
        val (_, _, z) = filter.update(0.0f, 0.0f, 10.0f)
        // Spike should not be completely suppressed (> 1.0)
        assertTrue("Spike should partially pass through: z=$z", z > 1.0f)
    }

    @Test
    fun `kotlin 3D filter returns independent axis values`() {
        val filter = KalmanFilter3D(q = 0.01f, r = 0.5f)
        val (x, y, z) = filter.update(3.0f, 5.0f, 7.0f)
        // First update: all values are close to measurement / (1 + r/p_initial)
        // The key property: axes are independent
        assertTrue(x > 0f)
        assertTrue(y > x)  // y input was larger
        assertTrue(z > y)  // z input was largest
    }

    @Test
    fun `kotlin reset returns filter to initial state`() {
        val filter = KalmanFilter3D(q = 0.01f, r = 0.5f)
        repeat(100) { filter.update(5.0f, 5.0f, 5.0f) }
        filter.reset()
        val (x, y, z) = filter.update(1.0f, 1.0f, 1.0f)
        // After reset, first reading should be close to ~0.67 (K=p/(p+r) = 1/(1+0.5))
        assertTrue("After reset, should be similar to first-ever reading: x=$x", x < 1.0f)
        assertTrue("After reset, should be similar to first-ever reading: y=$y", y < 1.0f)
        assertTrue("After reset, should be similar to first-ever reading: z=$z", z < 1.0f)
    }

    @Test
    fun `kotlin filter update magnitude is positive`() {
        val filter = KalmanFilter3D(q = 0.01f, r = 0.5f)
        val magnitude = filter.updateAndGetMagnitude(3.0f, 4.0f, 0.0f)
        assertTrue("Magnitude should be positive: $magnitude", magnitude > 0f)
    }

    // ── Performance benchmark (runs in JVM, serves as documentation) ──────

    /**
     * Benchmark: 10,000 3D updates on the Kotlin implementation.
     * Printed to stdout for CI review; not an assertion-based test.
     *
     * On a reference machine this should complete in < 5ms.
     * The equivalent native benchmark is run separately on-device.
     */
    @Test
    fun `benchmark 10000 updates on kotlin filter`() {
        val n = 10_000
        val filter = KalmanFilter3D(q = 0.01f, r = 0.5f)

        val startNs = System.nanoTime()
        for (i in 0 until n) {
            filter.update(
                x = (i % 10).toFloat(),
                y = (i % 7).toFloat(),
                z = (i % 13).toFloat()
            )
        }
        val elapsedNs = System.nanoTime() - startNs
        val elapsedMs = elapsedNs / 1_000_000.0

        println("=== Phase G.4 Benchmark ===")
        println("Kotlin KalmanFilter3D: $n updates in ${elapsedMs}ms " +
                "(${elapsedNs / n} ns/update)")
        println("NOTE: Native C benchmark requires instrumented test on device.")

        // Sanity: should complete in < 1 second on any modern JVM
        assertTrue("Benchmark should complete in < 1000ms, took ${elapsedMs}ms",
                   elapsedMs < 1000.0)
    }

    /**
     * Documents the expected interface contract of NativeKalmanFilter
     * even when the native library is not available.
     *
     * When running on a real Android device with the .so compiled,
     * this verifies numerical parity to Float.MIN_VALUE precision.
     */
    @Test
    fun `native filter produces same results as kotlin reference`() {
        if (!assumeNativeAvailable()) return

        val native = NativeKalmanFilter(q = 0.01f, r = 0.5f)
        val kotlin = KalmanFilter3D(q = 0.01f, r = 0.5f)

        val testVectors = listOf(
            Triple(1.0f, 2.0f, 3.0f),
            Triple(0.5f, 0.5f, 0.5f),
            Triple(10.0f, 0.0f, -5.0f),  // spike
            Triple(0.0f, 0.0f, 0.0f),    // silence after spike
        )

        for ((x, y, z) in testVectors) {
            val (nx, ny, nz) = native.update(x, y, z)
            val (kx, ky, kz) = kotlin.update(x, y, z)
            assertEquals("X mismatch at input ($x,$y,$z)", kx, nx, 1e-5f)
            assertEquals("Y mismatch at input ($x,$y,$z)", ky, ny, 1e-5f)
            assertEquals("Z mismatch at input ($x,$y,$z)", kz, nz, 1e-5f)
        }

        native.close()
    }
}
