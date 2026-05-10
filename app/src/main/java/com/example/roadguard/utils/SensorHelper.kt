package com.example.roadguard.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class SensorHelper(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var maxAcceleration = 0f

    // High-pass filter to remove gravity
    private val gravity = FloatArray(3)
    private val linearAcceleration = FloatArray(3)
    private val alpha = 0.8f

    private val _severity = MutableStateFlow(0f)
    val severity = _severity.asStateFlow()

    fun startListening() {
        maxAcceleration = 0f // Reset on start
        _severity.value = 0f
        
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        } else {
            android.util.Log.w("SensorHelper", "Accelerometer not available on this device.")
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            // Isolate gravity
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

            // Remove gravity to get linear acceleration
            linearAcceleration[0] = event.values[0] - gravity[0]
            linearAcceleration[1] = event.values[1] - gravity[1]
            linearAcceleration[2] = event.values[2] - gravity[2]

            val currentAcceleration = sqrt(
                linearAcceleration[0] * linearAcceleration[0] +
                linearAcceleration[1] * linearAcceleration[1] +
                linearAcceleration[2] * linearAcceleration[2]
            )

            if (currentAcceleration > maxAcceleration) {
                maxAcceleration = currentAcceleration
            }
            _severity.value = calculateSeverity(currentAcceleration)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }

    /**
     * Gets the maximum severity recorded since listening started.
     * This is used when saving the report.
     */
    fun getSeverity(): Float {
        return calculateSeverity(maxAcceleration)
    }

    /**
     * Calculates severity on a scale of 1 to 5 based on the max acceleration recorded.
     * The thresholds are indicative and may need tuning.
     */
    private fun calculateSeverity(acceleration: Float): Float {
        return when {
            acceleration < 2f -> 1.0f // Very minor bump
            acceleration < 5f -> 2.0f // Minor bump
            acceleration < 10f -> 3.0f // Moderate bump
            acceleration < 20f -> 4.0f // Significant jolt
            else -> 5.0f // Severe impact
        }.toFloat()
    }
}
