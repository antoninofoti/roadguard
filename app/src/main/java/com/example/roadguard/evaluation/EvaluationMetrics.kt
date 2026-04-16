package com.example.roadguard.evaluation

/**
 * Summary metrics for one detection mode during an evaluation session.
 *
 * Computed offline from a set of [EvaluationEvent]s matched against
 * [GroundTruthEntry] locations.
 *
 * @param mode The detection modality these metrics belong to
 * @param truePositives Detections within radius of a known damage location
 * @param falsePositives Detections with no nearby known damage
 * @param falseNegatives Known damage locations with no nearby detection
 * @param precision TP / (TP + FP)
 * @param recall    TP / (TP + FN)
 * @param f1        Harmonic mean of precision and recall
 * @param avgFusedScore Mean fusion score across all detections
 * @param avgLatencyMs Mean time between passing damage and detection
 * @param totalEvents Total events (TP + FP)
 * @param condition Driving condition label ("Day+Dry", "Night+Dry", etc.)
 */
data class EvaluationMetrics(
    val mode: DetectionMode,
    val truePositives: Int,
    val falsePositives: Int,
    val falseNegatives: Int,
    val precision: Float,
    val recall: Float,
    val f1: Float,
    val avgFusedScore: Float,
    val avgLatencyMs: Float,
    val totalEvents: Int,
    val condition: String
) {
    companion object {
        /** CSV header for metrics output. */
        const val CSV_HEADER =
            "Mode,Condition,TP,FP,FN,Precision,Recall,F1," +
            "AvgFusedScore,AvgLatencyMs,TotalEvents"
    }

    /** Serialize metrics to a single CSV line. */
    fun toCsvLine(): String =
        "${mode.name},$condition,$truePositives,$falsePositives,$falseNegatives," +
        "${"%.4f".format(precision)},${"%.4f".format(recall)},${"%.4f".format(f1)}," +
        "${"%.4f".format(avgFusedScore)},${"%.1f".format(avgLatencyMs)},$totalEvents"

    /** Human-readable summary. */
    fun toSummaryString(): String =
        "[${mode.displayName}|$condition] P=${"%5.1f".format(precision*100)}% " +
        "R=${"%5.1f".format(recall*100)}% F1=${"%5.1f".format(f1*100)}% " +
        "(TP=$truePositives FP=$falsePositives FN=$falseNegatives)"
}
