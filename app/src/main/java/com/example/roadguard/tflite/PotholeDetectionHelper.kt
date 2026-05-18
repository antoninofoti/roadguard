package com.example.roadguard.tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class PotholeDetectionHelper(context: Context) {

    companion object {
        private const val MODEL_NAME = "yolov8n_pothole.tflite"
        private const val INPUT_IMAGE_WIDTH = 640
        private const val INPUT_IMAGE_HEIGHT = 640

        fun initOpenCV() {
            if (!org.opencv.android.OpenCVLoader.initDebug()) {
                android.util.Log.e("PotholeDetection", "OpenCV initialization failed via initDebug()")
            } else {
                android.util.Log.d("PotholeDetection", "OpenCV initialized successfully")
            }
        }
    }

    private var interpreter: Interpreter? = null
    private var hasLoggedModelInfo = false

    init {
        interpreter = Interpreter(loadModelFile(context))
        logModelTensorInfoIfNeeded()
    }

    /**
     * Release the TFLite Interpreter and its native XNNPACK delegate.
     * Must be called when the owning component (Fragment/Activity) is destroyed.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        hasLoggedModelInfo = false
    }

    private fun logModelTensorInfoIfNeeded() {
        if (hasLoggedModelInfo) return

        val tfLite = interpreter ?: return
        val inputShape = tfLite.getInputTensor(0).shape().contentToString()
        val outputShape = tfLite.getOutputTensor(0).shape().contentToString()
        android.util.Log.i(
            "PotholeDetection",
            "TFLite model tensors: input=$inputShape output=$outputShape"
        )
        hasLoggedModelInfo = true
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        context.assets.openFd(MODEL_NAME).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
        }
    }

    fun detectPotholes(bitmap: Bitmap): List<Detection> {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val blurredMat = Mat()
        Imgproc.GaussianBlur(mat, blurredMat, Size(5.0, 5.0), 0.0)

        val processedBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(blurredMat, processedBitmap)

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(INPUT_IMAGE_HEIGHT, INPUT_IMAGE_WIDTH, ResizeOp.ResizeMethod.BILINEAR))
            .add(org.tensorflow.lite.support.common.ops.NormalizeOp(0.0f, 255.0f))
            .build()

        var tensorImage = TensorImage.fromBitmap(processedBitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputShape = interpreter?.getOutputTensor(0)?.shape() ?: intArrayOf(1, 5, 8400)
        if (outputShape.size != 3 || outputShape[0] != 1) {
            android.util.Log.e("PotholeDetection", "Unexpected model output shape: ${outputShape.contentToString()}")
            mat.release()
            blurredMat.release()
            return emptyList()
        }

        val isChannelFirst = outputShape[1] <= outputShape[2]
        val channels = if (isChannelFirst) outputShape[1] else outputShape[2]
        val boxes = if (isChannelFirst) outputShape[2] else outputShape[1]
        val outputBuffer = Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }

        interpreter?.run(tensorImage.buffer, outputBuffer)

        val detections = mutableListOf<Detection>()
        val originalWidth = bitmap.width.toFloat()
        val originalHeight = bitmap.height.toFloat()
        // Lowered threshold to 0.10f for monitor/screen-recording demos
        val confThreshold = 0.10f

        // Supports common YOLO layouts: [1, 5, N], [1, 6, N], and [1, N, C].
        for (i in 0 until boxes) {
            val x = if (isChannelFirst) outputBuffer[0][0][i] else outputBuffer[0][i][0]
            val y = if (isChannelFirst) outputBuffer[0][1][i] else outputBuffer[0][i][1]
            val w = if (isChannelFirst) outputBuffer[0][2][i] else outputBuffer[0][i][2]
            val h = if (isChannelFirst) outputBuffer[0][3][i] else outputBuffer[0][i][3]
            val objectness = if (isChannelFirst) outputBuffer[0][4][i] else outputBuffer[0][i][4]
            val classMax = if (channels > 5) {
                var maxClass = 0f
                for (c in 5 until channels) {
                    val classConf = if (isChannelFirst) outputBuffer[0][c][i] else outputBuffer[0][i][c]
                    if (classConf > maxClass) maxClass = classConf
                }
                maxClass
            } else {
                1f
            }
            val conf = objectness * classMax
            if (conf > confThreshold) {
                val left = (x - w / 2) * (originalWidth / INPUT_IMAGE_WIDTH)
                val top = (y - h / 2) * (originalHeight / INPUT_IMAGE_HEIGHT)
                val right = (x + w / 2) * (originalWidth / INPUT_IMAGE_WIDTH)
                val bottom = (y + h / 2) * (originalHeight / INPUT_IMAGE_HEIGHT)
                val boundingBox = RectF(left, top, right, bottom)
                detections.add(
                    Detection(
                        boundingBox,
                        "Pothole", // or use class id if needed
                        conf
                    )
                )
            }
        }

        // Release OpenCV Mats to free memory
        mat.release()
        blurredMat.release()

        return detections
    }

    /**
     * Draws bounding boxes for detections on a bitmap.
     */
    fun drawDetections(bitmap: Bitmap, detections: List<Detection>): Bitmap {
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(resultBitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(128, 255, 0, 0) // Rosso trasparente
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.YELLOW
            textSize = 36f
            style = android.graphics.Paint.Style.FILL
        }
        for (det in detections) {
            canvas.drawRect(det.boundingBox, paint)
            canvas.drawText(
                "${det.label} ${(det.confidence * 100).toInt()}%",
                det.boundingBox.left,
                det.boundingBox.top - 10,
                textPaint
            )
        }
        return resultBitmap
    }
}
