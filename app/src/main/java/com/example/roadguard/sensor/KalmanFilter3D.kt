package com.example.roadguard.sensor

import kotlin.math.sqrt

/**
 * 1D Kalman filter for sensor noise reduction.
 *
 * Uses a constant-velocity model to smooth noisy sensor readings while
 * preserving sharp spikes (e.g., from potholes). Validated in the Python
 * prototype (see analysis/sensor_fusion_prototype.py).
 *
 * @param q Process noise variance — how much we trust the motion model.
 *          Lower values = smoother output, slower response to changes.
 * @param r Measurement noise variance — how much we trust the raw sensor.
 *          Higher values = more smoothing, less trust in individual readings.
 */
class KalmanFilter1D(
    private val q: Float = 0.01f,
    private val r: Float = 0.5f
) {
    private var x: Float = 0f   // State estimate
    private var p: Float = 1f   // Estimation error covariance

    /**
     * Process a new measurement and return the filtered value.
     *
     * @param measurement Raw sensor reading
     * @return Filtered (smoothed) value
     */
    fun update(measurement: Float): Float {
        // Prediction step
        p += q

        // Correction step
        val k = p / (p + r)  // Kalman gain
        x += k * (measurement - x)
        p *= (1 - k)

        return x
    }

    /** Reset filter to initial state. */
    fun reset() {
        x = 0f
        p = 1f
    }
}

/**
 * 3D Kalman filter applying independent 1D filters to each IMU axis.
 *
 * Reduces noise on accelerometer and gyroscope readings while preserving
 * the characteristic peaks of road damage events. Parameters Q and R
 * should be calibrated using the Kaggle Road Quality Dataset.
 *
 * @param q Process noise for all axes
 * @param r Measurement noise for all axes
 */
class KalmanFilter3D(
    q: Float = 0.01f,
    r: Float = 0.5f
) {
    private val filterX = KalmanFilter1D(q, r)
    private val filterY = KalmanFilter1D(q, r)
    private val filterZ = KalmanFilter1D(q, r)

    /**
     * Filter a 3-axis reading.
     *
     * @return Triple of (filteredX, filteredY, filteredZ)
     */
    fun update(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        return Triple(
            filterX.update(x),
            filterY.update(y),
            filterZ.update(z)
        )
    }

    /**
     * Filter a 3-axis reading and return the magnitude.
     *
     * @return Magnitude of the filtered vector: sqrt(fx² + fy² + fz²)
     */
    fun updateAndGetMagnitude(x: Float, y: Float, z: Float): Float {
        val (fx, fy, fz) = update(x, y, z)
        return sqrt(fx * fx + fy * fy + fz * fz)
    }

    /** Reset all axis filters. */
    fun reset() {
        filterX.reset()
        filterY.reset()
        filterZ.reset()
    }
}
