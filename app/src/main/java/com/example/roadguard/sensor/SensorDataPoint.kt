package com.example.roadguard.sensor

/**
 * A single timestamped sensor reading from accelerometer and gyroscope.
 *
 * Stores both raw and Kalman-filtered values for analysis.
 * Used by [AnomalyDetector] in the sliding window analysis.
 */
data class SensorDataPoint(
    val timestamp: Long,            // System.nanoTime() timestamp
    val accelX: Float,              // Raw accelerometer X (m/s²)
    val accelY: Float,              // Raw accelerometer Y (m/s²)
    val accelZ: Float,              // Raw accelerometer Z (m/s²)
    val gyroX: Float = 0f,          // Raw gyroscope X (rad/s)
    val gyroY: Float = 0f,          // Raw gyroscope Y (rad/s)
    val gyroZ: Float = 0f,          // Raw gyroscope Z (rad/s)
    val filteredAccelMagnitude: Float = 0f,  // Kalman-filtered accel magnitude
    val filteredGyroMagnitude: Float = 0f    // Kalman-filtered gyro magnitude
)
