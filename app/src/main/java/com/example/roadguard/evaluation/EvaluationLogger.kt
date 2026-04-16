package com.example.roadguard.evaluation

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Logs detection events during a structured field evaluation session.
 *
 * Each session corresponds to one run of a test route in one detection mode.
 * Events are written to a CSV file for offline analysis in Python/Excel.
 *
 * Thesis usage (Phase E, §E.2 Data Collection):
 *   - Create one EvaluationLogger per session (mode + route + condition)
 *   - Call [logEvent] for every FusionResult that reaches PROMPT_USER or above
 *   - Call [finishSession] when the route ends
 *   - Import all session CSVs into the Python analysis notebook
 *
 * File naming: `eval_<mode>_<condition>_<sessionId>.csv`
 *
 * @param mode The detection modality active in this session
 * @param condition Human label for driving condition (e.g., "Day+Dry", "Night+Dry")
 * @param route Human label for the test route (e.g., "Route_A_ViaAppia")
 * @param outputDir Directory to write the CSV file (app's external files dir)
 */
class EvaluationLogger(
    val mode: DetectionMode,
    private val condition: String,
    private val route: String,
    private val outputDir: File
) {
    companion object {
        private const val TAG = "EvaluationLogger"
        private val DATE_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    }

    val sessionId: String = UUID.randomUUID().toString().take(8)
    val startTimeMs: Long = System.currentTimeMillis()
    private val startDate: String = DATE_FORMAT.format(Date(startTimeMs))

    private val fileName = "eval_${mode.name}_${condition.replace("+", "_")}_${route}_${startDate}.csv"
    private val csvFile: File = File(outputDir, fileName)
    private var csvWriter: FileWriter? = null
    private var eventCount = 0
    private var isFinished = false

    init {
        try {
            outputDir.mkdirs()
            csvWriter = FileWriter(csvFile, false)
            csvWriter?.appendLine(EvaluationEvent.CSV_HEADER)
            csvWriter?.flush()
            Log.i(TAG, "Session started: $sessionId | mode=${mode.name} | file=${csvFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize session logging", e)
        }
    }

    /**
     * Log a detection event.
     *
     * @param event The [EvaluationEvent] to record. Build it from a [FusionResult]
     *              using [EvaluationEvent] fields.
     */
    fun logEvent(event: EvaluationEvent) {
        if (isFinished) {
            Log.w(TAG, "Tried to log to a finished session ($sessionId)")
            return
        }
        try {
            csvWriter?.appendLine(event.toCsvLine())
            eventCount++
            if (eventCount % 10 == 0) {
                csvWriter?.flush() // Periodic flush to prevent data loss
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event", e)
        }
    }

    /**
     * Finish the session: flush, close the file, and log a summary.
     *
     * @return The CSV file for optional upload or sharing.
     */
    fun finishSession(): File? {
        if (isFinished) return csvFile
        isFinished = true
        return try {
            csvWriter?.flush()
            csvWriter?.close()
            csvWriter = null

            val durationMin = (System.currentTimeMillis() - startTimeMs) / 60_000.0
            Log.i(TAG, "Session $sessionId finished: $eventCount events | " +
                    "duration=%.1f min | file=${csvFile.absolutePath}".format(durationMin))
            csvFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finish session", e)
            null
        }
    }

    /**
     * Session metadata summary (for display in UI).
     */
    fun getSessionSummary(): String =
        "Session $sessionId | ${mode.displayName} | $condition | $route | $eventCount events"

    /** The CSV file being written (null if not yet created). */
    fun getOutputFile(): File = csvFile
}
