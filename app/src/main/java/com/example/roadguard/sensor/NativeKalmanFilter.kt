package com.example.roadguard.sensor

import kotlin.math.sqrt

/**
 * Kotlin wrapper around the native C Kalman filter (Phase G.4 — JNI).
 *
 * Provides the same API as [KalmanFilter3D] but delegates computation
 * to the C implementation in `libboardguard_native.so` via JNI.
 *
 * ## Motivation
 * The KalmanFilter1D runs at 50 Hz continuously during driving. Moving the
 * computation to C allows the Android Runtime (ART) to avoid JVM object
 * allocation overhead on the hot path and enables the compiler to apply
 * SIMD / FPU optimisations via `-ffast-math`.
 *
 * ## Memory management
 * Each filter axis allocates a C struct on the native heap. The handles
 * (opaque Long pointers) are released in [close]. Callers should invoke
 * [close] when the filter is no longer needed (e.g., in onDestroy/onPause).
 *
 * @param q Process noise variance (default: 0.01 — same as Kotlin counterpart)
 * @param r Measurement noise variance (default: 0.5 — same as Kotlin counterpart)
 */
class NativeKalmanFilter(
    private val q: Float = 0.01f,
    private val r: Float = 0.5f,
) : AutoCloseable {

    private val kotlinFallback = KalmanFilter3D(q = q, r = r)
    private var nativeEnabled = isAvailable

    // Handles are 0 (invalid) when native lib is not available
    private val handleX: Long = if (nativeEnabled) nativeCreate(q, r) else 0L
    private val handleY: Long = if (nativeEnabled) nativeCreate(q, r) else 0L
    private val handleZ: Long = if (nativeEnabled) nativeCreate(q, r) else 0L

    /** Reusable buffer to avoid FloatArray allocation per call. */
    private val resultBuffer = FloatArray(3)

    init {
        if (nativeEnabled && (handleX == 0L || handleY == 0L || handleZ == 0L)) {
            nativeEnabled = false
            releaseHandlesSilently()
        }
    }

    /**
     * Filter a 3-axis IMU reading.
     * Uses the single-JNI-call [nativeUpdate3D] to minimise JNI transition overhead.
     *
     * @return Triple of (filteredX, filteredY, filteredZ)
     */
    fun update(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        if (!nativeEnabled) {
            return kotlinFallback.update(x, y, z)
        }

        return try {
            nativeUpdate3D(handleX, handleY, handleZ, x, y, z, resultBuffer)
            Triple(resultBuffer[0], resultBuffer[1], resultBuffer[2])
        } catch (_: UnsatisfiedLinkError) {
            nativeEnabled = false
            kotlinFallback.update(x, y, z)
        }
    }

    /**
     * Filter a 3-axis reading and return the vector magnitude.
     * Useful for anomaly detection which operates on ||accel|| directly.
     *
     * @return sqrt(fx² + fy² + fz²)
     */
    fun updateAndGetMagnitude(x: Float, y: Float, z: Float): Float {
        if (!nativeEnabled) {
            return kotlinFallback.updateAndGetMagnitude(x, y, z)
        }

        return try {
            nativeUpdate3D(handleX, handleY, handleZ, x, y, z, resultBuffer)
            val fx = resultBuffer[0]; val fy = resultBuffer[1]; val fz = resultBuffer[2]
            sqrt(fx * fx + fy * fy + fz * fz)
        } catch (_: UnsatisfiedLinkError) {
            nativeEnabled = false
            kotlinFallback.updateAndGetMagnitude(x, y, z)
        }
    }

    /**
     * Reset all three axis filters to their initial state (x=0, p=1).
     * Call this when restarting a measurement session.
     */
    fun reset() {
        kotlinFallback.reset()
        if (!nativeEnabled) {
            return
        }

        try {
            nativeReset(handleX)
            nativeReset(handleY)
            nativeReset(handleZ)
        } catch (_: UnsatisfiedLinkError) {
            nativeEnabled = false
        }
    }

    /**
     * Release native memory. Must be called when the filter is no longer needed.
     * After calling this, any further [update]/[reset] calls are undefined.
     */
    override fun close() {
        releaseHandlesSilently()
        nativeEnabled = false
    }

    /** True when JNI backend is currently active; false means Kotlin fallback. */
    fun isUsingNative(): Boolean = nativeEnabled

    private fun releaseHandlesSilently() {
        if (handleX != 0L) {
            runCatching { nativeDestroy(handleX) }
        }
        if (handleY != 0L) {
            runCatching { nativeDestroy(handleY) }
        }
        if (handleZ != 0L) {
            runCatching { nativeDestroy(handleZ) }
        }
    }

    // ── JNI declarations ────────────────────────────────────────────────

    private external fun nativeCreate(q: Float, r: Float): Long
    private external fun nativeUpdate(handle: Long, measurement: Float): Float
    private external fun nativeReset(handle: Long)
    private external fun nativeDestroy(handle: Long)
    private external fun nativeUpdate3D(
        handleX: Long, handleY: Long, handleZ: Long,
        x: Float, y: Float, z: Float,
        result: FloatArray
    )

    companion object {
        /**
         * Whether the native library was successfully loaded.
         * False in JVM unit test environments where the .so is not present.
         * Use as a runtime feature flag to fall back to [KalmanFilter3D].
         */
        val isAvailable: Boolean = try {
            System.loadLibrary("roadguard_native")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }
}
