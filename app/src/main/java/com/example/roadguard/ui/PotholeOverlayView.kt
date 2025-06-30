package com.example.roadguard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.roadguard.tflite.Detection

class PotholeOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var detections: List<Detection> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 36f
        style = Paint.Style.FILL
    }
    private val placeholderPaint = Paint().apply {
        color = Color.argb(80, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private var showPlaceholder = false
    fun showNoDetectionPlaceholder(show: Boolean) {
        showPlaceholder = show
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty() && showPlaceholder) {
            // Disegna overlay trasparente e messaggio
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), placeholderPaint)
            canvas.drawText(
                "Nessuna buca rilevata",
                width / 2f - 180,
                height / 2f,
                textPaint
            )
        } else {
            for (det in detections) {
                canvas.drawRect(det.boundingBox, paint)
                canvas.drawText(
                    "${det.label} ${(det.confidence * 100).toInt()}%",
                    det.boundingBox.left,
                    det.boundingBox.top - 10,
                    textPaint
                )
            }
        }
    }
}
