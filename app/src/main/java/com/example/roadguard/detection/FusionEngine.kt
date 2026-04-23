package com.example.roadguard.detection

import android.util.Log
import com.example.roadguard.detection.context.LightContextProvider
import com.example.roadguard.detection.context.SpeedContextProvider
import com.example.roadguard.evaluation.DetectionMode
import com.example.roadguard.model.DetectionSource
import com.example.roadguard.sensor.AnomalyEvent
import kotlin.math.max
import kotlin.math.min

/**
 * Late-fusion engine that combines CV detection and IMU sensor anomaly signals.
 *
 * Implements the fusion formula validated in the Python prototype:
 *
 *     Score = α × CV_confidence + β × Sensor_confidence + γ × Temporal_bonus
 *
 * Where (base weights):
 * - α = 0.55 (CV weight — higher because visual provides spatial detail)
 * - β = 0.30 (Sensor weight — confirms physical road impact)
 * - γ = 0.15 (Temporal bonus — applied when both signals within ±2s window)
 *
 * **Phase D — Adaptive Weights (Original Thesis Contribution)**:
 * When [fusionMode] is ADAPTIVE, weights are modulated by contextual signals:
 *
 *     α_eff = α_base × cv_modifier(speed, light)
 *     β_eff = β_base × sensor_modifier(speed, light)
 *     γ_eff = γ_base × (1 if both active else 0)
 *     Normalize: α_eff + β_eff + γ_eff = 1
 *
 * This ensures the system adapts its trust in each modality based on
 * real-time driving conditions, while maintaining the mathematical
 * invariant that weights always sum to 1.0.
 *
 * Decision thresholds:
 * - Score > 0.75 → AUTO_REPORT (dual-confirmed, no user intervention)
 * - Score > 0.50 → PROMPT_USER (ask user to confirm)
 * - Score < 0.50 → DISCARD (log only)
 *
 * @param cvWeight Base weight for CV confidence (α)
 * @param sensorWeight Base weight for sensor confidence (β)
 * @param temporalWeight Base weight for temporal correlation bonus (γ)
 * @param autoThreshold Score above which reports are created automatically
 * @param promptThreshold Score above which user is prompted for confirmation
 * @param temporalWindowMs Time window in ms for temporal correlation (±window)
 * @param fusionMode FIXED (original) or ADAPTIVE (context-aware, thesis contribution)
 * @param detectionMode Optional Phase E evaluation mode (overrides fusionMode when set)
 */
class FusionEngine(
    private val cvWeight: Float = 0.55f,
    private val sensorWeight: Float = 0.30f,
    private val temporalWeight: Float = 0.15f,
    private val autoThreshold: Float = 0.75f,
    private val promptThreshold: Float = 0.50f,
    private val temporalWindowMs: Long = 2000L,
    var fusionMode: FusionMode = FusionMode.FIXED,
    var detectionMode: DetectionMode? = null
) {
    companion object {
        private const val TAG = "FusionEngine"
    }

    // Recent events buffer for temporal correlation
    private val recentCvDetections = mutableListOf<CvDetection>()
    private val recentSensorEvents = mutableListOf<AnomalyEvent>()

    // Max buffer size to prevent memory leaks
    private val maxBufferSize = 50

    // Current environmental context (updated externally)
    private var currentContext: FusionContext = FusionContext.UNKNOWN

    /**
     * A CV detection event (pothole detected by the TFLite model).
     */
    data class CvDetection(
        val confidence: Float,    // Model confidence (0.0 - 1.0)
        val label: String,        // Detection class label
        val timestampMs: Long     // System.currentTimeMillis()
    )

    /**
     * Update the environmental context for adaptive fusion.
     *
     * Called by the SensorService whenever new context data is available
     * (GPS speed update, light sensor reading, etc.).
     *
     * In FIXED mode this data is stored but not used for weight computation.
     * In ADAPTIVE mode it modulates the fusion weights.
     *
     * @param context Latest environmental context snapshot
     */
    fun setFusionContext(context: FusionContext) {
        currentContext = context
    }

    /**
     * Get the current fusion context (for logging/debugging).
     */
    fun getFusionContext(): FusionContext = currentContext

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
     *
     * In FIXED mode, uses the base weights directly.
     * In ADAPTIVE mode, modulates weights via context providers
     * and normalizes so they always sum to 1.0.
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

        // Compute effective weights based on fusion mode
        val (effectiveAlpha, effectiveBeta, effectiveGamma) = computeEffectiveWeights()

        // Weighted fusion score
        val fusedScore = min(1.0f, max(0f,
            effectiveAlpha * cvConfidence +
            effectiveBeta * sensorConfidence +
            effectiveGamma * temporalBonus
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
            timestamp = timestamp,
            effectiveAlpha = effectiveAlpha,
            effectiveBeta = effectiveBeta,
            effectiveGamma = effectiveGamma,
            fusionMode = fusionMode.name
        )

        Log.d(TAG, "Fusion[%s]: cv=%.2f sensor=%.2f temporal=%.2f → score=%.3f → %s (%s) [α=%.3f β=%.3f γ=%.3f]"
            .format(fusionMode, cvConfidence, sensorConfidence, temporalBonus,
                    fusedScore, action, detectionSource,
                    effectiveAlpha, effectiveBeta, effectiveGamma))

        return result
    }

    /**
     * Compute the effective weights based on the current detection/fusion mode.
     *
     * Priority order:
     * 1. [detectionMode] set (Phase E evaluation) → use its fixed weights
     * 2. [fusionMode] == ADAPTIVE → context-aware adaptive weights
     * 3. [fusionMode] == FIXED (default) → base weights
     *
     * All paths guarantee α + β + γ = 1.0.
     *
     * @return Triple of (alpha, beta, gamma) that always sum to 1.0
     */
    internal fun computeEffectiveWeights(): Triple<Float, Float, Float> {
        // Phase E: DetectionMode overrides fusionMode
        detectionMode?.let { mode ->
            return when (mode) {
                DetectionMode.CV_ONLY     -> Triple(1.0f, 0.0f, 0.0f)
                DetectionMode.SENSOR_ONLY -> Triple(0.0f, 1.0f, 0.0f)
                DetectionMode.FIXED_FUSION ->
                    Triple(cvWeight, sensorWeight, temporalWeight)
                DetectionMode.ADAPTIVE_FUSION ->
                    computeAdaptiveWeights()
            }
        }

        // Standard FusionMode path (no DetectionMode override)
        return when (fusionMode) {
            FusionMode.FIXED    -> Triple(cvWeight, sensorWeight, temporalWeight)
            FusionMode.ADAPTIVE -> computeAdaptiveWeights()
        }
    }

    /**
     * Compute context-modulated adaptive weights and normalize to sum 1.0.
     * Used by both ADAPTIVE [FusionMode] and ADAPTIVE_FUSION [DetectionMode].
     */
    private fun computeAdaptiveWeights(): Triple<Float, Float, Float> {
        val speedProvider = SpeedContextProvider(currentContext.speedKmh)
        val lightProvider = LightContextProvider(
            currentContext.ambientLightLux,
            currentContext.isNightTime
        )

        val cvModifier     = speedProvider.getCvModifier()     * lightProvider.getCvModifier()
        val sensorModifier = speedProvider.getSensorModifier() * lightProvider.getSensorModifier()

        val rawAlpha = cvWeight     * cvModifier
        val rawBeta  = sensorWeight * sensorModifier
        val rawGamma = temporalWeight              // Temporal not context-modified

        val total = rawAlpha + rawBeta + rawGamma
        if (total <= 0f) {
            Log.w(TAG, "Weight normalization fallback: total=$total")
            return Triple(cvWeight, sensorWeight, temporalWeight)
        }
        return Triple(rawAlpha / total, rawBeta / total, rawGamma / total)
    }

    /**
     * Find the best matching sensor event within the temporal window.
     */
    private fun findMatchingSensorEvent(timestampMs: Long): AnomalyEvent? {
        val windowStart = timestampMs - temporalWindowMs
        val windowEnd = timestampMs + temporalWindowMs

        return recentSensorEvents
            .filter { event ->
                normalizeEventTimestampMs(event.timestamp) in windowStart..windowEnd
            }
            .maxByOrNull { it.confidence }
    }

    /**
     * Normalize sensor event timestamps to epoch milliseconds.
     *
     * Current pipeline emits sensor events in epoch ms. Older events may still
     * carry monotonic nanoseconds, so we retain a compatibility path.
     */
    private fun normalizeEventTimestampMs(rawTimestamp: Long): Long {
        val epochUpperBoundMs = 10_000_000_000_000L
        if (rawTimestamp < epochUpperBoundMs) {
            return rawTimestamp
        }

        val nowNano = System.nanoTime()
        if (rawTimestamp > nowNano) {
            return System.currentTimeMillis()
        }

        val eventAgeMs = (nowNano - rawTimestamp) / 1_000_000
        return System.currentTimeMillis() - eventAgeMs
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
        currentContext = FusionContext.UNKNOWN
        Log.d(TAG, "FusionEngine reset")
    }
}
