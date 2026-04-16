package com.example.roadguard.detection.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [LightContextProvider].
 *
 * Verifies that light-based weight modulation behaves correctly
 * across daylight, twilight, and night conditions, including
 * the time-based fallback when the light sensor is unavailable.
 */
class LightContextProviderTest {

    // ── Daylight: CV fully trusted ──

    @Test
    fun `bright daylight returns neutral CV modifier`() {
        val provider = LightContextProvider(ambientLightLux = 1000f, isNightTime = false)
        assertEquals(1.0f, provider.getCvModifier(), 0.01f)
    }

    @Test
    fun `bright daylight returns neutral sensor modifier`() {
        val provider = LightContextProvider(ambientLightLux = 1000f, isNightTime = false)
        assertEquals(1.0f, provider.getSensorModifier(), 0.01f)
    }

    @Test
    fun `exactly at daylight threshold returns neutral`() {
        val provider = LightContextProvider(ambientLightLux = 500f, isNightTime = false)
        assertEquals(1.0f, provider.getCvModifier(), 0.01f)
    }

    // ── Twilight: CV partially degraded ──

    @Test
    fun `twilight reduces CV modifier`() {
        val provider = LightContextProvider(ambientLightLux = 300f, isNightTime = false)
        val cvMod = provider.getCvModifier()
        assertTrue("CV modifier should be < 1.0 in twilight", cvMod < 1.0f)
        assertTrue("CV modifier should be > 0.5 in twilight", cvMod > 0.5f)
    }

    @Test
    fun `twilight increases sensor modifier`() {
        val provider = LightContextProvider(ambientLightLux = 300f, isNightTime = false)
        assertTrue("Sensor modifier should be > 1.0 in twilight",
            provider.getSensorModifier() > 1.0f)
    }

    @Test
    fun `exactly at twilight threshold`() {
        val provider = LightContextProvider(ambientLightLux = 100f, isNightTime = false)
        assertEquals(0.8f, provider.getCvModifier(), 0.01f)
        assertEquals(1.1f, provider.getSensorModifier(), 0.01f)
    }

    // ── Night: CV severely degraded ──

    @Test
    fun `darkness heavily reduces CV modifier`() {
        val provider = LightContextProvider(ambientLightLux = 10f, isNightTime = true)
        assertTrue("CV modifier should be close to 0.5 in darkness",
            provider.getCvModifier() < 0.6f)
    }

    @Test
    fun `darkness heavily boosts sensor modifier`() {
        val provider = LightContextProvider(ambientLightLux = 10f, isNightTime = true)
        assertTrue("Sensor modifier should be > 1.3 in darkness",
            provider.getSensorModifier() > 1.3f)
    }

    @Test
    fun `zero lux gives minimum CV modifier`() {
        val provider = LightContextProvider(ambientLightLux = 0f, isNightTime = true)
        assertEquals(0.5f, provider.getCvModifier(), 0.01f)
        assertEquals(1.4f, provider.getSensorModifier(), 0.01f)
    }

    // ── Fallback: time-based when sensor unavailable ──

    @Test
    fun `no sensor daytime returns neutral CV`() {
        val provider = LightContextProvider(ambientLightLux = -1f, isNightTime = false)
        assertEquals(1.0f, provider.getCvModifier(), 0.01f)
        assertEquals(1.0f, provider.getSensorModifier(), 0.01f)
    }

    @Test
    fun `no sensor nighttime returns night CV`() {
        val provider = LightContextProvider(ambientLightLux = -1f, isNightTime = true)
        assertEquals(0.5f, provider.getCvModifier(), 0.01f)
        assertEquals(1.4f, provider.getSensorModifier(), 0.01f)
    }

    // ── Smooth Transitions ──

    @Test
    fun `CV modifier decreases monotonically as light decreases`() {
        val luxValues = listOf(1000f, 500f, 300f, 100f, 50f, 10f, 0f)
        val cvMods = luxValues.map {
            LightContextProvider(ambientLightLux = it, isNightTime = false).getCvModifier()
        }
        for (i in 1 until cvMods.size) {
            assertTrue("CV modifier must decrease as light decreases (${luxValues[i]} lux)",
                cvMods[i] <= cvMods[i - 1] + 0.001f)
        }
    }

    @Test
    fun `sensor modifier increases monotonically as light decreases`() {
        val luxValues = listOf(1000f, 500f, 300f, 100f, 50f, 10f, 0f)
        val sensorMods = luxValues.map {
            LightContextProvider(ambientLightLux = it, isNightTime = false).getSensorModifier()
        }
        for (i in 1 until sensorMods.size) {
            assertTrue("Sensor modifier must increase as light decreases",
                sensorMods[i] >= sensorMods[i - 1] - 0.001f)
        }
    }

    // ── All modifiers positive ──

    @Test
    fun `all modifiers are positive across all light levels`() {
        val luxValues = listOf(0f, 10f, 50f, 100f, 300f, 500f, 1000f, 10000f)
        for (lux in luxValues) {
            val provider = LightContextProvider(ambientLightLux = lux, isNightTime = false)
            assertTrue("CV modifier must be positive at $lux lux",
                provider.getCvModifier() > 0f)
            assertTrue("Sensor modifier must be positive at $lux lux",
                provider.getSensorModifier() > 0f)
        }
    }

    // ── Sunrise/Sunset Estimation ──

    @Test
    fun `Rome midday in summer is not night`() {
        // Rome: 41.9°N, June 21st (day 172), 12:00 local, UTC+2 (CEST)
        val isNight = LightContextProvider.estimateIsNight(
            latDegrees = 41.9,
            dayOfYear = 172,
            hourOfDay = 12,
            timezoneOffsetHours = 2
        )
        assertEquals(false, isNight)
    }

    @Test
    fun `Rome midnight in summer is night`() {
        val isNight = LightContextProvider.estimateIsNight(
            latDegrees = 41.9,
            dayOfYear = 172,
            hourOfDay = 2,
            timezoneOffsetHours = 2
        )
        assertEquals(true, isNight)
    }

    @Test
    fun `Rome midday in winter is not night`() {
        // December 21st (day 355), 12:00 local, UTC+1 (CET)
        val isNight = LightContextProvider.estimateIsNight(
            latDegrees = 41.9,
            dayOfYear = 355,
            hourOfDay = 12,
            timezoneOffsetHours = 1
        )
        assertEquals(false, isNight)
    }
}
