package com.example.roadguard.detection.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SpeedContextProvider].
 *
 * Verifies that speed-based weight modulation behaves correctly
 * across all speed bands and boundary conditions.
 */
class SpeedContextProviderTest {

    // ── Low Speed Band: CV boost, sensor reduction ──

    @Test
    fun `stationary vehicle returns neutral modifiers`() {
        val provider = SpeedContextProvider(0f)
        assertEquals(1.0f, provider.getCvModifier(), 0.01f)
        assertEquals(1.0f, provider.getSensorModifier(), 0.01f)
    }

    @Test
    fun `very low speed boosts CV modifier`() {
        val provider = SpeedContextProvider(5f)
        assertTrue("CV modifier should be > 1.0 at low speed",
            provider.getCvModifier() > 1.0f)
    }

    @Test
    fun `very low speed reduces sensor modifier`() {
        val provider = SpeedContextProvider(5f)
        assertTrue("Sensor modifier should be < 1.0 at low speed",
            provider.getSensorModifier() < 1.0f)
    }

    @Test
    fun `near zero speed gives maximum CV boost`() {
        val provider = SpeedContextProvider(1f)
        // Almost at 0, should be close to CV_MOD_LOW_SPEED = 1.3
        assertTrue("CV modifier near 0 km/h should approach 1.3",
            provider.getCvModifier() > 1.25f)
    }

    // ── Normal Speed Band: neutral ──

    @Test
    fun `normal speed 50 kmh returns neutral modifiers`() {
        val provider = SpeedContextProvider(50f)
        assertEquals(1.0f, provider.getCvModifier(), 0.01f)
        assertEquals(1.0f, provider.getSensorModifier(), 0.01f)
    }

    @Test
    fun `threshold 20 kmh returns neutral CV modifier`() {
        val provider = SpeedContextProvider(20f)
        assertEquals(1.0f, provider.getCvModifier(), 0.01f)
    }

    @Test
    fun `threshold 80 kmh returns neutral CV modifier`() {
        val provider = SpeedContextProvider(80f)
        assertEquals(1.0f, provider.getCvModifier(), 0.01f)
    }

    // ── High Speed Band: sensor boost, CV reduction ──

    @Test
    fun `high speed reduces CV modifier`() {
        val provider = SpeedContextProvider(120f)
        assertTrue("CV modifier should be < 1.0 at high speed",
            provider.getCvModifier() < 1.0f)
    }

    @Test
    fun `high speed boosts sensor modifier`() {
        val provider = SpeedContextProvider(120f)
        assertTrue("Sensor modifier should be > 1.0 at high speed",
            provider.getSensorModifier() > 1.0f)
    }

    @Test
    fun `very high speed caps modifiers`() {
        // At 160 km/h (2x threshold), interpolation should be capped at 1.0
        val provider = SpeedContextProvider(160f)
        assertEquals(0.7f, provider.getCvModifier(), 0.01f)
        assertEquals(1.3f, provider.getSensorModifier(), 0.01f)
    }

    @Test
    fun `extreme speed does not exceed caps`() {
        val provider = SpeedContextProvider(300f)
        assertEquals(0.7f, provider.getCvModifier(), 0.01f)
        assertEquals(1.3f, provider.getSensorModifier(), 0.01f)
    }

    // ── Smooth Transitions ──

    @Test
    fun `modifiers change smoothly across low speed band`() {
        // Start from 1 km/h: speed=0 is a special "stationary/unknown" case
        // that returns neutral (1.0) rather than the low-speed boost.
        val speeds = listOf(1f, 5f, 10f, 15f, 20f)
        val cvMods = speeds.map { SpeedContextProvider(it).getCvModifier() }

        // CV modifier should decrease monotonically from ~1.3 to 1.0
        for (i in 1 until cvMods.size) {
            assertTrue("CV modifier should decrease as speed increases in low band " +
                "(speed=${speeds[i]}: ${cvMods[i]} should be <= ${cvMods[i-1]})",
                cvMods[i] <= cvMods[i - 1] + 0.001f) // small epsilon for float
        }
    }

    @Test
    fun `modifiers change smoothly across high speed band`() {
        val speeds = listOf(80f, 100f, 120f, 140f, 160f)
        val sensorMods = speeds.map { SpeedContextProvider(it).getSensorModifier() }

        // Sensor modifier should increase monotonically
        for (i in 1 until sensorMods.size) {
            assertTrue("Sensor modifier should increase as speed increases in high band",
                sensorMods[i] >= sensorMods[i - 1] - 0.001f)
        }
    }

    // ── Negative Speed (edge case) ──

    @Test
    fun `negative speed treated as stationary`() {
        val provider = SpeedContextProvider(-10f)
        assertEquals(1.0f, provider.getCvModifier(), 0.01f)
        assertEquals(1.0f, provider.getSensorModifier(), 0.01f)
    }

    // ── All modifiers are positive ──

    @Test
    fun `all modifiers are positive across all speeds`() {
        val speeds = listOf(0f, 5f, 10f, 20f, 50f, 80f, 100f, 150f, 200f)
        for (speed in speeds) {
            val provider = SpeedContextProvider(speed)
            assertTrue("CV modifier must be positive at $speed km/h",
                provider.getCvModifier() > 0f)
            assertTrue("Sensor modifier must be positive at $speed km/h",
                provider.getSensorModifier() > 0f)
        }
    }
}
