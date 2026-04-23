package com.example.roadguard.ui

import android.Manifest
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.example.roadguard.R
import com.example.roadguard.detection.FusionAction
import com.example.roadguard.detection.FusionEngine
import com.example.roadguard.detection.FusionMode
import com.example.roadguard.detection.FusionResult
import com.example.roadguard.model.DetectionSource
import com.example.roadguard.repository.ReportRepository
import com.example.roadguard.sensor.AnomalyEvent
import com.example.roadguard.services.SensorService
import com.example.roadguard.tflite.PotholeDetectionHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Live pothole detection with FusionEngine integration.
 *
 * Pipeline:
 * 1. CameraX captures frames → PotholeDetectionHelper (YOLOv8 TFLite)
 * 2. SensorService provides anomaly events via callback
 * 3. FusionEngine combines CV + sensor signals
 * 4. Based on fused score: AUTO_REPORT, PROMPT_USER, or DISCARD
 */
class LivePotholeDetectionFragment : Fragment() {

    companion object {
        private const val TAG = "LivePotholeDetection"
        private const val ANALYSIS_INTERVAL_MS = 350L
        private const val REPORT_COOLDOWN_MS = 5000L
        private const val PROMPT_COOLDOWN_MS = 3000L
    }

    // Camera
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: PotholeOverlayView
    private lateinit var potholeDetectionHelper: PotholeDetectionHelper
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var lastAnalysisTime = 0L

    // Fusion Engine
    private val fusionEngine = FusionEngine(fusionMode = FusionMode.ADAPTIVE)
    private val reportRepository = ReportRepository()
    private var lastReportTime = 0L
    private var lastPromptTime = 0L
    private var activePromptDialog: AlertDialog? = null

    // Sensor service binding
    private var sensorService: SensorService? = null
    private var serviceBound = false

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastKnownLocation: Location? = null

    // UI
    private lateinit var progressBar: ProgressBar
    private lateinit var fusionStatusText: TextView
    private var permissionsGranted = false
    private var emptyDetectionFrames = 0
    private val emptyDetectionThreshold = 10

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // Last captured bitmap for report creation
    private var lastCapturedBitmap: Bitmap? = null

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.isNotEmpty() && permissions.values.all { it }) {
            permissionsGranted = true
            updateLastKnownLocation()
            startCamera()
        } else {
            progressBar.visibility = View.GONE
            Toast.makeText(requireContext(), "Permissions required", Toast.LENGTH_LONG).show()
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    // Service connection to SensorService
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SensorService.LocalBinder
            sensorService = binder.getService()
            serviceBound = true

            // Set up anomaly listener — feeds sensor events to FusionEngine
            sensorService?.setOnAnomalyDetectedListener { event ->
                handleSensorAnomaly(event)
            }

            syncFusionContextFromService()

            Log.d(TAG, "SensorService bound, anomaly listener registered (mode=${fusionEngine.fusionMode})")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sensorService = null
            serviceBound = false
            Log.d(TAG, "SensorService disconnected")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_live_pothole_detection, container, false)
        previewView = view.findViewById(R.id.previewView)
        overlayView = view.findViewById(R.id.overlayView)
        potholeDetectionHelper = PotholeDetectionHelper(requireContext())

        // Progress bar
        progressBar = ProgressBar(requireContext()).apply {
            isIndeterminate = true
            visibility = View.GONE
            val params = FrameLayout.LayoutParams(120, 120)
            params.gravity = Gravity.CENTER
            layoutParams = params
            elevation = 20f
        }
        (view as FrameLayout).addView(progressBar)

        // Back button
        val btnBack = Button(requireContext()).apply {
            text = "← Back"
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            setTextColor(ContextCompat.getColor(context, R.color.purple_700))
            textSize = 18f
            setPadding(40, 10, 40, 10)
            background = ContextCompat.getDrawable(context, R.drawable.button_back_rounded)
            setOnClickListener { parentFragmentManager.popBackStack() }
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = 64
            params.leftMargin = 32
            layoutParams = params
            elevation = 10f
        }
        (view as FrameLayout).addView(btnBack)

        // Fusion status indicator (bottom of screen)
        fusionStatusText = TextView(requireContext()).apply {
            text = "Fusion: Initializing..."
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 14f
            setBackgroundColor(0x80000000.toInt()) // Semi-transparent black
            setPadding(24, 12, 24, 12)
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.BOTTOM
            layoutParams = params
            elevation = 15f
        }
        (view as FrameLayout).addView(fusionStatusText)

        // REC button for logging
        val btnRec = Button(requireContext()).apply {
            text = "● REC"
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setBackgroundResource(R.drawable.button_rec_background) // We'll need to create this or use a color
            setOnClickListener {
                val isLogging = sensorService?.isLoggingEnabled ?: false
                sensorService?.toggleLogging(!isLogging)
                if (sensorService?.isLoggingEnabled == true) {
                    text = "■ STOP"
                    backgroundTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(context, android.R.color.holo_red_dark)
                    )
                    Toast.makeText(context, "Logging started", Toast.LENGTH_SHORT).show()
                } else {
                    text = "● REC"
                    backgroundTintList = android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.purple_500)
                    )
                    Toast.makeText(context, "Logging saved to storage", Toast.LENGTH_SHORT).show()
                }
            }
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            params.gravity = Gravity.TOP or Gravity.END
            params.topMargin = 64
            params.rightMargin = 32
            layoutParams = params
            elevation = 10f
        }
        (view as FrameLayout).addView(btnRec)

        // Handle hardware back button
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    parentFragmentManager.popBackStack()
                }
            }
        )

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Bind to SensorService (which handles Kalman + AnomalyDetector)
        val serviceIntent = Intent(requireContext(), SensorService::class.java)
        requireContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        if (!allPermissionsGranted()) {
            progressBar.visibility = View.VISIBLE
            requestPermissionLauncher.launch(requiredPermissions)
        } else {
            permissionsGranted = true
            progressBar.visibility = View.GONE
            startCameraAndLocation()
        }
    }

    // ========== Fusion Integration ==========

    /**
     * Called when the CV model detects a pothole in a camera frame.
     */
    private fun handleCvDetection(bitmap: Bitmap, confidence: Float) {
        lastCapturedBitmap = bitmap
        syncFusionContextFromService()

        val result = fusionEngine.onCvDetection(confidence, "pothole")
        processFusionResult(result, bitmap)
    }

    /**
     * Called when SensorService detects an anomaly event.
     * Runs on the sensor thread — dispatch UI updates to main thread.
     */
    private fun handleSensorAnomaly(event: AnomalyEvent) {
        syncFusionContextFromService()
        val result = fusionEngine.onSensorAnomaly(event)

        requireActivity().runOnUiThread {
            processFusionResult(result, lastCapturedBitmap)
        }
    }

    /**
     * Process a fusion result and take appropriate action.
     */
    private fun processFusionResult(result: FusionResult, bitmap: Bitmap?) {
        // Update status UI
        val statusEmoji = when (result.action) {
            FusionAction.AUTO_REPORT -> "🟢 AUTO"
            FusionAction.PROMPT_USER -> "🟡 PROMPT"
            FusionAction.DISCARD -> "⚪ —"
        }
        fusionStatusText.text = "Fusion: %.2f %s | CV:%.2f Sensor:%.2f | %s"
            .format(result.fusedScore, statusEmoji, result.cvConfidence,
                result.sensorConfidence, result.detectionSource)

        when (result.action) {
            FusionAction.AUTO_REPORT -> {
                Log.d(TAG, "AUTO_REPORT: fused=${result.fusedScore}")
                tryCreateFusionReport(bitmap, result)
            }
            FusionAction.PROMPT_USER -> {
                Log.d(TAG, "PROMPT_USER: fused=${result.fusedScore}")
                promptUserForReport(result, bitmap)
            }
            FusionAction.DISCARD -> {
                // Low confidence — do nothing
            }
        }
    }

    private fun promptUserForReport(result: FusionResult, bitmap: Bitmap?) {
        if (!isAdded) return
        if (activePromptDialog?.isShowing == true) return

        val now = System.currentTimeMillis()
        if (now - lastPromptTime < PROMPT_COOLDOWN_MS) {
            Log.d(TAG, "Prompt skipped: cooldown active")
            return
        }
        lastPromptTime = now

        val scorePct = (result.fusedScore * 100).toInt()
        activePromptDialog = AlertDialog.Builder(requireContext())
            .setTitle("Confirm road damage report")
            .setMessage(
                "Fusion score: $scorePct%\n" +
                    "Source: ${result.detectionSource}\n\n" +
                    "Send this report to operators?"
            )
            .setPositiveButton("Send report") { _, _ ->
                tryCreateFusionReport(bitmap, result)
            }
            .setNegativeButton("Dismiss") { _, _ ->
                Log.d(TAG, "PROMPT_USER dismissed by user")
            }
            .setOnDismissListener {
                activePromptDialog = null
            }
            .show()
    }

    /**
     * Create a report using the fusion result data.
     * Respects a 5-second cooldown between reports.
     */
    private fun tryCreateFusionReport(bitmap: Bitmap?, result: FusionResult) {
        val now = System.currentTimeMillis()
        if (now - lastReportTime < REPORT_COOLDOWN_MS) {
            Log.d(TAG, "Report skipped: cooldown active")
            return
        }

        val reportBitmap = bitmap ?: Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        val location = getCurrentLocationGeoPoint() ?: result.anomalyEvent?.location
        if (location == null) {
            Log.w(TAG, "Report skipped: no valid GPS location available")
            Toast.makeText(
                requireContext(),
                "Location unavailable: report not sent",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        lastReportTime = now

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Save bitmap to temp file
                val file = File(requireContext().cacheDir, "${UUID.randomUUID()}.jpg")
                FileOutputStream(file).use { fos ->
                    reportBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    fos.flush()
                }
                val uri = Uri.fromFile(file)

                // Use fusion-aware report creation
                val reportResult = reportRepository.addFusionReport(
                    imageUri = uri,
                    location = location,
                    severity = result.fusedScore,
                    cvConfidence = result.cvConfidence,
                    sensorConfidence = result.sensorConfidence,
                    fusedScore = result.fusedScore,
                    damageType = result.damageType,
                    detectionSource = result.detectionSource
                )

                CoroutineScope(Dispatchers.Main).launch {
                    reportResult.onSuccess { reportId ->
                        val actionLabel = if (result.action == FusionAction.AUTO_REPORT)
                            "Auto-report" else "Report"
                        Toast.makeText(
                            requireContext(),
                            "$actionLabel sent (${result.detectionSource}, score=%.0f%%)"
                                .format(result.fusedScore * 100),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    reportResult.onFailure { e ->
                        Toast.makeText(
                            requireContext(),
                            "Report error: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating fusion report", e)
            }
        }
    }

    private fun syncFusionContextFromService() {
        val context = sensorService?.getCurrentFusionContext() ?: return
        fusionEngine.setFusionContext(context)
    }

    // ========== Camera ==========

    private fun startCameraAndLocation() {
        updateLastKnownLocation()
        startCamera()
    }

    private fun startCamera() {
        Log.d(TAG, "startCamera()")
        progressBar.visibility = View.VISIBLE
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(640, 640), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { image -> processImageProxy(image) }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner, cameraSelector, preview, imageAnalyzer
                )
                Log.d(TAG, "Camera bound to lifecycle")
                progressBar.visibility = View.GONE
            } catch (exc: Exception) {
                Log.e(TAG, "Camera error: ${exc.message}", exc)
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Camera error: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun processImageProxy(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisTime < ANALYSIS_INTERVAL_MS) {
            image.close()
            return
        }
        lastAnalysisTime = now

        val bitmap = imageProxyToBitmapSafe(image)
        if (bitmap != null) {
            val detections = potholeDetectionHelper.detectPotholes(bitmap)
            Log.d(TAG, "CV detected ${detections.size} pothole(s)")

            requireActivity().runOnUiThread {
                overlayView.detections = detections
                overlayView.visibility = View.VISIBLE
                overlayView.invalidate()

                if (detections.isNotEmpty()) {
                    emptyDetectionFrames = 0
                    overlayView.showNoDetectionPlaceholder(false)

                    // Feed best detection to FusionEngine
                    val bestConfidence = detections.maxOf { it.confidence }
                    handleCvDetection(bitmap, bestConfidence)
                } else {
                    emptyDetectionFrames++
                    if (emptyDetectionFrames >= emptyDetectionThreshold) {
                        overlayView.showNoDetectionPlaceholder(true)
                        emptyDetectionFrames = 0
                    } else {
                        overlayView.showNoDetectionPlaceholder(false)
                    }
                }
            }
        } else {
            Log.e(TAG, "Bitmap conversion failed")
        }
        image.close()
    }

    // ========== Image Conversion ==========

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
            val yuvImage = android.graphics.YuvImage(
                nv21, android.graphics.ImageFormat.NV21,
                image.width, image.height, null
            )
            java.io.ByteArrayOutputStream().use { out ->
                yuvImage.compressToJpeg(
                    android.graphics.Rect(0, 0, image.width, image.height),
                    100, out
                )
                val yuv = out.toByteArray()
                android.graphics.BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "imageProxyToBitmapSafe error", e)
            null
        }
    }

    // ========== Location ==========

    private fun updateLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    lastKnownLocation = location
                } else {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).addOnSuccessListener { loc ->
                        if (loc != null) lastKnownLocation = loc
                    }
                }
            }
        }
    }

    private fun getCurrentLocationGeoPoint(): GeoPoint? {
        updateLastKnownLocation()
        return lastKnownLocation?.let { GeoPoint(it.latitude, it.longitude) }
    }

    // ========== Permissions ==========

    private fun allPermissionsGranted(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // ========== Lifecycle ==========

    override fun onDestroy() {
        super.onDestroy()
        activePromptDialog?.dismiss()
        activePromptDialog = null
        cameraExecutor.shutdown()
        if (serviceBound) {
            sensorService?.setOnAnomalyDetectedListener(null)
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
        fusionEngine.reset()
        Log.d(TAG, "Fragment destroyed, resources released")
    }
}
