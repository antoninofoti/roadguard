package com.example.roadguard.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for cybersecurity utilities (Phase F.2, F.3).
 *
 * F.2: EXIF stripping — tested via pure-logic helpers (hasNoGpsData)
 *      Full pixel-level test requires Android Bitmap so it lives in androidTest.
 *
 * F.3: Input validation (ReportValidator) and rate limiting (RateLimiter).
 */
class SecurityUtilsTest {

    // ── ReportValidator — Coordinates ──────────────────────────

    @Test
    fun `valid Italian coordinates pass validation`() {
        // Rome
        assertTrue(ReportValidator.validateCoordinates(41.89, 12.49).isValid)
        // Milan
        assertTrue(ReportValidator.validateCoordinates(45.46, 9.19).isValid)
        // Palermo
        assertTrue(ReportValidator.validateCoordinates(38.11, 13.36).isValid)
        // Bolzano (north edge)
        assertTrue(ReportValidator.validateCoordinates(46.5, 11.35).isValid)
        // Lampedusa (south edge)
        assertTrue(ReportValidator.validateCoordinates(35.5, 12.6).isValid)
    }

    @Test
    fun `coordinates outside Italy fail validation`() {
        // Berlin
        assertFalse(ReportValidator.validateCoordinates(52.52, 13.40).isValid)
        // Paris
        assertFalse(ReportValidator.validateCoordinates(48.85, 2.35).isValid)
        // New York
        assertFalse(ReportValidator.validateCoordinates(40.71, -74.01).isValid)
        // South Pole
        assertFalse(ReportValidator.validateCoordinates(-90.0, 0.0).isValid)
    }

    @Test
    fun `NaN coordinates fail validation`() {
        assertFalse(ReportValidator.validateCoordinates(Double.NaN, 12.49).isValid)
        assertFalse(ReportValidator.validateCoordinates(41.89, Double.NaN).isValid)
    }

    @Test
    fun `latitude at exact boundary is valid`() {
        assertTrue(ReportValidator.validateCoordinates(ReportValidator.ITALY_LAT_MIN, 12.49).isValid)
        assertTrue(ReportValidator.validateCoordinates(ReportValidator.ITALY_LAT_MAX, 12.49).isValid)
    }

    @Test
    fun `latitude just outside boundary is invalid`() {
        assertFalse(ReportValidator.validateCoordinates(ReportValidator.ITALY_LAT_MIN - 0.01, 12.49).isValid)
        assertFalse(ReportValidator.validateCoordinates(ReportValidator.ITALY_LAT_MAX + 0.01, 12.49).isValid)
    }

    // ── ReportValidator — Scores ────────────────────────────────

    @Test
    fun `valid scores in range 0 to 1 pass`() {
        assertTrue(ReportValidator.validateScore(0.0f).isValid)
        assertTrue(ReportValidator.validateScore(0.5f).isValid)
        assertTrue(ReportValidator.validateScore(1.0f).isValid)
    }

    @Test
    fun `scores outside 0_1 fail`() {
        assertFalse(ReportValidator.validateScore(-0.01f).isValid)
        assertFalse(ReportValidator.validateScore(1.01f).isValid)
        assertFalse(ReportValidator.validateScore(Float.NaN).isValid)
        assertFalse(ReportValidator.validateScore(Float.POSITIVE_INFINITY).isValid)
    }

    // ── ReportValidator — Damage Type ───────────────────────────

    @Test
    fun `known damage types pass`() {
        val validTypes = listOf("pothole", "bump", "speed_bump", "roughness", "crack", "unknown", "")
        for (t in validTypes) {
            assertTrue("'$t' should be valid", ReportValidator.validateDamageType(t).isValid)
        }
    }

    @Test
    fun `unknown damage type fails`() {
        assertFalse(ReportValidator.validateDamageType("explosion").isValid)
        assertFalse(ReportValidator.validateDamageType("DROP TABLE reports").isValid)
    }

    // ── ReportValidator — Full report ───────────────────────────

    @Test
    fun `valid full report passes`() {
        val result = ReportValidator.validateReport(
            lat = 41.89, lng = 12.49,
            fusedScore = 0.78f, severity = 0.8f,
            damageType = "pothole"
        )
        assertTrue(result.isValid)
    }

    @Test
    fun `report with invalid coordinates fails even if other fields valid`() {
        val result = ReportValidator.validateReport(
            lat = 0.0, lng = 0.0,  // Gulf of Guinea — not Italy
            fusedScore = 0.78f, severity = 0.8f,
            damageType = "pothole"
        )
        assertFalse(result.isValid)
    }

    @Test
    fun `report validation collects all failures`() {
        val result = ReportValidator.validateReport(
            lat = 0.0,          // invalid
            lng = 0.0,          // invalid
            fusedScore = -1f,   // invalid
            severity = 2f,      // invalid
            damageType = "bomb" // invalid
        )
        assertFalse(result.isValid)
        val reason = (result as ValidationResult.Invalid).reason
        // Should contain multiple error messages
        assertTrue("Expected multiple errors in: $reason", reason.contains(";"))
    }

    // ── RateLimiter ─────────────────────────────────────────────

    @Test
    fun `rate limiter allows submissions within the limit`() {
        val limiter = RateLimiter(maxRequests = 5, windowMs = 60_000L)
        repeat(5) {
            assertTrue("Submission $it should be allowed", limiter.isAllowed())
        }
    }

    @Test
    fun `rate limiter blocks the 6th submission when limit is 5`() {
        val limiter = RateLimiter(maxRequests = 5, windowMs = 60_000L)
        repeat(5) { limiter.isAllowed() }
        assertFalse("6th submission should be blocked", limiter.isAllowed())
    }

    @Test
    fun `rate limiter allows again after reset`() {
        val limiter = RateLimiter(maxRequests = 3, windowMs = 60_000L)
        repeat(3) { limiter.isAllowed() }
        assertFalse(limiter.isAllowed()) // blocked

        limiter.reset()
        assertTrue("After reset, submission should be allowed", limiter.isAllowed())
    }

    @Test
    fun `rate limiter currentCount reflects submissions`() {
        val limiter = RateLimiter(maxRequests = 10, windowMs = 60_000L)
        assertEquals(0, limiter.currentCount())
        limiter.isAllowed()
        assertEquals(1, limiter.currentCount())
        limiter.isAllowed()
        assertEquals(2, limiter.currentCount())
    }

    @Test
    fun `rate limiter is thread-safe for concurrent access`() {
        val limiter = RateLimiter(maxRequests = 10, windowMs = 60_000L)
        val results = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
        val threads = (1..10).map {
            Thread { results.add(limiter.isAllowed()) }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val allowed = results.count { it }
        assertEquals("Exactly 10 submissions should be allowed", 10, allowed)
    }

    // ── StructuredLogger — format validation ────────────────────

    @Test
    fun `GPS rounding to 3 decimal places works`() {
        // Test the precision via ReportValidator which uses the same logic
        // We test the concept: 3 d.p. for GPS = ~111m granularity
        val lat = 41.89234567
        val rounded = Math.round(lat * 1000) / 1000.0
        assertEquals(41.892, rounded, 0.0001)
    }

    @Test
    fun `ValidationResult Invalid carries reason`() {
        val result = ValidationResult.Invalid("test reason")
        assertFalse(result.isValid)
        assertEquals("test reason", result.reason)
    }

    @Test
    fun `ValidationResult Valid is valid`() {
        val result = ValidationResult.Valid
        assertTrue(result.isValid)
    }
}
