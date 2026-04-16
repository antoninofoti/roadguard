package com.example.roadguard.evaluation

import android.util.Log
import com.example.roadguard.detection.FusionContext
import com.example.roadguard.detection.FusionEngine
import com.example.roadguard.detection.FusionMode
import com.example.roadguard.detection.FusionResult
import com.example.roadguard.sensor.AnomalyEvent

/**
 * Coordinator for a single field evaluation session.
 *
 * Ties the [DetectionMode] selector to the [FusionEngine] configuration
 * and the [EvaluationLogger] for CSV recording.
 *
 * Usage (from the camera/sensor pipeline):
 * ```
 * val session = EvaluationSession(
 *     mode = DetectionMode.ADAPTIVE_FUSION,
 *     condition = "Day+Dry",
 *     route = "Route_A",
 *     fusionEngine = fusionEngine,
 *     outputDir = getExternalFilesDir(null)!!
 * )
 * session.start()
 *
 * // On each FusionResult from the pipeline:
 * session.record(fusionResult, lat, lng, speedKmh, lightLux, isNight)
 *
 * // When done with the route:
 * val csvFile = session.finish()
 * ```
 *
 * @param mode The detection modality for this session
 * @param condition Driving condition label (e.g., "Day+Dry", "Night+Wet")
 * @param route Route identifier (e.g., "Route_A_ViaAppia")
 * @param fusionEngine The active engine — this class configures it for [mode]
 * @param outputDir Directory where the CSV will be saved
 */
class EvaluationSession(
    val mode: DetectionMode,
    private val condition: String,
    private val route: String,
    private val fusionEngine: FusionEngine,
    private val outputDir: java.io.File
) {
    companion object {
        private const val TAG = "EvaluationSession"
    }

    private var logger: EvaluationLogger? = null
    private var isActive = false

    /**
     * Start the session: configure the FusionEngine for this mode and open the CSV.
     */
    fun start() {
        if (isActive) {
            Log.w(TAG, "Session already active")
            return
        }

        // Configure the engine for this detection mode
        fusionEngine.detectionMode = mode
        when (mode) {
            DetectionMode.ADAPTIVE_FUSION -> {
                fusionEngine.fusionMode = FusionMode.ADAPTIVE
            }
            else -> {
                fusionEngine.fusionMode = FusionMode.FIXED
            }
        }

        logger = EvaluationLogger(mode, condition, route, outputDir)
        isActive = true
        Log.i(TAG, "Started ${mode.displayName} session on '$route' ($condition)")
    }

    /**
     * Record the outcome of a FusionResult as an EvaluationEvent.
     *
     * Call this for every result that reaches the PROMPT_USER or AUTO_REPORT action,
     * or for every detection event depending on the required granularity.
     *
     * @param result FusionResult from the engine
     * @param lat Current GPS latitude (null if unavailable)
     * @param lng Current GPS longitude (null if unavailable)
     * @param context Current FusionContext (provides speed, light, night)
     */
    fun record(
        result: FusionResult,
        lat: Double?,
        lng: Double?,
        context: FusionContext
    ) {
        if (!isActive) {
            Log.w(TAG, "record() called on inactive session")
            return
        }
        val sessionLogger = logger ?: return

        val event = EvaluationEvent(
            sessionId = sessionLogger.sessionId,
            mode = mode,
            timestampMs = result.timestamp,
            lat = lat,
            lng = lng,
            speedKmh = context.speedKmh,
            cvConfidence = result.cvConfidence,
            sensorConfidence = result.sensorConfidence,
            fusedScore = result.fusedScore,
            effectiveAlpha = result.effectiveAlpha,
            effectiveBeta = result.effectiveBeta,
            effectiveGamma = result.effectiveGamma,
            damageType = result.damageType,
            action = result.action.name,
            ambientLightLux = context.ambientLightLux,
            isNight = context.isNightTime
        )

        sessionLogger.logEvent(event)
    }

    /**
     * Finish the session: flush the CSV, restore engine to default, return file.
     *
     * @return The written CSV file, or null on error
     */
    fun finish(): java.io.File? {
        if (!isActive) return null

        // Restore engine to default state
        fusionEngine.detectionMode = null
        fusionEngine.fusionMode = FusionMode.FIXED
        isActive = false

        val file = logger?.finishSession()
        Log.i(TAG, "Session finished: ${logger?.getSessionSummary()}")
        return file
    }

    /** True while the session is recording. */
    fun isActive(): Boolean = isActive

    /** Current session ID, available after [start]. */
    fun sessionId(): String = logger?.sessionId ?: ""
}
