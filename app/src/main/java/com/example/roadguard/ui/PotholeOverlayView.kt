package com.example.roadguard.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.example.roadguard.R
import com.example.roadguard.tflite.Detection
import java.util.Locale

/**
 * HUD overlay rendered on top of the CameraX preview.
 *
 * Draws:
 *  - Bounding boxes for active detections (red stroke + label)
 *  - A "no detection" dim overlay when requested
 *  - A bottom status strip with CV / IMU / Fused scores and a colour-coded state dot
 *  - A pulsing ring animation when a high-confidence detection occurs
 *
 * Design rationale (Appendix B.4):
 *  - All elements are semi-transparent; the camera feed remains legible.
 *  - Status strip is at the bottom to minimise cognitive interference with driving.
 *  - Colour semantics: GREEN = scanning/clear, AMBER = medium confidence, RED = high confidence / auto-report.
 */
class PotholeOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Detection state ──────────────────────────────────────────────────────
    var detections: List<Detection> = emptyList()
        set(value) { field = value; invalidate() }

    private var showPlaceholder = false

    /** Latest fusion scores — set by the fragment after each FusionEngine call. */
    var cvScore: Float = 0f
    var imuScore: Float = 0f
    var fusedScore: Float = 0f

    /** Live debug metrics for scan health visibility. */
    var debugFps: Float = 0f
    var debugInferenceMs: Long = 0L
    var debugDetectionCount: Int = 0

    /**
     * Semantic state that controls the status-dot colour and label.
     * SCANNING  → green
     * DETECTED  → amber (PROMPT_USER territory)
     * SUBMITTED → red (AUTO_REPORT submitted)
     */
    enum class DetectionState { SCANNING, DETECTED, SUBMITTED }
    var detectionState: DetectionState = DetectionState.SCANNING
        set(value) { field = value; if (value != DetectionState.SCANNING) startPulse() else stopPulse(); invalidate() }

    // ── Paints ───────────────────────────────────────────────────────────────
    private val boxPaint = Paint().apply {
        color = Color.rgb(239, 68, 68)   // red-500
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val labelBackPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val labelTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }
    private val placeholderPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val stripPaint = Paint().apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val statusTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isAntiAlias = true
    }
    private val debugStripPaint = Paint().apply {
        color = Color.argb(165, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val debugTextPaint = Paint().apply {
        color = Color.rgb(134, 239, 172) // green-300
        textSize = 24f
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }
    private val dotPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val pulsePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    // ── Pulse animation ──────────────────────────────────────────────────────
    private var pulseRadius = 0f
    private var pulseAlpha = 0
    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 800
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            val frac = it.animatedFraction
            pulseRadius = frac * 80f
            pulseAlpha = ((1f - frac) * 200).toInt()
            invalidate()
        }
    }

    private fun startPulse() { if (!pulseAnimator.isRunning) pulseAnimator.start() }
    private fun stopPulse()  { pulseAnimator.cancel(); pulseRadius = 0f; pulseAlpha = 0 }

    // ── Public helpers ───────────────────────────────────────────────────────
    fun showNoDetectionPlaceholder(show: Boolean) { showPlaceholder = show; invalidate() }

    /** Convenience: update all HUD values and trigger redraw. */
    fun updateHud(cv: Float, imu: Float, fused: Float, state: DetectionState) {
        cvScore = cv; imuScore = imu; fusedScore = fused; detectionState = state
    }

    /** Updates debug strip metrics and triggers redraw. */
    fun updateDebugMetrics(fps: Float, inferenceMs: Long, detectionCount: Int) {
        debugFps = fps
        debugInferenceMs = inferenceMs
        debugDetectionCount = detectionCount
        invalidate()
    }

    // ── Drawing ──────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (detections.isEmpty() && showPlaceholder) {
            canvas.drawRect(0f, 0f, w, h, placeholderPaint)
        }

        // Bounding boxes
        for (det in detections) {
            val box = det.boundingBox
            canvas.drawRect(box, boxPaint)
            val label = "${det.label} ${(det.confidence * 100).toInt()}%"
            val tw = labelTextPaint.measureText(label)
            val top = maxOf(box.top - 44f, 0f)
            canvas.drawRect(box.left, top, box.left + tw + 12f, top + 44f, labelBackPaint)
            canvas.drawText(label, box.left + 6f, top + 32f, labelTextPaint)
        }

        // Pulse ring (centred on screen)
        if (pulseRadius > 0f) {
            pulsePaint.color = dotColorForState(detectionState)
            pulsePaint.alpha = pulseAlpha
            canvas.drawCircle(w / 2f, h / 2f, pulseRadius, pulsePaint)
        }

        // Top debug strip (scan health)
        val debugStripH = 56f
        canvas.drawRect(0f, 0f, w, debugStripH, debugStripPaint)
        val debugText = String.format(
            Locale.US,
            "FPS %.1f   INF %dms   DET %d",
            debugFps,
            debugInferenceMs,
            debugDetectionCount
        )
        canvas.drawText(debugText, 18f, 36f, debugTextPaint)

        // Bottom status strip
        val stripH = 72f
        canvas.drawRect(0f, h - stripH, w, h, stripPaint)

        // State dot
        dotPaint.color = dotColorForState(detectionState)
        canvas.drawCircle(36f, h - stripH / 2f, 14f, dotPaint)

        // Score text
        val stateLabel = when (detectionState) {
            DetectionState.SCANNING  -> context.getString(R.string.detection_scanning)
            DetectionState.DETECTED  -> context.getString(R.string.detection_detected)
            DetectionState.SUBMITTED -> context.getString(R.string.detection_submitted)
        }
        val scoreText = "$stateLabel   CV:${pct(cvScore)}%  IMU:${pct(imuScore)}%  Fused:${pct(fusedScore)}%"
        statusTextPaint.alpha = 230
        canvas.drawText(scoreText, 60f, h - stripH / 2f + 10f, statusTextPaint)
    }

    private fun pct(v: Float) = (v * 100).toInt()

    private fun dotColorForState(state: DetectionState) = when (state) {
        DetectionState.SCANNING  -> Color.rgb(34, 197, 94)   // green-500
        DetectionState.DETECTED  -> Color.rgb(251, 191, 36)  // amber-400
        DetectionState.SUBMITTED -> Color.rgb(239, 68, 68)   // red-500
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPulse()
    }
}
