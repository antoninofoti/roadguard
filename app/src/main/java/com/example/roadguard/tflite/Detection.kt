package com.example.roadguard.tflite

import android.graphics.RectF

data class Detection(
    val boundingBox: RectF,
    val label: String,
    val confidence: Float
)
