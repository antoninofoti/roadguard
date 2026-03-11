package com.example.roadguard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.os.IBinder
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.roadguard.services.SensorService
import com.example.roadguard.tflite.PotholeDetectionHelper

class PotholeDetectionActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var imageView: ImageView
    private lateinit var btnCamera: Button
    private lateinit var btnGallery: Button
    private lateinit var btnLiveDetection: Button
    private lateinit var switchLogging: Switch
    private lateinit var btnExportCsv: Button
    
    private lateinit var potholeDetectionHelper: PotholeDetectionHelper
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastAcceleration: Float = 0.0f
    private var lastShake: Long = 0

    private var sensorService: SensorService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: android.content.ComponentName, service: IBinder) {
            val binder = service as SensorService.LocalBinder
            sensorService = binder.getService()
            isBound = true
            
            // Sync switch with actual service state
            switchLogging.isChecked = sensorService?.isLoggingEnabled == true
            btnExportCsv.isEnabled = sensorService?.getLatestLogFile() != null && !switchLogging.isChecked
        }

        override fun onServiceDisconnected(arg0: android.content.ComponentName) {
            isBound = false
            sensorService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pothole_detection)

        imageView = findViewById(R.id.imageView)
        btnCamera = findViewById(R.id.btnCamera)
        btnGallery = findViewById(R.id.btnGallery)
        btnLiveDetection = findViewById(R.id.btnLiveDetection)
        switchLogging = findViewById(R.id.switchLogging)
        btnExportCsv = findViewById(R.id.btnExportCsv)
        
        potholeDetectionHelper = PotholeDetectionHelper(this)
        PotholeDetectionHelper.initOpenCV()

        switchLogging.setOnCheckedChangeListener { _, isChecked ->
            if (isBound) {
                sensorService?.toggleLogging(isChecked)
                btnExportCsv.isEnabled = !isChecked && sensorService?.getLatestLogFile() != null
                if (isChecked) {
                    Toast.makeText(this, "CSV Logging Started", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "CSV Logging Stopped", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnExportCsv.setOnClickListener {
            exportCsvFile()
        }

        btnCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
            } else {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                cameraLauncher.launch(intent)
            }
        }

        btnGallery.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(intent)
        }

        btnLiveDetection.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, com.example.roadguard.ui.LivePotholeDetectionFragment())
                .addToBackStack(null)
                .commit()
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        Intent(this, SensorService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        if (isBound) {
            switchLogging.isChecked = sensorService?.isLoggingEnabled == true
            btnExportCsv.isEnabled = sensorService?.getLatestLogFile() != null && !switchLogging.isChecked
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun exportCsvFile() {
        if (!isBound) return
        val logFile = sensorService?.getLatestLogFile()
        if (logFile != null && logFile.exists()) {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                logFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "RoadGuard Sensor Log")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Export CSV"))
        } else {
            Toast.makeText(this, "No CSV file available to export.", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val imageBitmap = result.data?.extras?.get("data") as? Bitmap
            imageBitmap?.let { processImage(it) }
        }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let {
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                bitmap?.let { processImage(it) }
            }
        }
    }

    private fun processImage(bitmap: Bitmap) {
        val detections = potholeDetectionHelper.detectPotholes(bitmap)
        val overlay = potholeDetectionHelper.drawDetections(bitmap, detections)
        imageView.setImageBitmap(overlay)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val acceleration = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = Math.abs(acceleration - lastAcceleration)
            lastAcceleration = acceleration
            if (delta > 10) {
                val now = System.currentTimeMillis()
                if (now - lastShake > 2000) {
                    lastShake = now
                    Toast.makeText(this, "Possible pothole detected by accelerometer!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
