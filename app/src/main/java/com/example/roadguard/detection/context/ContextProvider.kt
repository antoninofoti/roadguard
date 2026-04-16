package com.example.roadguard.detection.context

/**
 * Interface for context providers that modulate fusion weights.
 *
 * Each provider analyzes one aspect of the driving environment
 * (speed, lighting, weather) and produces multipliers that adjust
 * the base weights of the fusion formula.
 *
 * Multipliers > 1.0 increase the weight of a modality (higher trust).
 * Multipliers < 1.0 decrease the weight (lower trust).
 * Multipliers = 1.0 leave the weight unchanged (neutral).
 *
 * After all providers contribute their modifiers, the FusionEngine
 * normalizes the resulting weights so they always sum to 1.0.
 */
interface ContextProvider {

    /**
     * Modifier for the CV weight (alpha).
     *
     * Values > 1.0 = increase trust in computer vision.
     * Values < 1.0 = decrease trust in computer vision.
     *
     * @return Positive float multiplier, typically in range [0.3, 1.5].
     */
    fun getCvModifier(): Float

    /**
     * Modifier for the sensor weight (beta).
     *
     * Values > 1.0 = increase trust in IMU sensors.
     * Values < 1.0 = decrease trust in IMU sensors.
     *
     * @return Positive float multiplier, typically in range [0.7, 1.5].
     */
    fun getSensorModifier(): Float
}
