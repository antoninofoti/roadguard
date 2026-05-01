package com.example.roadguard.utils

import android.util.Log
import com.example.roadguard.detection.FusionAction
import com.example.roadguard.detection.FusionResult
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured JSON logger for RoadGuard detection pipeline events.
 *
 * **Observability rationale (Thesis §H.1)**:
 * Standard `android.util.Log` outputs unstructured text that cannot be
 * ingested by modern observability stacks (ELK, Grafana Loki, Datadog).
 *
 * This logger emits each event as a single JSON line, compatible with:
 * - Grafana Loki (via logcat scraping or Filebeat agent)
 * - ELK Stack (Filebeat → Logstash → Elasticsearch)
 * - Custom backend analytics pipelines
 *
 * Key design decisions:
 * - One JSON object per log call (log-structured, not line-structured)
 * - Every entry includes: timestamp, event_type, level, and context
 * - Detection events include all fusion metadata for offline analysis
 * - No PII in logs: userId is hashed, GPS coordinates rounded to 3 decimal places
 *
 * Usage:
 * ```kotlin
 * StructuredLogger.logFusionResult(
 *     cvScore = 0.85f, sensorScore = 0.72f, fusedScore = 0.78f,
 *     action = "AUTO_REPORT", lat = 41.89, lng = 12.49, speedKmh = 45f
 * )
 * ```
 *
 * Which emits to logcat under tag "RG_STRUCT":
 * ```json
 * {"ts":"2026-04-15T21:30:00Z","event":"FUSION_RESULT","level":"INFO",
 *  "cvScore":0.85,"sensorScore":0.72,"fusedScore":0.78,
 *  "action":"AUTO_REPORT","lat":41.89,"lng":12.49,"speed_kmh":45.0}
 * ```
 */
object StructuredLogger {

    private const val TAG = "RG_STRUCT"
    private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    // ── GPS precision for logs: 3 decimal places ≈ 111m resolution ──
    // Sufficient for street-level analysis without exposing exact location
    private const val GPS_PRECISION = 3

    // ── Event Type Constants ─────────────────────────────────────────

    const val EVENT_FUSION_RESULT  = "FUSION_RESULT"
    const val EVENT_ANOMALY        = "SENSOR_ANOMALY"
    const val EVENT_CV_DETECTION   = "CV_DETECTION"
    const val EVENT_SESSION_START  = "EVAL_SESSION_START"
    const val EVENT_SESSION_END    = "EVAL_SESSION_END"
    const val EVENT_RATE_LIMITED   = "RATE_LIMIT_EXCEEDED"
    const val EVENT_VALIDATION_ERR = "VALIDATION_ERROR"
    const val EVENT_UPLOAD         = "FIREBASE_UPLOAD"
    const val EVENT_CONTEXT_UPDATE = "FUSION_CONTEXT_UPDATE"

    // ── Core Logging Methods ─────────────────────────────────────────

    /**
     * Log a FusionEngine result with all relevant metadata.
     *
     * This is the most important event for offline analysis — it captures
     * the full state of the system at each detection decision point.
     */
    /**
     * Log a FusionEngine result with all relevant metadata.
     */
    fun logFusionResult(
        result: FusionResult,
        lat: Double? = null,
        lng: Double? = null,
        speedKmh: Float = 0f
    ) {
        buildEntry(EVENT_FUSION_RESULT) {
            put("cvScore", result.cvConfidence)
            put("sensorScore", result.sensorConfidence)
            put("fusedScore", result.fusedScore)
            put("action", result.action.name)
            put("detectionSource", result.detectionSource)
            put("fusionMode", result.fusionMode)
            put("alpha", result.effectiveAlpha)
            put("beta", result.effectiveBeta)
            put("gamma", result.effectiveGamma)
            put("damageType", result.damageType)
            lat?.let { put("lat", roundGps(it)) }
            lng?.let { put("lng", roundGps(it)) }
            put("speed_kmh", speedKmh)
        }.emit("INFO")
    }

    /**
     * Log a sensor anomaly event from the AnomalyDetector.
     */
    fun logSensorAnomaly(
        anomalyType: String,
        severity: Float,
        confidence: Float,
        accelPeak: Float,
        gyroPeak: Float,
        lat: Double? = null,
        lng: Double? = null
    ) {
        buildEntry(EVENT_ANOMALY) {
            put("anomalyType", anomalyType)
            put("severity", severity)
            put("confidence", confidence)
            put("accelPeak", accelPeak)
            put("gyroPeak", gyroPeak)
            lat?.let { put("lat", roundGps(it)) }
            lng?.let { put("lng", roundGps(it)) }
        }.emit("INFO")
    }

    /**
     * Log an evaluation session lifecycle event.
     */
    fun logEvalSession(
        eventType: String,
        sessionId: String,
        mode: String,
        condition: String,
        route: String,
        eventCount: Int = 0
    ) {
        buildEntry(eventType) {
            put("sessionId", sessionId)
            put("detectionMode", mode)
            put("condition", condition)
            put("route", route)
            put("eventCount", eventCount)
        }.emit("INFO")
    }

    /**
     * Log a rate limit event (security monitoring).
     */
    fun logRateLimitExceeded(currentCount: Int, maxAllowed: Int) {
        buildEntry(EVENT_RATE_LIMITED) {
            put("currentCount", currentCount)
            put("maxAllowed", maxAllowed)
        }.emit("WARN")
    }

    /**
     * Log a validation error (security monitoring).
     */
    fun logValidationError(reason: String, field: String = "") {
        buildEntry(EVENT_VALIDATION_ERR) {
            put("reason", reason)
            if (field.isNotEmpty()) put("field", field)
        }.emit("WARN")
    }

    /**
     * Log a Firebase upload attempt and result.
     */
    fun logUpload(
        uploadType: String,
        success: Boolean,
        durationMs: Long,
        sizeBytes: Long = -1
    ) {
        buildEntry(EVENT_UPLOAD) {
            put("uploadType", uploadType)
            put("success", success)
            put("durationMs", durationMs)
            if (sizeBytes >= 0) put("sizeBytes", sizeBytes)
        }.emit(if (success) "INFO" else "ERROR")
    }

    /**
     * Log a FusionContext update (for monitoring context-aware behavior).
     */
    fun logContextUpdate(
        speedKmh: Float,
        ambientLightLux: Float,
        isNight: Boolean,
        derivedCvMod: Float = 1f,
        derivedSensorMod: Float = 1f
    ) {
        buildEntry(EVENT_CONTEXT_UPDATE) {
            put("speed_kmh", speedKmh)
            put("light_lux", ambientLightLux)
            put("isNight", isNight)
            put("cvModifier", derivedCvMod)
            put("sensorModifier", derivedSensorMod)
        }.emit("DEBUG")
    }

    // ── Internal helpers ─────────────────────────────────────────────

    private fun buildEntry(eventType: String, block: JSONObject.() -> Unit): JSONObject {
        return try {
            JSONObject().apply {
                put("ts", ISO_FORMAT.format(Date()))
                put("event", eventType)
                block()
            }
        } catch (e: Exception) {
            Log.w("StructuredLogger", "Validation error logging failed: ${e.message}")
            JSONObject()
        }
    }

    private fun JSONObject.emit(level: String) {
        if (length() == 0) return
        put("level", level)
        val line = toString()
        when (level) {
            "DEBUG" -> Log.d(TAG, line)
            "WARN"  -> Log.w(TAG, line)
            "ERROR" -> Log.e(TAG, line)
            else    -> Log.i(TAG, line)
        }
    }

    /**
     * Round a GPS coordinate to [GPS_PRECISION] decimal places.
     * 3 d.p. = ~111m precision — ok for street-level, not exact.
     */
    private fun roundGps(value: Double): Double {
        val factor = Math.pow(10.0, GPS_PRECISION.toDouble())
        return Math.round(value * factor) / factor
    }
}
