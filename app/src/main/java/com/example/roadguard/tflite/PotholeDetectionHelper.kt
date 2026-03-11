package com.example.roadguard.tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class PotholeDetectionHelper(context: Context) {

    companion object {
        private const val MODEL_NAME = "pothole-y8objdect_float16.tflite"
        private const val INPUT_IMAGE_WIDTH = 640
        private const val INPUT_IMAGE_HEIGHT = 640

        fun initOpenCV() {
            OpenCVLoader.initLocal()
        }
    }

    private var interpreter: Interpreter? = null

    init {
        interpreter = Interpreter(loadModelFile(context))
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_NAME)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
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

        // CORRECT: YOLOv8 output: [1, 6, 8400] (6: x, y, w, h, conf, class; 8400: boxes)
        val outputBuffer = Array(1) { Array(6) { FloatArray(8400) } }

        interpreter?.run(tensorImage.buffer, outputBuffer)

        val detections = mutableListOf<Detection>()
        val originalWidth = bitmap.width.toFloat()
        val originalHeight = bitmap.height.toFloat()
        val confThreshold = 0.3f

        // Loop over 8400 boxes
        for (i in 0 until 8400) {
            val x = outputBuffer[0][0][i]
            val y = outputBuffer[0][1][i]
            val w = outputBuffer[0][2][i]
            val h = outputBuffer[0][3][i]
            val conf = outputBuffer[0][4][i]
            // val cls = outputBuffer[0][5][i] (Unused for now)
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
