package com.example.roadguard.detection

/**
 * Driving mode of the fusion engine.
 *
 * - FIXED: uses the original fixed weights (α=0.55, β=0.30, γ=0.15).
 *   This is the baseline for A/B comparison in the thesis.
 * - ADAPTIVE: modulates weights based on contextual signals (speed, light).
 *   This is the original contribution of this thesis.
 */
enum class FusionMode {
    FIXED,
    ADAPTIVE
}

/**
 * Environmental context snapshot used by the adaptive fusion engine
 * to modulate CV and sensor weights at runtime.
 *
 * All fields have sensible defaults so the engine operates correctly
 * even when context information is partially unavailable (graceful degradation).
 *
 * @param speedKmh Vehicle speed from GPS in km/h. 0 when stationary or unavailable.
 * @param ambientLightLux Ambient light from TYPE_LIGHT sensor in lux.
 *        -1 indicates sensor not available (will fall back to time-based estimation).
 * @param isNightTime True if current time is between sunset and sunrise.
 *        Calculated from GPS coordinates and date as fallback when light sensor is unavailable.
 * @param timestamp When this context snapshot was captured (System.currentTimeMillis()).
 */
data class FusionContext(
    val speedKmh: Float = 0f,
    val ambientLightLux: Float = -1f,
    val isNightTime: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Whether the ambient light sensor provided a valid reading.
     */
    val hasLightSensor: Boolean
        get() = ambientLightLux >= 0f

    companion object {
        /** Default context with no information — triggers FIXED fallback behavior. */
        val UNKNOWN = FusionContext()
    }
}
