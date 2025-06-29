package com.example.roadguard.view

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roadguard.repository.ReportRepository
import com.example.roadguard.tflite.Detection
import com.example.roadguard.utils.LocationHelper
import com.example.roadguard.utils.SensorHelper
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainViewModel : ViewModel() {
    val imageBitmap = mutableStateOf<Bitmap?>(null)
    val detections = mutableStateOf<List<Detection>>(emptyList())

    private val reportRepository = ReportRepository()
    private lateinit var sensorHelper: SensorHelper

    val isSavingReport = mutableStateOf(false)
    val saveReportError = mutableStateOf<String?>(null)
    val saveReportSuccess = mutableStateOf(false)

    val currentLocation = mutableStateOf<GeoPoint?>(null)
    val locationError = mutableStateOf<String?>(null)

    val currentSeverity: StateFlow<Float>?
        get() = if (this::sensorHelper.isInitialized) sensorHelper.severity else null

    fun initializeSensorHelper(context: Context) {
        if (!this::sensorHelper.isInitialized) {
            sensorHelper = SensorHelper(context)
        }
    }

    fun startSensorListening() {
        if (this::sensorHelper.isInitialized) {
            sensorHelper.startListening()
        }
    }

    fun stopSensorListening() {
        if (this::sensorHelper.isInitialized) {
            sensorHelper.stopListening()
        }
    }

    fun addReport(bitmap: Bitmap, context: Context) {
        viewModelScope.launch {
            isSavingReport.value = true
            saveReportError.value = null
            saveReportSuccess.value = false

            val locationHelper = LocationHelper(context)
            if (!locationHelper.hasLocationPermission()) {
                saveReportError.value = "Location permission not granted."
                isSavingReport.value = false
                return@launch
            }

            if (!this::sensorHelper.isInitialized) {
                saveReportError.value = "Sensor helper not initialized."
                isSavingReport.value = false
                return@launch
            }

            val severity = sensorHelper.getSeverity()

            locationHelper.getCurrentLocation()
                .onSuccess {
                    currentLocation.value = it
                    // Got location, now proceed to save report
                    saveReportWithLocation(bitmap, it, severity, context)
                }
                .onFailure {
                    saveReportError.value = it.message
                    isSavingReport.value = false
                }
        }
    }

    private fun saveReportWithLocation(bitmap: Bitmap, location: GeoPoint, severity: Float, context: Context) {
        viewModelScope.launch {
            // 1. Save bitmap to a temporary file to get a Uri
            val file = File(context.cacheDir, "${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            val imageUri = Uri.fromFile(file)

            // 2. Call repository to add report
            val result = reportRepository.addReport(imageUri, location, severity)

            result.onSuccess {
                saveReportSuccess.value = true
                file.delete() // Clean up
            }.onFailure {
                saveReportError.value = it.message
                file.delete() // Clean up
            }
            isSavingReport.value = false
        }
    }
}
