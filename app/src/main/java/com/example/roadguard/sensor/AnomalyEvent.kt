package com.example.roadguard.sensor

import com.google.firebase.firestore.GeoPoint

/**
 * Type of road anomaly detected by the sensor fusion pipeline.
 */
enum class AnomalyType {
    POTHOLE,     // Vertical impact — sharp spike on Z-axis + gyroscope
    BUMP,        // Moderate vertical disturbance
    SPEED_BUMP,  // Large but gradual acceleration change, low gyroscope
    ROUGHNESS    // Sustained elevated vibration over multiple samples
}

/**
 * An anomaly event detected by [AnomalyDetector] from IMU sensor data.
 *
 * Represents a road surface irregularity with severity and confidence scores
 * that will be combined with CV detection by the FusionEngine.
 */
data class AnomalyEvent(
    val type: AnomalyType,
    val severity: Float,        // 0.0 - 1.0 normalized severity
    val confidence: Float,      // 0.0 - 1.0 detection confidence
    val timestamp: Long,        // Epoch ms used for cross-modal temporal correlation
    val accelPeak: Float,       // Peak accelerometer magnitude during event
    val gyroPeak: Float,        // Peak gyroscope magnitude during event
    val location: GeoPoint? = null  // GPS location at time of detection
)
