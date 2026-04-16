package com.example.roadguard.utils

import android.util.Log

/**
 * Client-side input validation and anti-abuse controls for report submission.
 *
 * **Cybersecurity rationale (Thesis §F.3)**:
 * Even with Firestore Security Rules enforcing server-side validation,
 * client-side validation provides a first line of defence against:
 * - Accidental invalid data (GPS jitter, sensor noise)
 * - Sybil attacks (bot accounts generating fake reports)
 * - DoS via report flooding (rate limiting)
 *
 * This class is deliberately stateless and pure so it is unit-testable
 * without Android dependencies.
 */
object ReportValidator {

    private const val TAG = "ReportValidator"

    // ── Italy bounding box (generous, covers all Italian territory) ──
    /** Minimum valid latitude for Italy (including Lampedusa). */
    const val ITALY_LAT_MIN = 35.0
    /** Maximum valid latitude for Italy (including Alps/Bolzano). */
    const val ITALY_LAT_MAX = 47.5
    /** Minimum valid longitude for Italy (including Sardinia). */
    const val ITALY_LNG_MIN = 6.5
    /** Maximum valid longitude for Italy (including Trieste). */
    const val ITALY_LNG_MAX = 18.5

    // ── Score / severity bounds ──
    const val SCORE_MIN = 0f
    const val SCORE_MAX = 1f

    // ── Rate limiting ──
    /** Max reports a user may submit in one sliding window. */
    const val MAX_REPORTS_PER_WINDOW = 10
    /** Duration of the sliding window in milliseconds (5 minutes). */
    const val RATE_WINDOW_MS = 5 * 60 * 1_000L

    /**
     * Validate GPS coordinates for a report submitted in Italy.
     *
     * @param lat Latitude from GPS
     * @param lng Longitude from GPS
     * @return [ValidationResult.Valid] or [ValidationResult.Invalid] with reason
     */
    fun validateCoordinates(lat: Double, lng: Double): ValidationResult {
        if (lat.isNaN() || lng.isNaN()) {
            return ValidationResult.Invalid("Coordinates are NaN")
        }
        if (lat < ITALY_LAT_MIN || lat > ITALY_LAT_MAX) {
            return ValidationResult.Invalid(
                "Latitude $lat out of Italy bounds [$ITALY_LAT_MIN, $ITALY_LAT_MAX]"
            )
        }
        if (lng < ITALY_LNG_MIN || lng > ITALY_LNG_MAX) {
            return ValidationResult.Invalid(
                "Longitude $lng out of Italy bounds [$ITALY_LNG_MIN, $ITALY_LNG_MAX]"
            )
        }
        return ValidationResult.Valid
    }

    /**
     * Validate a fused score or severity value.
     *
     * @param score Score value to validate (must be in [0.0, 1.0])
     * @param fieldName Field name for error messages
     */
    fun validateScore(score: Float, fieldName: String = "score"): ValidationResult {
        if (score.isNaN() || score.isInfinite()) {
            return ValidationResult.Invalid("$fieldName is NaN or Infinite")
        }
        if (score < SCORE_MIN || score > SCORE_MAX) {
            return ValidationResult.Invalid(
                "$fieldName=$score out of bounds [$SCORE_MIN, $SCORE_MAX]"
            )
        }
        return ValidationResult.Valid
    }

    /**
     * Validate a damage type string.
     *
     * @param damageType The damage type label to validate
     */
    fun validateDamageType(damageType: String): ValidationResult {
        val valid = setOf("pothole", "bump", "speed_bump", "roughness", "crack", "unknown", "")
        if (damageType.trim().lowercase() !in valid) {
            return ValidationResult.Invalid("Unknown damage type: '$damageType'")
        }
        return ValidationResult.Valid
    }

    /**
     * Validate all fields of a report submission in one call.
     *
     * @return [ValidationResult.Valid] only if all fields pass individually
     */
    fun validateReport(
        lat: Double,
        lng: Double,
        fusedScore: Float,
        severity: Float,
        damageType: String
    ): ValidationResult {
        val checks = listOf(
            validateCoordinates(lat, lng),
            validateScore(fusedScore, "fusedScore"),
            validateScore(severity, "severity"),
            validateDamageType(damageType)
        )
        val failed = checks.filterIsInstance<ValidationResult.Invalid>()
        return if (failed.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(failed.joinToString("; ") { it.reason })
        }
    }
}

/** Result of a validation check. */
sealed class ValidationResult {
    /** The input passed all checks. */
    object Valid : ValidationResult()

    /**
     * The input failed validation.
     * @param reason Human-readable explanation for logging/debugging.
     *        NOT shown to users (no information leakage).
     */
    data class Invalid(val reason: String) : ValidationResult()

    val isValid: Boolean get() = this is Valid
}

/**
 * Client-side rate limiter for report submissions.
 *
 * Uses a sliding window counter: stores timestamps of the last N submissions
 * and rejects new ones if the window is full.
 *
 * This is a defense-in-depth measure — the server-side Firestore rules
 * would be the authoritative rate limit; this prevents accidental floods
 * from a malfunctioning sensor pipeline.
 *
 * @param maxRequests Maximum allowed submissions per window
 * @param windowMs Duration of the sliding window in milliseconds
 */
class RateLimiter(
    private val maxRequests: Int = ReportValidator.MAX_REPORTS_PER_WINDOW,
    private val windowMs: Long = ReportValidator.RATE_WINDOW_MS
) {
    private val submissionTimestamps = ArrayDeque<Long>()
    private val TAG = "RateLimiter"

    /**
     * Check if a new submission is allowed right now.
     *
     * Evicts timestamps older than [windowMs] from the head, then
     * checks if the remaining count is below [maxRequests].
     *
     * @return true if the submission is allowed
     */
    @Synchronized
    fun isAllowed(): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - windowMs

        // Evict expired timestamps
        while (submissionTimestamps.isNotEmpty() && submissionTimestamps.first() < windowStart) {
            submissionTimestamps.removeFirst()
        }

        return if (submissionTimestamps.size < maxRequests) {
            submissionTimestamps.addLast(now)
            true
        } else {
            Log.w(TAG, "Rate limit exceeded: ${submissionTimestamps.size}/$maxRequests " +
                    "submissions in last ${windowMs / 1000}s")
            false
        }
    }

    /** Number of submissions in the current window. */
    @Synchronized
    fun currentCount(): Int {
        val windowStart = System.currentTimeMillis() - windowMs
        return submissionTimestamps.count { it >= windowStart }
    }

    /** Reset all submission history (e.g., on logout). */
    @Synchronized
    fun reset() {
        submissionTimestamps.clear()
    }
}
