package com.example.roadguard.services

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.roadguard.repository.ReportRepository
import com.example.roadguard.sensor.AnomalyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles CSV logging and Firebase upload for sensor data.
 */
class SensorLogger(private val context: Context) {
    private val tag = "SensorLogger"
    private val reportRepository = ReportRepository()
    private var csvWriter: FileWriter? = null
    private var currentCsvFile: File? = null
    var isLoggingEnabled = false
        private set

    fun startLogging() {
        try {
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "SensorLog_$timeStamp.csv"
            currentCsvFile = File(context.getExternalFilesDir(null), fileName)
            csvWriter = FileWriter(currentCsvFile)
            
            val header = "Timestamp,Accel_Raw_X,Accel_Raw_Y,Accel_Raw_Z," +
                    "Accel_Filtered_Mag," +
                    "Gyro_Raw_X,Gyro_Raw_Y,Gyro_Raw_Z," +
                    "Gyro_Filtered_Mag," +
                    "Lat,Lng,Speed_kmh," +
                    "AmbientLight_lux,IsNight," +
                    "Anomaly_Type,Anomaly_Confidence\n"
            csvWriter?.append(header)
            
            isLoggingEnabled = true
            Log.d(tag, "Logging started: ${currentCsvFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start logging", e)
            isLoggingEnabled = false
        }
    }

    fun stopLogging() {
        if (!isLoggingEnabled) return
        try {
            csvWriter?.flush()
            csvWriter?.close()
            csvWriter = null
            Log.d(tag, "Logging stopped: ${currentCsvFile?.absolutePath}")

            currentCsvFile?.let { file ->
                CoroutineScope(Dispatchers.IO).launch {
                    val result = reportRepository.uploadSensorLog(file)
                    result.onSuccess { Log.d(tag, "Log uploaded: $it") }
                    result.onFailure { Log.e(tag, "Log upload failed", it) }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to stop logging", e)
        } finally {
            isLoggingEnabled = false
        }
    }

    fun logToCsv(
        timestamp: Long,
        accelRaw: FloatArray,
        accelMagFiltered: Float,
        gyroRaw: FloatArray,
        gyroMagFiltered: Float,
        location: Location?,
        isNight: Boolean,
        latestLightLux: Float,
        event: AnomalyEvent?
    ) {
        if (!isLoggingEnabled) return
        try {
            val lat = location?.latitude?.toString() ?: ""
            val lng = location?.longitude?.toString() ?: ""
            val speedKmh = location?.speed?.let { (it * 3.6).toString() } ?: ""
            val lightLux = if (latestLightLux >= 0f) latestLightLux.toString() else ""
            val anomalyType = event?.type?.name ?: ""
            val anomalyConfidence = event?.confidence?.toString() ?: ""

            val line = "$timestamp,${accelRaw[0]},${accelRaw[1]},${accelRaw[2]}," +
                    "$accelMagFiltered," +
                    "${gyroRaw[0]},${gyroRaw[1]},${gyroRaw[2]}," +
                    "$gyroMagFiltered," +
                    "$lat,$lng,$speedKmh," +
                    "$lightLux,$isNight," +
                    "$anomalyType,$anomalyConfidence\n"
            
            csvWriter?.append(line)
        } catch (e: Exception) {
            Log.e(tag, "Error writing to CSV", e)
            stopLogging()
        }
    }

    fun getLatestLogFile(): File? = currentCsvFile
}
