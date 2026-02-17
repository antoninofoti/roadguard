package com.example.roadguard.sensor

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

/**
 * Unit tests for [AnomalyDetector].
 *
 * Validates that the detector:
 * 1. Detects genuine pothole-like spikes
 * 2. Ignores normal driving vibrations
 * 3. Classifies anomaly types correctly
 * 4. Respects cooldown between events
 * 5. Requires minimum data before detecting
 */
class AnomalyDetectorTest {

    private lateinit var detector: AnomalyDetector

    @Before
    fun setup() {
        detector = AnomalyDetector(
            windowSize = 50,
            accelStdThreshold = 2.5f,
            gyroStdThreshold = 2.0f,
            minSeverity = 0.1f
        )
    }

    @Test
    fun `no detection with insufficient data`() {
        // Only 10 readings (less than windowSize/2 = 25)
        repeat(10) { i ->
            val event = detector.addReading(
                accelMagnitude = 9.81f,
                gyroMagnitude = 0.1f,
                timestamp = (i * 20_000_000L)  // 20ms intervals
            )
            assertNull("Should not detect with only $i readings", event)
        }
    }

    @Test
    fun `no detection on normal driving`() {
        // Fill window with normal driving data (small random variation)
        val events = mutableListOf<AnomalyEvent>()

        repeat(100) { i ->
            val accel = 9.81f + (Math.random().toFloat() - 0.5f) * 0.3f
            val gyro = 0.05f + (Math.random().toFloat() - 0.5f) * 0.02f
            val event = detector.addReading(accel, gyro, i * 20_000_000L)
            if (event != null) events.add(event)
        }

        assertTrue(
            "Should have very few or no events on smooth road, got ${events.size}",
            events.size <= 2
        )
    }

    @Test
    fun `detects pothole spike`() {
        // Fill window with baseline
        repeat(60) { i ->
            detector.addReading(9.81f, 0.05f, i * 20_000_000L)
        }

        // Inject pothole: high accel + high gyro
        val event = detector.addReading(
            accelMagnitude = 20.0f,   // Way above normal ~9.81
            gyroMagnitude = 2.0f,     // Way above normal ~0.05
            timestamp = 60 * 20_000_000L
        )

        assertNotNull("Should detect pothole spike", event)
        event?.let {
            assertEquals("Should classify as POTHOLE", AnomalyType.POTHOLE, it.type)
            assertTrue("Severity should be significant", it.severity > 0.3f)
            assertTrue("Confidence should be high (both sensors)", it.confidence >= 0.9f)
        }
    }

    @Test
    fun `detects speed bump pattern`() {
        // Fill window with baseline
        repeat(60) { i ->
            detector.addReading(9.81f, 0.05f, i * 20_000_000L)
        }

        // Speed bump: high accel but low gyro
        val event = detector.addReading(
            accelMagnitude = 16.0f,
            gyroMagnitude = 0.06f,   // Still close to normal
            timestamp = 60 * 20_000_000L
        )

        assertNotNull("Should detect speed bump", event)
        event?.let {
            assertEquals("Should classify as SPEED_BUMP", AnomalyType.SPEED_BUMP, it.type)
            assertTrue("Confidence should be moderate (accel only)", it.confidence < 0.9f)
        }
    }

    @Test
    fun `respects cooldown between events`() {
        // Fill window with baseline
        repeat(60) { i ->
            detector.addReading(9.81f, 0.05f, i * 20_000_000L)
        }

        // First spike — should be detected
        val baseTime = 60 * 20_000_000L
        val event1 = detector.addReading(20.0f, 2.0f, baseTime)
        assertNotNull("First spike should be detected", event1)

        // Second spike 100ms later — should be in cooldown (500ms)
        val event2 = detector.addReading(20.0f, 2.0f, baseTime + 100_000_000L)
        assertNull("Second spike within 500ms cooldown should be suppressed", event2)

        // Third spike 600ms later — should be detected
        val event3 = detector.addReading(20.0f, 2.0f, baseTime + 600_000_000L)
        assertNotNull("Spike after cooldown should be detected", event3)
    }

    @Test
    fun `severity scales with z-score`() {
        // Fill window with baseline
        repeat(60) { i ->
            detector.addReading(9.81f, 0.05f, i * 20_000_000L)
        }

        // Moderate spike
        val moderateEvent = detector.addReading(14.0f, 1.0f, 60 * 20_000_000L)

        // Reset and refill, then bigger spike
        detector.reset()
        repeat(60) { i ->
            detector.addReading(9.81f, 0.05f, i * 20_000_000L)
        }
        val severeEvent = detector.addReading(25.0f, 3.0f, 60 * 20_000_000L)

        if (moderateEvent != null && severeEvent != null) {
            assertTrue(
                "Severe event severity (${severeEvent.severity}) should be > moderate (${moderateEvent.severity})",
                severeEvent.severity > moderateEvent.severity
            )
        }
    }

    @Test
    fun `reset clears state`() {
        // Fill with baseline
        repeat(60) { i ->
            detector.addReading(9.81f, 0.05f, i * 20_000_000L)
        }

        detector.reset()

        // After reset, detector should need new data before detecting
        val event = detector.addReading(20.0f, 2.0f, 0L)
        assertNull("Should not detect after reset with only 1 reading", event)
    }
}
