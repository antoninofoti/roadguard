package com.example.roadguard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.example.roadguard.R
import com.example.roadguard.tflite.PotholeDetectionHelper
import com.example.roadguard.model.Report
import com.example.roadguard.repository.ReportRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors
import android.graphics.Matrix
import androidx.camera.view.PreviewView
import android.os.Bundle
import android.content.Context
import com.google.android.gms.location.LocationRequest
import android.widget.ProgressBar

class LivePotholeDetectionFragment : Fragment(), SensorEventListener {
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: PotholeOverlayView
    private lateinit var potholeDetectionHelper: PotholeDetectionHelper
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var lastAnalysisTime = 0L
    private val analysisIntervalMs = 350L // Analizza ogni 350ms (più fluido)

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastAcceleration: Float = 0.0f
    private var lastShake: Long = 0
    private val reportRepository = ReportRepository()
    private var lastReportTime = 0L
    private val reportIntervalMs = 5000L // almeno 5s tra un report e l'altro

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastKnownLocation: Location? = null

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    private val PERMISSION_REQUEST_CODE = 1001
    private lateinit var progressBar: ProgressBar
    private var permissionsGranted = false

    private var emptyDetectionFrames = 0
    private val emptyDetectionThreshold = 10 // 10 frame senza detection

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_live_pothole_detection, container, false)
        previewView = view.findViewById(R.id.previewView)
        overlayView = view.findViewById(R.id.overlayView)
        potholeDetectionHelper = PotholeDetectionHelper(requireContext())
        // ProgressBar centrata
        progressBar = ProgressBar(requireContext()).apply {
            isIndeterminate = true
            visibility = View.GONE
            val params = android.widget.FrameLayout.LayoutParams(
                120, 120
            )
            params.gravity = android.view.Gravity.CENTER
            layoutParams = params
            elevation = 20f
        }
        (view as android.widget.FrameLayout).addView(progressBar)

        // Bottone indietro coerente con il tema, senza MaterialButton
        val btnBack = Button(requireContext()).apply {
            text = "Indietro"
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            setTextColor(ContextCompat.getColor(context, R.color.purple_700))
            textSize = 18f
            setPadding(40, 10, 40, 10)
            background = ContextCompat.getDrawable(context, R.drawable.button_back_rounded)
            setOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            val params = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 64
            params.leftMargin = 32
            layoutParams = params
            bringToFront()
            elevation = 10f
        }
        (view as android.widget.FrameLayout).addView(btnBack)

        // Gestione back hardware/software
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                parentFragmentManager.popBackStack()
            }
        })
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("LivePotholeDetection", "onViewCreated called")
        super.onViewCreated(view, savedInstanceState)
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        // Unica richiesta permessi atomica
        if (!allPermissionsGranted()) {
            Log.d("LivePotholeDetection", "Permessi non ancora concessi, li richiedo")
            progressBar.visibility = View.VISIBLE
            requestPermissions(REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE)
        } else {
            Log.d("LivePotholeDetection", "Permessi già concessi, avvio camera e location")
            permissionsGranted = true
            progressBar.visibility = View.GONE
            startCameraAndLocation()
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                permissionsGranted = true
                progressBar.visibility = View.GONE
                startCameraAndLocation()
            } else {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Permessi necessari non concessi", Toast.LENGTH_LONG).show()
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
    }

    private fun startCameraAndLocation() {
        Log.d("LivePotholeDetection", "startCameraAndLocation() called")
        // Aggiorna posizione subito
        updateLastKnownLocation()
        // Avvia camera
        startCamera()
    }

    private fun updateLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    lastKnownLocation = location
                } else {
                    val locationRequest = LocationRequest.Builder(LocationRequest.PRIORITY_HIGH_ACCURACY, 1000).build()
                    fusedLocationClient.getCurrentLocation(
                        LocationRequest.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).addOnSuccessListener { loc ->
                        if (loc != null && loc is Location) lastKnownLocation = loc
                    }
                }
            }
        }
    }

    private fun startCamera() {
        Log.d("LivePotholeDetection", "startCamera() called")
        progressBar.visibility = View.VISIBLE
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            Log.d("LivePotholeDetection", "CameraProvider future listener triggered")
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, { image ->
                        processImageProxy(image)
                    })
                }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                Log.d("LivePotholeDetection", "Unbinding all cameras")
                cameraProvider.unbindAll()
                Log.d("LivePotholeDetection", "Binding to lifecycle")
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner, cameraSelector, preview, imageAnalyzer
                )
                Log.d("LivePotholeDetection", "Camera successfully bound to lifecycle")
                progressBar.visibility = View.GONE
            } catch (exc: Exception) {
                Log.e("LivePotholeDetection", "Camera error: ${exc.message}", exc)
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Camera error: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun processImageProxy(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < analysisIntervalMs) {
            image.close()
            return
        }
        lastAnalysisTime = now
        val bitmap = imageProxyToBitmapSafe(image)
        if (bitmap != null) {
            val detections = potholeDetectionHelper.detectPotholes(bitmap)
            Log.d("LivePotholeDetection", "Detection count: ${detections.size}")
            detections.forEachIndexed { idx, det ->
                Log.d("LivePotholeDetection", "Detection #$idx: $det")
            }
            requireActivity().runOnUiThread {
                overlayView.detections = detections
                overlayView.visibility = View.VISIBLE
                overlayView.invalidate() // Forza redraw
                if (detections.isNotEmpty()) {
                    emptyDetectionFrames = 0
                    overlayView.showNoDetectionPlaceholder(false)
                    tryCreateReport(bitmap, "pothole", 1.0f)
                } else {
                    emptyDetectionFrames++
                    if (emptyDetectionFrames >= emptyDetectionThreshold) {
                        overlayView.showNoDetectionPlaceholder(true)
                        Toast.makeText(requireContext(), "Nessuna buca rilevata", Toast.LENGTH_SHORT).show()
                        emptyDetectionFrames = 0
                    } else {
                        overlayView.showNoDetectionPlaceholder(false)
                    }
                }
            }
        } else {
            Log.e("LivePotholeDetection", "Bitmap conversion failed")
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Errore conversione immagine", Toast.LENGTH_SHORT).show()
            }
        }
        image.close()
    }

    // Conversione robusta da ImageProxy a Bitmap (YUV_420_888 -> JPEG -> Bitmap)
    private fun imageProxyToBitmapSafe(image: ImageProxy): Bitmap? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
            java.io.ByteArrayOutputStream().use { out ->
                yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
                val yuv = out.toByteArray()
                android.graphics.BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
            }
        } catch (e: Exception) {
            Log.e("LivePotholeDetection", "imageProxyToBitmapSafe error", e)
            null
        }
    }

    // Chiamata asincrona per creare un report
    private fun tryCreateReport(bitmap: Bitmap, type: String, severity: Float) {
        val now = System.currentTimeMillis()
        if (now - lastReportTime < reportIntervalMs) return
        lastReportTime = now
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = File(requireContext().cacheDir, "${UUID.randomUUID()}.jpg")
                FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    fos.flush()
                }
                val uri = Uri.fromFile(file)
                val location = getCurrentLocationGeoPoint()
                reportRepository.addReport(uri, location ?: GeoPoint(0.0, 0.0), severity)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(requireContext(), "Report inviato", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("LivePotholeDetection", "Errore invio report", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(requireContext(), "Errore invio report", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Recupera la posizione reale usando FusedLocationProviderClient
    private fun getCurrentLocationGeoPoint(): GeoPoint? {
        updateLastKnownLocation()
        lastKnownLocation?.let {
            return GeoPoint(it.latitude, it.longitude)
        }
        return null
    }

    // Accelerometro: crea report se scossa forte
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
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Scossa forte rilevata!", Toast.LENGTH_SHORT).show()
                    }
                    // Crea report accelerometro
                    tryCreateReport(Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888), "accelerometer", delta)
                }
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        sensorManager.unregisterListener(this)
    }
}
