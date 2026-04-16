package com.example.roadguard.calibration

/**
 * A ground truth entry for calibration and evaluation.
 *
 * Represents a known road damage location that has been verified
 * in person (surveyed and GPS-tagged). Used to calculate precision,
 * recall, and F1 score of the detection pipeline.
 *
 * @param lat Latitude of the damage location
 * @param lng Longitude of the damage location
 * @param type Type of damage: "pothole", "bump", "speed_bump", "roughness"
 * @param radiusMeters Spatial tolerance for matching a detection to this entry.
 *        Default 15m accounts for GPS inaccuracy and vehicle path variation.
 * @param description Optional human-readable description for the field log.
 */
data class GroundTruthEntry(
    val lat: Double,
    val lng: Double,
    val type: String,
    val radiusMeters: Double = 15.0,
    val description: String = ""
) {
    companion object {
        /**
         * Parse a list of ground truth entries from CSV content.
         *
         * Expected CSV format (header line required):
         * ```
         * lat,lng,type,radius_m,description
         * 41.8902,12.4922,pothole,15,Via Tiburtina near metro
         * ```
         *
         * @param csvContent The full CSV content as a string
         * @return List of parsed GroundTruthEntry objects
         */
        fun fromCsv(csvContent: String): List<GroundTruthEntry> {
            val lines = csvContent.trim().lines()
            if (lines.size < 2) return emptyList() // Need header + at least 1 data line

            return lines.drop(1) // Skip header
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split(",").map { it.trim() }
                    if (parts.size < 3) return@mapNotNull null
                    try {
                        GroundTruthEntry(
                            lat = parts[0].toDouble(),
                            lng = parts[1].toDouble(),
                            type = parts[2],
                            radiusMeters = parts.getOrNull(3)?.toDoubleOrNull() ?: 15.0,
                            description = parts.getOrNull(4) ?: ""
                        )
                    } catch (e: NumberFormatException) {
                        null
                    }
                }
        }
    }
}
