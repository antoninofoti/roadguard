package com.example.roadguard.sensor

import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Sliding window anomaly detector for IMU sensor data.
 *
 * Analyzes a window of recent Kalman-filtered sensor readings to detect
 * statistical anomalies indicating road damage. Uses z-score analysis
 * on accelerometer and gyroscope magnitudes.
 *
 * Translated from the validated Python prototype (analysis/sensor_fusion_prototype.py).
 *
 * @param windowSize Number of recent readings to analyze (at 50Hz, 50 = 1 second)
 * @param accelStdThreshold Z-score threshold for accelerometer anomaly.
 *        Higher = fewer false positives but may miss smaller damage.
 * @param gyroStdThreshold Z-score threshold for gyroscope anomaly.
 * @param minSeverity Minimum severity to report (filters very minor bumps).
 */
class AnomalyDetector(
    private val windowSize: Int = 50,
    private val accelStdThreshold: Float = 2.5f,
    private val gyroStdThreshold: Float = 2.0f,
    private val minSeverity: Float = 0.1f
) {
    private val accelWindow = ArrayDeque<Float>(windowSize)
    private val gyroWindow = ArrayDeque<Float>(windowSize)

    // Cooldown to avoid reporting the same event multiple times
    private var lastEventTimestamp: Long = 0L
    private val cooldownNanos: Long = 500_000_000L  // 500ms cooldown between events

    /**
     * Add a new filtered sensor reading and check for anomalies.
     *
     * @param accelMagnitude Kalman-filtered accelerometer magnitude (m/s²)
     * @param gyroMagnitude Kalman-filtered gyroscope magnitude (rad/s)
     * @param timestamp System.nanoTime() of the reading
     * @return [AnomalyEvent] if an anomaly was detected, null otherwise
     */
    fun addReading(
        accelMagnitude: Float,
        gyroMagnitude: Float,
        timestamp: Long
    ): AnomalyEvent? {
        // Maintain sliding window (FIFO)
        if (accelWindow.size >= windowSize) {
            accelWindow.removeFirst()
            gyroWindow.removeFirst()
        }
        accelWindow.addLast(accelMagnitude)
        gyroWindow.addLast(gyroMagnitude)

        // Need at least half a window of data before detecting
        if (accelWindow.size < windowSize / 2) {
            return null
        }

        // Cooldown check
        if (timestamp - lastEventTimestamp < cooldownNanos) {
            return null
        }

        // Calculate statistics
        val accelStats = calculateStats(accelWindow)
        val gyroStats = calculateStats(gyroWindow)

        // Z-score of current reading
        val accelZScore = (accelMagnitude - accelStats.mean) / (accelStats.std + 1e-6f)
        val gyroZScore = (gyroMagnitude - gyroStats.mean) / (gyroStats.std + 1e-6f)

        val isAccelAnomaly = accelZScore > accelStdThreshold
        // Require meaningful raw gyro magnitude (> 0.5 rad/s) to avoid
        // false positives when baseline std is near zero.
        val isGyroAnomaly = gyroZScore > gyroStdThreshold && gyroMagnitude > 0.5f

        if (!isAccelAnomaly && !isGyroAnomaly) {
            return null
        }

        // Update cooldown
        lastEventTimestamp = timestamp

        // Classify anomaly type
        val anomalyType = classifyAnomaly(accelZScore, gyroZScore, gyroMagnitude)

        // Calculate severity (normalized 0-1)
        val severity = min(1.0f, max(minSeverity, accelZScore / 10.0f))

        // Calculate confidence based on signal agreement
        val confidence = when {
            isAccelAnomaly && isGyroAnomaly -> 0.9f  // Both sensors agree
            isAccelAnomaly -> 0.6f                    // Accel only
            else -> 0.4f                              // Gyro only
        }

        return AnomalyEvent(
            type = anomalyType,
            severity = severity,
            confidence = confidence,
            timestamp = timestamp,
            accelPeak = accelMagnitude,
            gyroPeak = gyroMagnitude
        )
    }

    /**
     * Classify the anomaly type based on the accelerometer and gyroscope signatures.
     *
     * - POTHOLE: high accel + high gyro (sharp vertical impact with angular change)
     * - SPEED_BUMP: high accel + low gyro (gradual vertical, vehicle stays level)
     * - BUMP: moderate accel spike
     * - ROUGHNESS: lower-level sustained irregularity
     */
    private fun classifyAnomaly(accelZScore: Float, gyroZScore: Float, gyroRaw: Float): AnomalyType {
        // Use raw gyro magnitude for classification to avoid misleading z-scores
        // when baseline gyro has near-zero std deviation.
        val gyroIsSignificant = gyroRaw > 0.5f && gyroZScore > 3.0f
        return when {
            accelZScore > 4.0f && gyroIsSignificant -> AnomalyType.POTHOLE
            accelZScore > 3.0f && !gyroIsSignificant -> AnomalyType.SPEED_BUMP
            accelZScore > 2.5f -> AnomalyType.BUMP
            else -> AnomalyType.ROUGHNESS
        }
    }

    /** Reset the detector's internal state. */
    fun reset() {
        accelWindow.clear()
        gyroWindow.clear()
        lastEventTimestamp = 0L
    }

    private data class WindowStats(val mean: Float, val std: Float)

    private fun calculateStats(window: ArrayDeque<Float>): WindowStats {
        if (window.isEmpty()) return WindowStats(0f, 1f)

        var sum = 0f
        for (v in window) sum += v
        val mean = sum / window.size

        var sumSqDiff = 0f
        for (v in window) {
            val diff = v - mean
            sumSqDiff += diff * diff
        }
        val std = sqrt(sumSqDiff / window.size)

        return WindowStats(mean, std)
    }
}
