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
 *
 * Includes the effective weights used during computation so that
 * the thesis can compare FIXED vs ADAPTIVE fusion modes quantitatively.
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
    val timestamp: Long,                // When the fusion was computed

    // --- Phase D: Adaptive Fusion tracking fields ---
    val effectiveAlpha: Float = 0.55f,  // CV weight actually used
    val effectiveBeta: Float = 0.30f,   // Sensor weight actually used
    val effectiveGamma: Float = 0.15f,  // Temporal weight actually used
    val fusionMode: String = "FIXED"    // FIXED or ADAPTIVE
)
