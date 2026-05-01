package com.example.roadguard.evaluation

import com.example.roadguard.calibration.GroundTruthEntry
import kotlin.math.sqrt

/**
 * Computes precision, recall, F1 and other evaluation metrics for a set of
 * [EvaluationEvent]s matched against [GroundTruthEntry] locations.
 *
 * This is the offline analysis component used after field data collection
 * to produce the thesis evaluation tables (Phase E, §E.3 Statistical Analysis).
 *
 * Matching strategy:
 * - A detection is a True Positive if its GPS location is within [gt.radiusMeters]
 *   of any unmatched ground truth entry.
 * - Each ground truth entry can only be matched once (greedy first-match).
 * - Unmatched detections are False Positives.
 * - Unmatched ground truth entries are False Negatives.
 */
class MetricsCalculator {

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0
    }

    /**
     * Compute metrics for a single detection mode.
     *
     * @param events All detection events from sessions of this mode
     * @param groundTruth Known damage locations for this route
     * @param condition Driving condition label for the output
     * @param mode The detection mode to report metrics for
     * @return [EvaluationMetrics] for the thesis table
     */
    fun computeMetrics(
        events: List<EvaluationEvent>,
        groundTruth: List<GroundTruthEntry>,
        condition: String,
        mode: DetectionMode
    ): EvaluationMetrics {
        // Only consider events with GPS coordinates and that were reportable
        val detectedAnomalies = events.filter {
            it.lat != null && it.lng != null &&
            (it.action == "AUTO_REPORT" || it.action == "PROMPT_USER")
        }

        val matchedGt = mutableSetOf<Int>()
        val matchedDet = mutableSetOf<Int>()

        // Greedy matching: each detection matches at most one GT entry
        for ((detIdx, det) in detectedAnomalies.withIndex()) {
            val matchingGtIdx = groundTruth.indices
                .filter { it !in matchedGt }
                .firstOrNull { gtIdx ->
                    val gt = groundTruth[gtIdx]
                    val distance = haversineDistance(det.lat!!, det.lng!!, gt.lat, gt.lng)
                    distance <= gt.radiusMeters
                }
            
            if (matchingGtIdx != null) {
                matchedGt.add(matchingGtIdx)
                matchedDet.add(detIdx)
            }
        }

        val tp = matchedGt.size
        val fp = detectedAnomalies.size - matchedDet.size
        val fn = groundTruth.size - matchedGt.size

        val precision = if (tp + fp > 0) tp.toFloat() / (tp + fp) else 0f
        val recall = if (tp + fn > 0) tp.toFloat() / (tp + fn) else 0f
        val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0f

        val avgFused = if (detectedAnomalies.isNotEmpty())
            detectedAnomalies.map { it.fusedScore }.average().toFloat() else 0f

        return EvaluationMetrics(
            mode = mode,
            truePositives = tp,
            falsePositives = fp,
            falseNegatives = fn,
            precision = precision,
            recall = recall,
            f1 = f1,
            avgFusedScore = avgFused,
            avgLatencyMs = 0f,      // Computed separately if timestamps align with GT
            totalEvents = detectedAnomalies.size,
            condition = condition
        )
    }

    /**
     * Compute metrics for all four detection modes from a collection of sessions.
     *
     * @param eventsByMode Map from DetectionMode to list of events
     * @param groundTruth Known damage locations for this route/condition
     * @param condition Driving condition label
     * @return List of [EvaluationMetrics], one per mode, in evaluation order
     */
    fun computeAllModes(
        eventsByMode: Map<DetectionMode, List<EvaluationEvent>>,
        groundTruth: List<GroundTruthEntry>,
        condition: String
    ): List<EvaluationMetrics> {
        return DetectionMode.evaluationOrder().map { mode ->
            val events = eventsByMode[mode] ?: emptyList()
            computeMetrics(events, groundTruth, condition, mode)
        }
    }

    /**
     * Parse events from a session CSV file content.
     *
     * @param csvContent Full content of an evaluation CSV file
     * @return List of parsed events (malformed lines are skipped)
     */
    fun parseEventsFromCsv(csvContent: String): List<EvaluationEvent> {
        return csvContent.trim().lines()
            .drop(1) // Skip header
            .filter { it.isNotBlank() }
            .mapNotNull { EvaluationEvent.fromCsvLine(it) }
    }

    /**
     * Format all metrics as a markdown table for the thesis.
     *
     * Example output:
     * | Mode | Condition | Precision | Recall | F1 | TP | FP | FN |
     */
    fun toMarkdownTable(metricsList: List<EvaluationMetrics>): String {
        val sb = StringBuilder()
        sb.appendLine("| Mode | Condition | Precision | Recall | F1 | TP | FP | FN | Events |")
        sb.appendLine("|------|-----------|-----------|--------|----|----|----|----|--------|")
        for (m in metricsList) {
            sb.appendLine(
                "| ${m.mode.displayName} | ${m.condition} | " +
                "${"%.1f".format(m.precision * 100)}% | " +
                "${"%.1f".format(m.recall * 100)}% | " +
                "${"%.1f".format(m.f1 * 100)}% | " +
                "${m.truePositives} | ${m.falsePositives} | ${m.falseNegatives} | ${m.totalEvents} |"
            )
        }
        return sb.toString()
    }

    // ── Internal helpers ─────────────────────────────────────────

    internal fun haversineDistance(
        lat1: Double, lng1: Double, lat2: Double, lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * Math.asin(sqrt(a))
    }
}
