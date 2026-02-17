package com.example.roadguard.detection

import android.util.Log
import com.example.roadguard.model.DetectionSource
import com.example.roadguard.sensor.AnomalyEvent
import com.example.roadguard.sensor.AnomalyType
import kotlin.math.max
import kotlin.math.min

/**
 * Late-fusion engine that combines CV detection and IMU sensor anomaly signals.
 *
 * Implements the fusion formula validated in the Python prototype:
 *
 *     Score = α × CV_confidence + β × Sensor_confidence + γ × Temporal_bonus
 *
 * Where:
 * - α = 0.55 (CV weight — higher because visual provides spatial detail)
 * - β = 0.30 (Sensor weight — confirms physical road impact)
 * - γ = 0.15 (Temporal bonus — applied when both signals within ±2s window)
 *
 * Decision thresholds:
 * - Score > 0.75 → AUTO_REPORT (dual-confirmed, no user intervention)
 * - Score > 0.50 → PROMPT_USER (ask user to confirm)
 * - Score < 0.50 → DISCARD (log only)
 *
 * @param cvWeight Weight for CV confidence (α)
 * @param sensorWeight Weight for sensor confidence (β)
 * @param temporalWeight Weight for temporal correlation bonus (γ)
 * @param autoThreshold Score above which reports are created automatically
 * @param promptThreshold Score above which user is prompted for confirmation
 * @param temporalWindowMs Time window in ms for temporal correlation (±window)
 */
class FusionEngine(
    private val cvWeight: Float = 0.55f,
    private val sensorWeight: Float = 0.30f,
    private val temporalWeight: Float = 0.15f,
    private val autoThreshold: Float = 0.75f,
    private val promptThreshold: Float = 0.50f,
    private val temporalWindowMs: Long = 2000L
) {
    companion object {
        private const val TAG = "FusionEngine"
    }

    // Recent events buffer for temporal correlation
    private val recentCvDetections = mutableListOf<CvDetection>()
    private val recentSensorEvents = mutableListOf<AnomalyEvent>()

    // Max buffer size to prevent memory leaks
    private val maxBufferSize = 50

    /**
     * A CV detection event (pothole detected by the TFLite model).
     */
    data class CvDetection(
        val confidence: Float,    // Model confidence (0.0 - 1.0)
        val label: String,        // Detection class label
        val timestampMs: Long     // System.currentTimeMillis()
    )

    /**
     * Register a new CV detection and attempt fusion with recent sensor events.
     *
     * @param confidence TFLite model confidence for this detection
     * @param label Detection class label (e.g., "Pothole")
     * @return [FusionResult] with the combined score and recommended action
     */
    fun onCvDetection(confidence: Float, label: String = "pothole"): FusionResult {
        val now = System.currentTimeMillis()
        val cvDetection = CvDetection(confidence, label, now)

        // Add to buffer
        recentCvDetections.add(cvDetection)
        trimBuffer(recentCvDetections, maxBufferSize)

        // Find best matching sensor event within temporal window
        val matchingSensorEvent = findMatchingSensorEvent(now)

        return computeFusion(
            cvConfidence = confidence,
            sensorEvent = matchingSensorEvent,
            damageTypeHint = label,
            timestamp = now
        )
    }

    /**
     * Register a new sensor anomaly event and attempt fusion with recent CV detections.
     *
     * @param event The anomaly event from the AnomalyDetector
     * @return [FusionResult] with the combined score and recommended action
     */
    fun onSensorAnomaly(event: AnomalyEvent): FusionResult {
        val now = System.currentTimeMillis()

        // Add to buffer
        recentSensorEvents.add(event)
        trimBuffer(recentSensorEvents, maxBufferSize)

        // Find best matching CV detection within temporal window
        val matchingCv = findMatchingCvDetection(now)

        return computeFusion(
            cvConfidence = matchingCv?.confidence ?: 0f,
            sensorEvent = event,
            damageTypeHint = event.type.name.lowercase(),
            timestamp = now
        )
    }

    /**
     * Core fusion computation.
     */
    private fun computeFusion(
        cvConfidence: Float,
        sensorEvent: AnomalyEvent?,
        damageTypeHint: String,
        timestamp: Long
    ): FusionResult {
        val sensorConfidence = sensorEvent?.confidence ?: 0f

        // Calculate temporal correlation bonus
        val temporalBonus = if (cvConfidence > 0.1f && sensorConfidence > 0.1f) {
            // Both signals present — full temporal bonus
            1.0f
        } else {
            0f
        }

        // Weighted fusion score
        val fusedScore = min(1.0f, max(0f,
            cvWeight * cvConfidence +
            sensorWeight * sensorConfidence +
            temporalWeight * temporalBonus
        ))

        // Determine action
        val action = when {
            fusedScore >= autoThreshold -> FusionAction.AUTO_REPORT
            fusedScore >= promptThreshold -> FusionAction.PROMPT_USER
            else -> FusionAction.DISCARD
        }

        // Determine detection source
        val detectionSource = when {
            cvConfidence > 0.1f && sensorConfidence > 0.1f -> DetectionSource.DUAL_CONFIRMED.name
            cvConfidence > 0.1f -> DetectionSource.CV_ONLY.name
            sensorConfidence > 0.1f -> DetectionSource.SENSOR_ONLY.name
            else -> DetectionSource.MANUAL.name
        }

        // Best damage type (CV label takes priority, sensor type as fallback)
        val damageType = when {
            cvConfidence > 0.1f -> damageTypeHint
            sensorEvent != null -> sensorEvent.type.name.lowercase()
            else -> damageTypeHint
        }

        val result = FusionResult(
            fusedScore = fusedScore,
            action = action,
            cvConfidence = cvConfidence,
            sensorConfidence = sensorConfidence,
            temporalBonus = temporalBonus,
            damageType = damageType,
            detectionSource = detectionSource,
            anomalyEvent = sensorEvent,
            timestamp = timestamp
        )

        Log.d(TAG, "Fusion: cv=%.2f sensor=%.2f temporal=%.2f → score=%.3f → %s (%s)"
            .format(cvConfidence, sensorConfidence, temporalBonus, fusedScore, action, detectionSource))

        return result
    }

    /**
     * Find the best matching sensor event within the temporal window.
     */
    private fun findMatchingSensorEvent(timestampMs: Long): AnomalyEvent? {
        val windowStart = timestampMs - temporalWindowMs
        val windowEnd = timestampMs + temporalWindowMs

        return recentSensorEvents
            .filter { event ->
                // Convert nanoTime to approximate ms offset
                // Since sensor timestamps use System.nanoTime() and our timestampMs uses
                // System.currentTimeMillis(), we approximate with the most recent events
                val eventAgeMs = (System.nanoTime() - event.timestamp) / 1_000_000
                val eventApproxMs = System.currentTimeMillis() - eventAgeMs
                eventApproxMs in windowStart..windowEnd
            }
            .maxByOrNull { it.confidence }
    }

    /**
     * Find the best matching CV detection within the temporal window.
     */
    private fun findMatchingCvDetection(timestampMs: Long): CvDetection? {
        val windowStart = timestampMs - temporalWindowMs
        val windowEnd = timestampMs + temporalWindowMs

        return recentCvDetections
            .filter { it.timestampMs in windowStart..windowEnd }
            .maxByOrNull { it.confidence }
    }

    /**
     * Trim a buffer to prevent unbounded memory growth.
     */
    private fun <T> trimBuffer(buffer: MutableList<T>, maxSize: Int) {
        while (buffer.size > maxSize) {
            buffer.removeAt(0)
        }
    }

    /** Reset the fusion engine's event buffers. */
    fun reset() {
        recentCvDetections.clear()
        recentSensorEvents.clear()
        Log.d(TAG, "FusionEngine reset")
    }
}
