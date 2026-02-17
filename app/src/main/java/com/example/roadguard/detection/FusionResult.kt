package com.example.roadguard.detection

import com.example.roadguard.sensor.AnomalyEvent

/**
 * Action to take based on the fusion result.
 */
enum class FusionAction {
    AUTO_REPORT,   // Score > autoThreshold — create report automatically
    PROMPT_USER,   // Score between promptThreshold and autoThreshold — ask user
    DISCARD        // Score < promptThreshold — log only, do not report
}

/**
 * Result of combining CV and sensor signals via [FusionEngine].
 */
data class FusionResult(
    val fusedScore: Float,              // Combined confidence score (0.0 - 1.0)
    val action: FusionAction,           // What to do with this detection
    val cvConfidence: Float,            // CV model confidence for this detection
    val sensorConfidence: Float,        // Sensor anomaly confidence
    val temporalBonus: Float,           // Bonus from temporal correlation (+/-2s)
    val damageType: String,             // Best guess: "pothole", "bump", etc.
    val detectionSource: String,        // DUAL_CONFIRMED, CV_ONLY, SENSOR_ONLY
    val anomalyEvent: AnomalyEvent?,    // Sensor event if available
    val timestamp: Long                 // When the fusion was computed
)
