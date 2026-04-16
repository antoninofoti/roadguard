package com.example.roadguard.detection.context

/**
 * Context provider that adjusts fusion weights based on vehicle speed.
 *
 * Rationale (from thesis literature analysis):
 * - At low speed (< 20 km/h): camera has more time to analyze frames,
 *   CV is more reliable → increase CV weight.
 * - At moderate speed (20-80 km/h): both modalities balanced → neutral.
 * - At high speed (> 80 km/h): road impact on sensors is amplified,
 *   sensors become more informative → increase sensor weight, decrease CV.
 *
 * Modifiers are linearly interpolated within each speed band to avoid
 * abrupt weight changes at band boundaries (smooth transitions).
 *
 * @param speedKmh Current vehicle speed from GPS in km/h.
 */
class SpeedContextProvider(private val speedKmh: Float) : ContextProvider {

    companion object {
        // Speed band boundaries (km/h)
        const val LOW_SPEED_THRESHOLD = 20f
        const val HIGH_SPEED_THRESHOLD = 80f

        // CV modifiers per band
        private const val CV_MOD_LOW_SPEED = 1.3f     // Low speed: CV has more time
        private const val CV_MOD_NORMAL = 1.0f         // Normal: balanced
        private const val CV_MOD_HIGH_SPEED = 0.7f     // High speed: CV less reliable

        // Sensor modifiers per band
        private const val SENSOR_MOD_LOW_SPEED = 0.8f  // Low speed: less impact signal
        private const val SENSOR_MOD_NORMAL = 1.0f     // Normal: balanced
        private const val SENSOR_MOD_HIGH_SPEED = 1.3f // High speed: more impact signal
    }

    /**
     * CV modifier based on speed.
     *
     * Decreases at high speed because camera frames may blur
     * and the time window for detection per pothole is shorter.
     */
    override fun getCvModifier(): Float {
        return when {
            speedKmh <= 0f -> CV_MOD_NORMAL // Stationary or unknown
            speedKmh < LOW_SPEED_THRESHOLD -> {
                // Linear interpolation from NORMAL to LOW_SPEED as speed decreases
                val t = 1f - (speedKmh / LOW_SPEED_THRESHOLD)
                CV_MOD_NORMAL + t * (CV_MOD_LOW_SPEED - CV_MOD_NORMAL)
            }
            speedKmh <= HIGH_SPEED_THRESHOLD -> CV_MOD_NORMAL
            else -> {
                // Linear interpolation from NORMAL to HIGH_SPEED as speed increases
                // Cap the interpolation at 2x the threshold range
                val t = ((speedKmh - HIGH_SPEED_THRESHOLD) / HIGH_SPEED_THRESHOLD)
                    .coerceAtMost(1f)
                CV_MOD_NORMAL + t * (CV_MOD_HIGH_SPEED - CV_MOD_NORMAL)
            }
        }
    }

    /**
     * Sensor modifier based on speed.
     *
     * Increases at high speed because road impacts produce stronger
     * and more distinctive IMU signatures at higher velocities.
     */
    override fun getSensorModifier(): Float {
        return when {
            speedKmh <= 0f -> SENSOR_MOD_NORMAL // Stationary or unknown
            speedKmh < LOW_SPEED_THRESHOLD -> {
                val t = 1f - (speedKmh / LOW_SPEED_THRESHOLD)
                SENSOR_MOD_NORMAL + t * (SENSOR_MOD_LOW_SPEED - SENSOR_MOD_NORMAL)
            }
            speedKmh <= HIGH_SPEED_THRESHOLD -> SENSOR_MOD_NORMAL
            else -> {
                val t = ((speedKmh - HIGH_SPEED_THRESHOLD) / HIGH_SPEED_THRESHOLD)
                    .coerceAtMost(1f)
                SENSOR_MOD_NORMAL + t * (SENSOR_MOD_HIGH_SPEED - SENSOR_MOD_NORMAL)
            }
        }
    }
}
