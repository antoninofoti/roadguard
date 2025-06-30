package com.example.roadguard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.roadguard.tflite.PotholeDetectionHelper

class PotholeDetectionActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var imageView: ImageView
    private lateinit var btnCamera: Button
    private lateinit var btnGallery: Button
    private lateinit var btnLiveDetection: Button
    private lateinit var potholeDetectionHelper: PotholeDetectionHelper
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastAcceleration: Float = 0.0f
    private var lastShake: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pothole_detection)

        imageView = findViewById(R.id.imageView)
        btnCamera = findViewById(R.id.btnCamera)
        btnGallery = findViewById(R.id.btnGallery)
        btnLiveDetection = findViewById(R.id.btnLiveDetection)
        potholeDetectionHelper = PotholeDetectionHelper(this)
        PotholeDetectionHelper.initOpenCV()

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
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
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
