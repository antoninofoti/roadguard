package com.example.roadguard.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.roadguard.sensor.AnomalyDetector
import com.example.roadguard.sensor.AnomalyEvent
import com.example.roadguard.sensor.KalmanFilter3D
import com.example.roadguard.sensor.SensorDataPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.GeoPoint
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * Enhanced sensor service with Kalman filtering and anomaly detection.
 *
 * Pipeline:
 * 1. Raw IMU readings (accelerometer + gyroscope)
 * 2. Kalman Filter 3D (noise reduction on each axis)
 * 3. Sliding Window Anomaly Detector (z-score analysis)
 * 4. AnomalyEvent emission via callback
 *
 * The service collects sensor data at SENSOR_DELAY_GAME rate (~50Hz)
 * and emits [AnomalyEvent]s when road damage patterns are detected.
 */
class SensorService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "SensorService"

        // Sensor sampling — SENSOR_DELAY_GAME = ~50Hz for good time resolution
        private const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_GAME

        // Sliding window for recent data points (for external consumers)
        private const val DATA_WINDOW_SIZE = 200  // ~4 seconds at 50Hz

        // Kalman filter parameters (calibrated from prototype)
        private const val ACCEL_Q = 0.01f   // Process noise for accelerometer
        private const val ACCEL_R = 0.5f    // Measurement noise for accelerometer
        private const val GYRO_Q = 0.005f   // Process noise for gyroscope
        private const val GYRO_R = 0.3f     // Measurement noise for gyroscope

        // Anomaly detector parameters
        private const val WINDOW_SIZE = 50          // ~1 second at 50Hz
        private const val ACCEL_THRESHOLD = 2.5f    // Z-score threshold
        private const val GYRO_THRESHOLD = 2.0f     // Z-score threshold
    }

    // System services
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null

    // Kalman filters
    private val accelKalman = KalmanFilter3D(q = ACCEL_Q, r = ACCEL_R)
    private val gyroKalman = KalmanFilter3D(q = GYRO_Q, r = GYRO_R)

    // Anomaly detector
    private val anomalyDetector = AnomalyDetector(
        windowSize = WINDOW_SIZE,
        accelStdThreshold = ACCEL_THRESHOLD,
        gyroStdThreshold = GYRO_THRESHOLD
    )

    // Data storage
    private val recentDataPoints = ArrayDeque<SensorDataPoint>(DATA_WINDOW_SIZE)

    // Latest raw readings (updated on each sensor event)
    private var latestAccel = floatArrayOf(0f, 0f, 0f)
    private var latestGyro = floatArrayOf(0f, 0f, 0f)

    // Legacy compatibility — max values for simple severity calculation
    private var maxAcceleration = 0f
    private var maxRotation = 0f

    // Logging
    var isLoggingEnabled = false
        private set
    private var csvWriter: FileWriter? = null
    private var currentCsvFile: File? = null

    // Anomaly event callback
    private var onAnomalyDetected: ((AnomalyEvent) -> Unit)? = null

    // Binding
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): SensorService = this@SensorService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Register at GAME rate for better temporal resolution
        accelerometer?.let {
            sensorManager.registerListener(this, it, SENSOR_DELAY)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SENSOR_DELAY)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.lastOrNull()?.let {
                    currentLocation = it
                }
            }
        }

        startLocationUpdates()
        Log.d(TAG, "SensorService started with Kalman filtering and anomaly detection")
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        stopLocationUpdates()
        stopLogging()
        Log.d(TAG, "SensorService stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                latestAccel = event.values.copyOf()
                processReadings(event.timestamp)

                // Legacy: track max acceleration
                val rawMag = sqrt(
                    event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2]
                )
                if (rawMag > maxAcceleration) {
                    maxAcceleration = rawMag
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                latestGyro = event.values.copyOf()

                // Legacy: track max rotation
                val rawMag = sqrt(
                    event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2]
                )
                if (rawMag > maxRotation) {
                    maxRotation = rawMag
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Core processing pipeline:
     * Raw IMU → Kalman Filter → Anomaly Detector → Event Emission
     */
    private fun processReadings(timestamp: Long) {
        // Step 1: Apply Kalman filter to reduce noise
        val filteredAccelMag = accelKalman.updateAndGetMagnitude(
            latestAccel[0], latestAccel[1], latestAccel[2]
        )
        val filteredGyroMag = gyroKalman.updateAndGetMagnitude(
            latestGyro[0], latestGyro[1], latestGyro[2]
        )

        // Step 2: Create data point and add to window
        val dataPoint = SensorDataPoint(
            timestamp = timestamp,
            accelX = latestAccel[0],
            accelY = latestAccel[1],
            accelZ = latestAccel[2],
            gyroX = latestGyro[0],
            gyroY = latestGyro[1],
            gyroZ = latestGyro[2],
            filteredAccelMagnitude = filteredAccelMag,
            filteredGyroMagnitude = filteredGyroMag
        )

        // Maintain sliding window
        if (recentDataPoints.size >= DATA_WINDOW_SIZE) {
            recentDataPoints.removeFirst()
        }
        recentDataPoints.addLast(dataPoint)

        // Step 3: Check for anomaly
        val event = anomalyDetector.addReading(
            accelMagnitude = filteredAccelMag,
            gyroMagnitude = filteredGyroMag,
            timestamp = timestamp
        )

        // Step 4: Emit event if detected
        if (event != null) {
            val geoPoint = currentLocation?.let {
                GeoPoint(it.latitude, it.longitude)
            }
            val locatedEvent = event.copy(location = geoPoint)

            Log.d(TAG, "Anomaly detected: ${locatedEvent.type}, " +
                    "severity=${locatedEvent.severity}, " +
                    "confidence=${locatedEvent.confidence}")

            onAnomalyDetected?.invoke(locatedEvent)
        }

        // Step 5: Log to CSV if enabled
        if (isLoggingEnabled) {
            logToCsv(
                timestamp = timestamp,
                accelRaw = latestAccel,
                accelFiltered = floatArrayOf(
                    latestAccel[0], latestAccel[1], latestAccel[2] // Placeholder, we don't store filtered axes
                ),
                accelMagFiltered = filteredAccelMag,
                gyroRaw = latestGyro,
                gyroFiltered = floatArrayOf(
                    latestGyro[0], latestGyro[1], latestGyro[2] // Placeholder, we don't store filtered axes
                ),
                gyroMagFiltered = filteredGyroMag,
                location = currentLocation,
                event = event
            )
        }
    }

    // ========== Public API ==========

    fun toggleLogging(enabled: Boolean) {
        if (enabled == isLoggingEnabled) return
        if (enabled) {
            startLogging()
        } else {
            stopLogging()
        }
    }

    private fun startLogging() {
        try {
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "SensorLog_$timeStamp.csv"
            currentCsvFile = File(getExternalFilesDir(null), fileName)
            csvWriter = FileWriter(currentCsvFile)
            
            // Write CSV Header
            val header = "Timestamp,Accel_Raw_X,Accel_Raw_Y,Accel_Raw_Z," +
                    "Accel_Filtered_Mag," + // We only track magnitude of filtered accel/gyro in SensorDataPoint right now implicitly via Kalman output
                    "Gyro_Raw_X,Gyro_Raw_Y,Gyro_Raw_Z," +
                    "Gyro_Filtered_Mag," +
                    "Lat,Lng,Speed_kmh," +
                    "Anomaly_Type,Anomaly_Confidence\n"
            csvWriter?.append(header)
            
            isLoggingEnabled = true
            Log.d(TAG, "Logging started: ${currentCsvFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start logging", e)
            isLoggingEnabled = false
        }
    }

    private fun stopLogging() {
        if (!isLoggingEnabled) return
        try {
            csvWriter?.flush()
            csvWriter?.close()
            csvWriter = null
            Log.d(TAG, "Logging stopped: ${currentCsvFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop logging", e)
        } finally {
            isLoggingEnabled = false
        }
    }

    private fun logToCsv(
        timestamp: Long,
        accelRaw: FloatArray,
        accelFiltered: FloatArray,
        accelMagFiltered: Float,
        gyroRaw: FloatArray,
        gyroFiltered: FloatArray,
        gyroMagFiltered: Float,
        location: Location?,
        event: AnomalyEvent?
    ) {
        try {
            val lat = location?.latitude?.toString() ?: ""
            val lng = location?.longitude?.toString() ?: ""
            val speedKmh = location?.speed?.let { (it * 3.6).toString() } ?: ""
            val anomalyType = event?.type?.name ?: ""
            val anomalyConfidence = event?.confidence?.toString() ?: ""

            val line = "$timestamp,${accelRaw[0]},${accelRaw[1]},${accelRaw[2]}," +
                    "${accelMagFiltered}," +
                    "${gyroRaw[0]},${gyroRaw[1]},${gyroRaw[2]}," +
                    "${gyroMagFiltered}," +
                    "$lat,$lng,$speedKmh,$anomalyType,$anomalyConfidence\n"
            
            csvWriter?.append(line)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to CSV", e)
            // Auto stop on error
            stopLogging()
        }
    }

    fun getLatestLogFile(): File? {
        return currentCsvFile
    }

    /**
     * Set a callback to receive anomaly events.
     * Called on the sensor thread — use appropriate dispatching for UI updates.
     */
    fun setOnAnomalyDetectedListener(listener: ((AnomalyEvent) -> Unit)?) {
        onAnomalyDetected = listener
    }

    /**
     * Get the most recent filtered sensor data points.
     * Useful for displaying sensor graphs in the UI.
     */
    fun getRecentDataPoints(): List<SensorDataPoint> {
        return recentDataPoints.toList()
    }

    /**
     * Get the latest filtered accelerometer magnitude.
     */
    fun getFilteredAccelMagnitude(): Float {
        return recentDataPoints.peekLast()?.filteredAccelMagnitude ?: 0f
    }

    /**
     * Get the latest filtered gyroscope magnitude.
     */
    fun getFilteredGyroMagnitude(): Float {
        return recentDataPoints.peekLast()?.filteredGyroMagnitude ?: 0f
    }

    /**
     * Legacy severity calculation for backward compatibility.
     *
     * Combines max acceleration and rotation with weights.
     * Resets max values after reading.
     *
     * @return Simple severity score (not normalized 0-1, raw weighted sum)
     */
    fun getSeverity(): Float {
        val severity = (maxAcceleration * 0.7 + maxRotation * 0.3).toFloat()
        maxAcceleration = 0f
        maxRotation = 0f
        return severity
    }

    /** Get current GPS location. */
    fun getCurrentLocation(): Location? {
        return currentLocation
    }

    /** Reset the sensor fusion pipeline (filters + detector + data window). */
    fun resetPipeline() {
        accelKalman.reset()
        gyroKalman.reset()
        anomalyDetector.reset()
        recentDataPoints.clear()
        maxAcceleration = 0f
        maxRotation = 0f
        Log.d(TAG, "Sensor fusion pipeline reset")
    }

    // ========== Location ==========

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000)
            .setMaxUpdateDelayMillis(10000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission: $unlikely")
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
