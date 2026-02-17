package com.example.roadguard.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Status of a report in the operator workflow.
 */
enum class ReportStatus {
    PENDING,     // Newly created, awaiting operator review
    CONFIRMED,   // Confirmed by operator as real damage
    REJECTED,    // Rejected by operator (false positive)
    RESOLVED     // Damage has been repaired
}

/**
 * How the report was generated.
 */
enum class DetectionSource {
    CV_ONLY,          // Only computer vision detected the damage
    SENSOR_ONLY,      // Only IMU sensors detected the damage
    DUAL_CONFIRMED,   // Both CV and sensor agree (highest confidence)
    MANUAL            // User manually created the report
}

/**
 * A road damage report with fusion metadata.
 *
 * Backward compatible with existing Firestore documents — all new fields
 * have sensible defaults so old reports deserialize correctly.
 */
data class Report(
    @DocumentId
    val id: String = "",
    val imageUrl: String = "",
    val location: GeoPoint? = null,
    @ServerTimestamp
    val timestamp: Date? = null,
    val userId: String = "",
    val severity: Float = 0f,

    // --- Phase 2: Fusion & Status fields ---
    val status: String = ReportStatus.PENDING.name,
    val detectionSource: String = DetectionSource.MANUAL.name,
    val cvConfidence: Float = 0f,
    val sensorConfidence: Float = 0f,
    val fusedScore: Float = 0f,
    val damageType: String = "",          // "pothole", "bump", "speed_bump", "roughness"

    // --- Operator workflow fields ---
    val operatorId: String = "",          // Operator who handled this report
    val resolvedAt: Date? = null,
    val notes: String = ""                // Operator notes
)
