package com.example.roadguard.detection.context

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.tan

/**
 * Context provider that adjusts fusion weights based on ambient lighting.
 *
 * Rationale:
 * - Daylight (> 500 lux): camera produces clear images → CV fully trusted.
 * - Twilight (100-500 lux): CV partially degraded → slightly increase sensor weight.
 * - Night (< 100 lux): CV severely degraded → heavily shift toward sensors.
 *
 * Uses the Android TYPE_LIGHT sensor when available; falls back to a simple
 * sunrise/sunset calculation based on GPS coordinates and day of year.
 *
 * @param ambientLightLux Ambient light reading in lux (-1 if sensor unavailable).
 * @param isNightTime Whether current time falls between sunset and sunrise.
 *        Used as fallback when light sensor is not available.
 */
class LightContextProvider(
    private val ambientLightLux: Float,
    private val isNightTime: Boolean
) : ContextProvider {

    companion object {
        // Light level thresholds (lux)
        const val DAYLIGHT_THRESHOLD = 500f
        const val TWILIGHT_THRESHOLD = 100f

        // CV modifiers per light condition
        private const val CV_MOD_DAY = 1.0f       // Full daylight: CV trusted
        private const val CV_MOD_TWILIGHT = 0.8f   // Dusk/dawn: CV partially degraded
        private const val CV_MOD_NIGHT = 0.5f      // Night: CV severely degraded

        // Sensor modifiers per light condition
        private const val SENSOR_MOD_DAY = 1.0f    // Day: sensors neutral
        private const val SENSOR_MOD_TWILIGHT = 1.1f // Twilight: slight sensor boost
        private const val SENSOR_MOD_NIGHT = 1.4f   // Night: heavy sensor reliance

        /**
         * Estimate whether it is currently nighttime based on GPS coordinates
         * and day of year using a simplified solar position calculation.
         *
         * This is intentionally approximate — we only need to distinguish
         * "roughly day" from "roughly night" for weight modulation.
         *
         * @param latDegrees Latitude in degrees (positive = north)
         * @param dayOfYear Day of year (1-365)
         * @param hourOfDay Current hour in local time (0-23)
         * @param timezoneOffsetHours UTC offset in hours (e.g., +1 for CET, +2 for CEST)
         * @return true if estimated to be nighttime
         */
        fun estimateIsNight(
            latDegrees: Double,
            dayOfYear: Int,
            hourOfDay: Int,
            timezoneOffsetHours: Int
        ): Boolean {
            // Solar declination (simplified)
            val declination = -23.45 * cos(Math.toRadians(360.0 / 365 * (dayOfYear + 10)))
            val decRad = Math.toRadians(declination)
            val latRad = Math.toRadians(latDegrees)

            // Hour angle at sunrise/sunset
            val cosHourAngle = -tan(latRad) * tan(decRad)

            // Handle polar day/night
            if (cosHourAngle < -1.0) return false  // Polar day (midnight sun)
            if (cosHourAngle > 1.0) return true    // Polar night

            val hourAngle = Math.toDegrees(acos(cosHourAngle))

            // Solar noon is approximately at 12:00 in solar time
            val sunriseHour = 12.0 - hourAngle / 15.0
            val sunsetHour = 12.0 + hourAngle / 15.0

            // Adjust for timezone — solar calculations are in solar time
            val localSunrise = sunriseHour + timezoneOffsetHours - 12 + 12 // simplified
            val localSunset = sunsetHour + timezoneOffsetHours - 12 + 12

            return hourOfDay < localSunrise || hourOfDay > localSunset
        }
    }

    /**
     * CV modifier based on ambient light.
     *
     * Camera-based detection degrades significantly in low light
     * because object boundaries become indistinct and noise increases.
     */
    override fun getCvModifier(): Float {
        if (ambientLightLux >= 0f) {
            // Use actual sensor reading
            return when {
                ambientLightLux >= DAYLIGHT_THRESHOLD -> CV_MOD_DAY
                ambientLightLux >= TWILIGHT_THRESHOLD -> {
                    // Linear interpolation between twilight and day
                    val t = (ambientLightLux - TWILIGHT_THRESHOLD) /
                            (DAYLIGHT_THRESHOLD - TWILIGHT_THRESHOLD)
                    CV_MOD_TWILIGHT + t * (CV_MOD_DAY - CV_MOD_TWILIGHT)
                }
                else -> {
                    // Linear interpolation between night and twilight
                    val t = (ambientLightLux / TWILIGHT_THRESHOLD).coerceIn(0f, 1f)
                    CV_MOD_NIGHT + t * (CV_MOD_TWILIGHT - CV_MOD_NIGHT)
                }
            }
        }

        // Fallback: use time-based estimation
        return if (isNightTime) CV_MOD_NIGHT else CV_MOD_DAY
    }

    /**
     * Sensor modifier based on ambient light.
     *
     * IMU sensors are unaffected by lighting, but their relative importance
     * increases when camera is less reliable (at night or in low light).
     */
    override fun getSensorModifier(): Float {
        if (ambientLightLux >= 0f) {
            return when {
                ambientLightLux >= DAYLIGHT_THRESHOLD -> SENSOR_MOD_DAY
                ambientLightLux >= TWILIGHT_THRESHOLD -> {
                    val t = (ambientLightLux - TWILIGHT_THRESHOLD) /
                            (DAYLIGHT_THRESHOLD - TWILIGHT_THRESHOLD)
                    SENSOR_MOD_TWILIGHT + t * (SENSOR_MOD_DAY - SENSOR_MOD_TWILIGHT)
                }
                else -> {
                    val t = (ambientLightLux / TWILIGHT_THRESHOLD).coerceIn(0f, 1f)
                    SENSOR_MOD_NIGHT + t * (SENSOR_MOD_TWILIGHT - SENSOR_MOD_NIGHT)
                }
            }
        }

        return if (isNightTime) SENSOR_MOD_NIGHT else SENSOR_MOD_DAY
    }
}
