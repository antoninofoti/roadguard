package com.example.roadguard.evaluation

/**
 * The four detection modalities compared in the thesis evaluation.
 *
 * Used by the EvaluationSession to run structured A/B experiments
 * comparing CV-only, sensor-only, and fusion approaches on the same routes.
 *
 * Thesis evaluation matrix (Phase E):
 *   Routes × Conditions × 4 Modes × Repetitions
 *
 * Each mode corresponds to a specific weight configuration in the FusionEngine:
 */
enum class DetectionMode(
    val displayName: String,
    val description: String,
    /** Weight for CV modality (alpha). */
    val alpha: Float,
    /** Weight for sensor modality (beta). */
    val beta: Float,
    /** Weight for temporal bonus (gamma). */
    val gamma: Float
) {
    /**
     * Pure computer vision only.
     * Camera (YOLOv8) detects damage; IMU sensors are ignored.
     * Baseline: shows CV performance in isolation.
     */
    CV_ONLY(
        displayName = "CV Only",
        description = "Computer Vision only (YOLOv8). Sensor signals ignored.",
        alpha = 1.0f,
        beta = 0.0f,
        gamma = 0.0f
    ),

    /**
     * Pure IMU sensors only.
     * Kalman-filtered accelerometer and gyroscope detect anomalies; camera is ignored.
     * Baseline: shows sensor performance in isolation.
     */
    SENSOR_ONLY(
        displayName = "Sensor Only",
        description = "IMU sensors only (Kalman + z-score). CV signals ignored.",
        alpha = 0.0f,
        beta = 1.0f,
        gamma = 0.0f
    ),

    /**
     * Fixed late fusion with literature-derived weights.
     * Original system: α=0.55, β=0.30, γ=0.15 (from Python prototype).
     */
    FIXED_FUSION(
        displayName = "Fixed Fusion",
        description = "Late fusion with fixed weights (α=0.55, β=0.30, γ=0.15).",
        alpha = 0.55f,
        beta = 0.30f,
        gamma = 0.15f
    ),

    /**
     * Context-aware adaptive fusion (original thesis contribution).
     * Weights modulate based on GPS speed and ambient light in real-time.
     * The alpha/beta/gamma values here are the BASE values before modulation.
     */
    ADAPTIVE_FUSION(
        displayName = "Adaptive Fusion",
        description = "Context-aware fusion: weights adapt to speed and lighting (Phase D).",
        alpha = 0.55f,
        beta = 0.30f,
        gamma = 0.15f
    );

    companion object {
        /** Returns all modes in the canonical thesis evaluation order. */
        fun evaluationOrder(): List<DetectionMode> =
            listOf(CV_ONLY, SENSOR_ONLY, FIXED_FUSION, ADAPTIVE_FUSION)

        /** Parses a mode from its name string (case-insensitive). */
        fun fromName(name: String): DetectionMode? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
