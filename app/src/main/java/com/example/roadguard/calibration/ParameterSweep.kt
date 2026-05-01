package com.example.roadguard.calibration

import android.util.Log
import com.example.roadguard.sensor.AnomalyDetector
import com.example.roadguard.sensor.AnomalyEvent
import com.example.roadguard.sensor.KalmanFilter3D
import kotlin.math.sqrt

/**
 * Configuration for a parameter sweep over the Kalman filter
 * and anomaly detector parameters.
 *
 * @param kalmanQ Process noise values to test
 * @param kalmanR Measurement noise values to test
 * @param accelThresholds Accelerometer z-score thresholds to test
 * @param gyroThresholds Gyroscope z-score thresholds to test
 */
data class SweepConfig(
    val kalmanQ: List<Float> = listOf(0.001f, 0.005f, 0.01f, 0.05f, 0.1f),
    val kalmanR: List<Float> = listOf(0.1f, 0.3f, 0.5f, 1.0f, 2.0f),
    val accelThresholds: List<Float> = listOf(1.5f, 2.0f, 2.5f, 3.0f),
    val gyroThresholds: List<Float> = listOf(1.5f, 2.0f, 2.5f)
) {
    /** Total number of parameter combinations in this sweep. */
    val totalCombinations: Int
        get() = kalmanQ.size * kalmanR.size * accelThresholds.size * gyroThresholds.size
}

/**
 * Result of evaluating one parameter combination against ground truth.
 *
 * @param q Kalman process noise used
 * @param r Kalman measurement noise used
 * @param accelThreshold Accelerometer z-score threshold used
 * @param gyroThreshold Gyroscope z-score threshold used
 * @param truePositives Number of correctly detected anomalies
 * @param falsePositives Number of detections not matching ground truth
 * @param falseNegatives Number of ground truth entries not detected
 * @param precision TP / (TP + FP)
 * @param recall TP / (TP + FN)
 * @param f1 2 × (P × R) / (P + R)
 */
data class SweepResult(
    val q: Float,
    val r: Float,
    val accelThreshold: Float,
    val gyroThreshold: Float,
    val truePositives: Int,
    val falsePositives: Int,
    val falseNegatives: Int,
    val precision: Float,
    val recall: Float,
    val f1: Float
)

/**
 * A single raw sensor reading parsed from a CSV log file.
 */
data class CsvSensorReading(
    val timestamp: Long,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val lat: Double?,
    val lng: Double?
)

/**
 * Grid search over Kalman and anomaly detector parameters.
 *
 * For each combination of (Q, R, accelThreshold, gyroThreshold):
 * 1. Creates a fresh KalmanFilter3D and AnomalyDetector
 * 2. Processes all sensor readings from the CSV log
 * 3. Compares detected anomalies against GPS ground truth
 * 4. Computes precision, recall, and F1 score
 *
 * This enables systematic calibration of sensor parameters for
 * a specific device and road environment (Thesis Calibration Protocol).
 */
class ParameterSweep {

    companion object {
        /** Earth's radius in meters for haversine distance. */
        private const val EARTH_RADIUS_M = 6_371_000.0
    }

    /**
     * Run a full parameter sweep.
     *
     * @param readings Parsed sensor readings from a CSV driving log
     * @param groundTruth Known damage locations for evaluation
     * @param config Parameter ranges to sweep
     * @return List of results, one per parameter combination, sorted by F1 descending
     */
    fun runSweep(
        readings: List<CsvSensorReading>,
        groundTruth: List<GroundTruthEntry>,
        config: SweepConfig = SweepConfig()
    ): List<SweepResult> {
        val results = mutableListOf<SweepResult>()

        for (q in config.kalmanQ) {
            for (r in config.kalmanR) {
                for (accelT in config.accelThresholds) {
                    for (gyroT in config.gyroThresholds) {
                        val result = evaluateCombination(
                            readings, groundTruth, q, r, accelT, gyroT
                        )
                        results.add(result)
                    }
                }
            }
        }

        return results.sortedByDescending { it.f1 }
    }

    /**
     * Evaluate a single parameter combination.
     */
    internal fun evaluateCombination(
        readings: List<CsvSensorReading>,
        groundTruth: List<GroundTruthEntry>,
        q: Float,
        r: Float,
        accelThreshold: Float,
        gyroThreshold: Float
    ): SweepResult {
        // Create fresh pipeline with these parameters
        val kalman = KalmanFilter3D(q = q, r = r)
        val detector = AnomalyDetector(
            windowSize = 50,
            accelStdThreshold = accelThreshold,
            gyroStdThreshold = gyroThreshold
        )

        // Process all readings and collect detected anomalies
        val detectedAnomalies = mutableListOf<DetectedAnomaly>()

        for (reading in readings) {
            val filteredAccelMag = kalman.updateAndGetMagnitude(
                reading.accelX, reading.accelY, reading.accelZ
            )
            // Create a separate Kalman for gyro (as in SensorService)
            // For simplicity, compute raw magnitude for gyro
            val gyroMag = sqrt(
                reading.gyroX * reading.gyroX +
                reading.gyroY * reading.gyroY +
                reading.gyroZ * reading.gyroZ
            )

            val event = detector.addReading(filteredAccelMag, gyroMag, reading.timestamp)
            if (event != null && reading.lat != null && reading.lng != null) {
                detectedAnomalies.add(
                    DetectedAnomaly(
                        lat = reading.lat,
                        lng = reading.lng,
                        event = event
                    )
                )
            }
        }

        // Match detections against ground truth
        val matchedGt = mutableSetOf<Int>()      // Indices of matched ground truth entries
        val matchedDet = mutableSetOf<Int>()      // Indices of matched detections

        for ((detIdx, det) in detectedAnomalies.withIndex()) {
            val matchingGtIdx = groundTruth.indices
                .filter { it !in matchedGt }
                .firstOrNull { gtIdx ->
                    val gt = groundTruth[gtIdx]
                    val distance = haversineDistance(det.lat, det.lng, gt.lat, gt.lng)
                    distance <= gt.radiusMeters
                }

            if (matchingGtIdx != null) {
                matchedGt.add(matchingGtIdx)
                matchedDet.add(detIdx)
            }
        }

        val tp = matchedGt.size
        val fp = detectedAnomalies.size - matchedDet.size
        val fn = groundTruth.size - matchedGt.size

        val precision = if (tp + fp > 0) tp.toFloat() / (tp + fp) else 0f
        val recall = if (tp + fn > 0) tp.toFloat() / (tp + fn) else 0f
        val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0f

        return SweepResult(
            q = q, r = r,
            accelThreshold = accelThreshold,
            gyroThreshold = gyroThreshold,
            truePositives = tp,
            falsePositives = fp,
            falseNegatives = fn,
            precision = precision,
            recall = recall,
            f1 = f1
        )
    }

    /**
     * Parse sensor readings from a CSV log file.
     *
     * Expected format matches SensorService CSV output:
     * ```
     * Timestamp,Accel_Raw_X,Accel_Raw_Y,Accel_Raw_Z,Accel_Filtered_Mag,
     * Gyro_Raw_X,Gyro_Raw_Y,Gyro_Raw_Z,Gyro_Filtered_Mag,
     * Lat,Lng,Speed_kmh,Anomaly_Type,Anomaly_Confidence
     * ```
     */
    fun parseCsv(csvContent: String): List<CsvSensorReading> {
        val lines = csvContent.trim().lines()
        if (lines.size < 2) return emptyList()

        return lines.drop(1) // Skip header
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(",").map { it.trim() }
                if (parts.size < 9) return@mapNotNull null
                try {
                    CsvSensorReading(
                        timestamp = parts[0].toLong(),
                        accelX = parts[1].toFloat(),
                        accelY = parts[2].toFloat(),
                        accelZ = parts[3].toFloat(),
                        // parts[4] = Accel_Filtered_Mag (skip, we recompute)
                        gyroX = parts[5].toFloat(),
                        gyroY = parts[6].toFloat(),
                        gyroZ = parts[7].toFloat(),
                        // parts[8] = Gyro_Filtered_Mag (skip, we recompute)
                        lat = parts.getOrNull(9)?.toDoubleOrNull(),
                        lng = parts.getOrNull(10)?.toDoubleOrNull()
                    )
                } catch (e: Exception) {
                    Log.w("ParameterSweep", "Failed to parse sensor reading: ${e.message}")
                    null
                }
            }
    }

    // ── Internal helpers ──────────────────────────────────────────

    private data class DetectedAnomaly(
        val lat: Double,
        val lng: Double,
        val event: AnomalyEvent
    )

    /**
     * Haversine distance between two points in meters.
     */
    internal fun haversineDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * Math.asin(sqrt(a))
    }
}
