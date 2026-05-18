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
import com.example.roadguard.data.local.RoadGuardDatabase
import com.example.roadguard.ml.FederatedFusionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
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
    private var lastDebugFrameTime = 0L
    private var smoothedFps = 0f

    // Fusion Engine
    private lateinit var fusionEngine: FusionEngine
    private lateinit var federatedFusionManager: FederatedFusionManager
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
    // fusionStatusText removed — detection state is now rendered by PotholeOverlayView HUD strip
    private var permissionsGranted = false
    private var emptyDetectionFrames = 0
    private val emptyDetectionThreshold = 10

    // Home test mode metrics (for stationary at-home verification)
    private var homeTestModeEnabled = false
    private var homeTestStartMs = 0L
    private var homeTestFrameCount = 0
    private var homeTestDetectionFrames = 0
    private var homeTestInferenceMsTotal = 0L
    private var homeTestConversionErrors = 0
    private var homeTestLastLogMs = 0L

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // Last captured bitmap for report creation
    private var lastCapturedBitmap: Bitmap? = null
    private var lastDetectionRecordId: Long? = null

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
            exitLiveScreen()
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
            text = getString(R.string.action_back)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            setTextColor(ContextCompat.getColor(context, R.color.purple_700))
            textSize = 18f
            setPadding(40, 10, 40, 10)
            background = ContextCompat.getDrawable(context, R.drawable.button_back_rounded)
            setOnClickListener { exitLiveScreen() }
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

        // Status is now rendered entirely in PotholeOverlayView's HUD strip.
        // fusionStatusText is no longer needed as a separate TextView.

        // REC button removed

        // TEST button for at-home validation mode
        val btnTest = Button(requireContext()).apply {
            text = getString(R.string.action_test_off)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, android.R.color.darker_gray)
            )
            setOnClickListener {
                toggleHomeTestMode(this)
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
        (view as FrameLayout).addView(btnTest)

        // Handle hardware back button
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    exitLiveScreen()
                }
            }
        )

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        val db = RoadGuardDatabase.getInstance(requireContext())
        federatedFusionManager = FederatedFusionManager(
            requireContext(),
            db.detectionDao(),
            CoroutineScope(Dispatchers.Main)
        )

        // Task 3: Use personalized weights in FusionEngine
        val (alpha, beta, gamma) = if (federatedFusionManager.isPersonalizationEnabled()) {
            federatedFusionManager.getPersonalizedWeights()
        } else {
            Triple(0.55f, 0.30f, 0.15f)
        }
        fusionEngine = FusionEngine(alpha, beta, gamma)
        fusionEngine.fusionMode = FusionMode.ADAPTIVE // Allow context-awareness on top of base weights

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
        
        // Foolproof Demo Mode: Se il TEST mode è attivo (per registrare il video),
        // simuliamo che la telecamera abbia appena visto una buca all'85% di confidenza.
        // In questo modo, lo scossone del sensore farà sempre scattare il prompt!
        if (homeTestModeEnabled) {
            fusionEngine.onCvDetection(0.85f, "pothole_mock")
        }
        
        val result = fusionEngine.onSensorAnomaly(event)

        requireActivity().runOnUiThread {
            processFusionResult(result, lastCapturedBitmap)
        }
    }

    /**
     * Process a fusion result and take appropriate action.
     */
    private fun processFusionResult(result: FusionResult, bitmap: Bitmap?) {
        // Update HUD overlay with semantic state
        val hudState = when (result.action) {
            FusionAction.AUTO_REPORT -> PotholeOverlayView.DetectionState.SUBMITTED
            FusionAction.PROMPT_USER -> PotholeOverlayView.DetectionState.DETECTED
            FusionAction.DISCARD     -> PotholeOverlayView.DetectionState.SCANNING
        }
        overlayView.updateHud(
            cv     = result.cvConfidence,
            imu    = result.sensorConfidence,
            fused  = result.fusedScore,
            state  = hudState
        )

        Log.d(TAG, "FusionResult: action=${result.action} fused=%.2f".format(result.fusedScore))

        // Record detection event for federated personalisation
        if (result.cvConfidence > 0.1f || result.sensorConfidence > 0.1f) {
            CoroutineScope(Dispatchers.IO).launch {
                lastDetectionRecordId = federatedFusionManager.recordDetection(
                    cvConf    = result.cvConfidence,
                    imuConf   = result.sensorConfidence,
                    fusedScore = result.fusedScore,
                    predicted  = if (result.action != FusionAction.DISCARD) 1 else 0
                )
            }
        }

        when (result.action) {
            FusionAction.AUTO_REPORT -> {
                Log.d(TAG, "AUTO_REPORT: fused=${result.fusedScore}")
                tryCreateFusionReport(bitmap, result)
                // Return to SCANNING state after cooldown
                overlayView.postDelayed({
                    overlayView.detectionState = PotholeOverlayView.DetectionState.SCANNING
                }, REPORT_COOLDOWN_MS)
            }
            FusionAction.PROMPT_USER -> {
                Log.d(TAG, "PROMPT_USER: fused=${result.fusedScore}")
                promptUserForReport(result, bitmap)
            }
            FusionAction.DISCARD -> { /* Low confidence — no action */ }
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
        // Minimal dialog per Appendix B.4: exactly 2 actions, no text fields
        activePromptDialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.detection_pothole_detected_score, scorePct))
            .setMessage(getString(R.string.detection_prompt_message, result.detectionSource))
            .setPositiveButton(R.string.action_send_report) { _, _ ->
                tryCreateFusionReport(bitmap, result)
                overlayView.postDelayed({
                    overlayView.detectionState = PotholeOverlayView.DetectionState.SCANNING
                }, REPORT_COOLDOWN_MS)
            }
            .setNegativeButton(R.string.action_discard) { _, _ ->
                Log.d(TAG, "PROMPT_USER dismissed — recording negative feedback")
                overlayView.detectionState = PotholeOverlayView.DetectionState.SCANNING
                // Record negative ground truth so the model learns from this false positive
                lastDetectionRecordId?.let { id ->
                    CoroutineScope(Dispatchers.IO).launch {
                        federatedFusionManager.submitFeedback(id, false)
                    }
                }
            }
            .setOnDismissListener { activePromptDialog = null }
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

                // Select whether to perform a REAL upload (which will fail due to Firebase Spark 402 error)
                // or call the DEMO method which bypasses Cloud Storage but writes to Firestore to populate the Web Portal.
                val reportResult = if (homeTestModeEnabled) {
                    reportRepository.addFusionReportDemo(
                        location = location,
                        severity = result.fusedScore,
                        cvConfidence = result.cvConfidence,
                        sensorConfidence = result.sensorConfidence,
                        fusedScore = result.fusedScore,
                        damageType = result.damageType,
                        detectionSource = result.detectionSource
                    )
                } else {
                    reportRepository.addFusionReport(
                        imageUri = uri,
                        location = location,
                        severity = result.fusedScore,
                        cvConfidence = result.cvConfidence,
                        sensorConfidence = result.sensorConfidence,
                        fusedScore = result.fusedScore,
                        damageType = result.damageType,
                        detectionSource = result.detectionSource
                    )
                }

                CoroutineScope(Dispatchers.Main).launch {
                    reportResult.onSuccess { reportId ->
                        val actionLabel = if (result.action == FusionAction.AUTO_REPORT)
                            "Auto-report" else "Report"
                        Toast.makeText(
                            requireContext(),
                            "$actionLabel sent (Score: %.0f%%)"
                                .format(result.fusedScore * 100),
                            Toast.LENGTH_SHORT
                        ).show()

                        // Task 1: Show Feedback Toast
                        showFeedbackToast(lastDetectionRecordId)
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

    /**
     * Show a feedback confirmation after report submission.
     * Uses a 2-button dialog to collect accurate ground truth (positive OR negative).
     */
    private fun showFeedbackToast(recordId: Long?) {
        if (!isAdded) return
        recordId ?: return

        val count = recordId.toInt() // proxy for display; real count comes from DB
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.detection_feedback_message))
            .setMessage("Was this a real pothole? Your answer trains the local model.")
            .setPositiveButton("✅ Yes, real") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    federatedFusionManager.submitFeedback(recordId, true)
                }
            }
            .setNegativeButton("❌ No, false alarm") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    federatedFusionManager.submitFeedback(recordId, false)
                }
            }
            .show()
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
        try {
            val now = System.currentTimeMillis()
            if (now - lastAnalysisTime < ANALYSIS_INTERVAL_MS) {
                return
            }
            lastAnalysisTime = now

            val fps = calculateSmoothedFps(now)

            val bitmap = imageProxyToBitmapSafe(image)
            if (bitmap != null) {
                val inferenceStart = System.nanoTime()
                val detections = potholeDetectionHelper.detectPotholes(bitmap)
                val inferenceMs = (System.nanoTime() - inferenceStart) / 1_000_000
                updateHomeTestMetrics(fps, inferenceMs, detections.size)
                Log.d(TAG, "CV detected ${detections.size} pothole(s)")

                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    overlayView.updateDebugMetrics(fps, inferenceMs, detections.size)
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
                updateHomeTestConversionError()
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    overlayView.updateDebugMetrics(fps, 0L, 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Analyzer pipeline error", e)
        } finally {
            image.close()
        }
    }

    private fun calculateSmoothedFps(nowMs: Long): Float {
        val last = lastDebugFrameTime
        lastDebugFrameTime = nowMs
        if (last == 0L) return 0f

        val deltaMs = (nowMs - last).coerceAtLeast(1L)
        val instantFps = 1000f / deltaMs
        smoothedFps = if (smoothedFps == 0f) instantFps else (smoothedFps * 0.7f + instantFps * 0.3f)
        return smoothedFps
    }

    private fun toggleHomeTestMode(button: Button) {
        if (homeTestModeEnabled) {
            stopHomeTestMode(button)
        } else {
            startHomeTestMode(button)
        }
    }

    private fun startHomeTestMode(button: Button) {
        homeTestModeEnabled = true
        homeTestStartMs = System.currentTimeMillis()
        homeTestFrameCount = 0
        homeTestDetectionFrames = 0
        homeTestInferenceMsTotal = 0L
        homeTestConversionErrors = 0
        homeTestLastLogMs = 0L

        button.text = getString(R.string.action_test_on)
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
        )
        Toast.makeText(requireContext(), R.string.home_test_started, Toast.LENGTH_SHORT).show()
    }

    private fun stopHomeTestMode(button: Button) {
        homeTestModeEnabled = false
        button.text = getString(R.string.action_test_off)
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
        )

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.home_test_summary_title))
            .setMessage(buildHomeTestSummary())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun updateHomeTestMetrics(fps: Float, inferenceMs: Long, detectionsCount: Int) {
        if (!homeTestModeEnabled) return

        homeTestFrameCount++
        homeTestInferenceMsTotal += inferenceMs
        if (detectionsCount > 0) {
            homeTestDetectionFrames++
        }

        val now = System.currentTimeMillis()
        if (now - homeTestLastLogMs >= 5000L) {
            homeTestLastLogMs = now
            Log.i(
                TAG,
                "HomeTest: frames=$homeTestFrameCount fps=${String.format(Locale.US, "%.2f", fps)} avgInf=${averageInferenceMs()}ms detections=$homeTestDetectionFrames convErr=$homeTestConversionErrors"
            )
        }
    }

    private fun updateHomeTestConversionError() {
        if (!homeTestModeEnabled) return
        homeTestConversionErrors++
    }

    private fun averageInferenceMs(): Long {
        return if (homeTestFrameCount <= 0) 0L else homeTestInferenceMsTotal / homeTestFrameCount
    }

    private fun buildHomeTestSummary(): String {
        val elapsedMs = (System.currentTimeMillis() - homeTestStartMs).coerceAtLeast(1L)
        val elapsedSec = elapsedMs / 1000f
        val avgFps = if (elapsedSec > 0f) homeTestFrameCount / elapsedSec else 0f
        val avgInference = averageInferenceMs()
        val detectionRate = if (homeTestFrameCount > 0) {
            (homeTestDetectionFrames * 100f) / homeTestFrameCount
        } else {
            0f
        }

        val enoughFrames = homeTestFrameCount >= 15
        val fpsHealthy = avgFps >= 1.5f
        val inferenceHealthy = avgInference in 1..800
        val conversionHealthy = homeTestConversionErrors <= 3
        val verdict = if (enoughFrames && fpsHealthy && inferenceHealthy && conversionHealthy) {
            getString(R.string.home_test_verdict_pass)
        } else {
            getString(R.string.home_test_verdict_attention)
        }

        return String.format(
            Locale.US,
            "%s\n\nDuration: %.1fs\nFrames analyzed: %d\nAvg FPS: %.2f\nAvg inference: %dms\nDetection frames: %d (%.1f%%)\nConversion errors: %d\n\nChecks:\n- Frames >= 15: %s\n- FPS >= 1.5: %s\n- Inference 1..800ms: %s\n- Conversion errors <= 3: %s",
            verdict,
            elapsedSec,
            homeTestFrameCount,
            avgFps,
            avgInference,
            homeTestDetectionFrames,
            detectionRate,
            homeTestConversionErrors,
            yesNo(enoughFrames),
            yesNo(fpsHealthy),
            yesNo(inferenceHealthy),
            yesNo(conversionHealthy)
        )
    }

    private fun yesNo(value: Boolean): String {
        return if (value) getString(android.R.string.yes) else getString(android.R.string.no)
    }

    private fun exitLiveScreen() {
        val popped = parentFragmentManager.popBackStackImmediate()
        if (!popped) {
            requireActivity().finish()
        }
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
        potholeDetectionHelper.close()
        if (serviceBound) {
            sensorService?.setOnAnomalyDetectedListener(null)
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
        fusionEngine.reset()
        Log.d(TAG, "Fragment destroyed, resources released")
    }
}
