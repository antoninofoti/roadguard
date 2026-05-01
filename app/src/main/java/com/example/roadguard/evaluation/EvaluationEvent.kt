package com.example.roadguard.evaluation

import android.util.Log

/**
 * A single detection event logged during a field evaluation session.
 *
 * Captures everything needed to compute precision/recall/F1 for the
 * thesis evaluation chapter. Each event is written to a CSV line
 * by [EvaluationLogger].
 *
 * @param sessionId ID of the evaluation session
 * @param mode Detection modality active at the time of the event
 * @param timestampMs Wall-clock time of the detection (System.currentTimeMillis())
 * @param lat GPS latitude at detection (null if unavailable)
 * @param lng GPS longitude at detection (null if unavailable)
 * @param speedKmh Vehicle speed from GPS in km/h
 * @param cvConfidence CV model confidence (0.0 if CV not active)
 * @param sensorConfidence Sensor anomaly confidence (0.0 if sensor not active)
 * @param fusedScore Combined weighted score
 * @param effectiveAlpha Actual alpha used (for ADAPTIVE mode tracking)
 * @param effectiveBeta Actual beta used
 * @param effectiveGamma Actual gamma used
 * @param damageType Best-guess damage type ("pothole", "bump", etc.)
 * @param action Fusion action taken (AUTO_REPORT, PROMPT_USER, DISCARD)
 * @param ambientLightLux Ambient light reading at detection (-1 if unavailable)
 * @param isNight Whether it was night at time of detection
 * @param isGroundTruth Whether this event was manually confirmed as true positive
 *        (filled in post-session during annotation)
 */
data class EvaluationEvent(
    val sessionId: String,
    val mode: DetectionMode,
    val timestampMs: Long,
    val lat: Double?,
    val lng: Double?,
    val speedKmh: Float,
    val cvConfidence: Float,
    val sensorConfidence: Float,
    val fusedScore: Float,
    val effectiveAlpha: Float,
    val effectiveBeta: Float,
    val effectiveGamma: Float,
    val damageType: String,
    val action: String,
    val ambientLightLux: Float,
    val isNight: Boolean,
    val isGroundTruth: Boolean = false  // Annotated post-hoc
) {
    companion object {
        /** CSV header matching the field order. */
        const val CSV_HEADER =
            "SessionId,Mode,TimestampMs,Lat,Lng,SpeedKmh," +
            "CvConfidence,SensorConfidence,FusedScore," +
            "EffectiveAlpha,EffectiveBeta,EffectiveGamma," +
            "DamageType,Action,AmbientLightLux,IsNight,IsGroundTruth"

        /**
         * Parse an EvaluationEvent from a CSV line.
         * Returns null if the line is malformed.
         */
        fun fromCsvLine(line: String): EvaluationEvent? {
            val p = line.split(",").map { it.trim() }
            if (p.size < 17) return null
            return try {
                EvaluationEvent(
                    sessionId = p[0],
                    mode = DetectionMode.fromName(p[1]) ?: return null,
                    timestampMs = p[2].toLong(),
                    lat = p[3].toDoubleOrNull(),
                    lng = p[4].toDoubleOrNull(),
                    speedKmh = p[5].toFloat(),
                    cvConfidence = p[6].toFloat(),
                    sensorConfidence = p[7].toFloat(),
                    fusedScore = p[8].toFloat(),
                    effectiveAlpha = p[9].toFloat(),
                    effectiveBeta = p[10].toFloat(),
                    effectiveGamma = p[11].toFloat(),
                    damageType = p[12],
                    action = p[13],
                    ambientLightLux = p[14].toFloat(),
                    isNight = p[15].toBoolean(),
                    isGroundTruth = p.getOrNull(16)?.toBoolean() ?: false
                )
            } catch (e: Exception) {
                Log.w("EvaluationEvent", "Failed to parse CSV line: ${e.message}")
                null
            }
        }
    }

    /** Serialize this event to a CSV line (without newline). */
    fun toCsvLine(): String =
        "$sessionId,${mode.name},$timestampMs," +
        "${lat ?: ""},${lng ?: ""},$speedKmh," +
        "$cvConfidence,$sensorConfidence,$fusedScore," +
        "$effectiveAlpha,$effectiveBeta,$effectiveGamma," +
        "$damageType,$action,$ambientLightLux,$isNight,$isGroundTruth"
}
