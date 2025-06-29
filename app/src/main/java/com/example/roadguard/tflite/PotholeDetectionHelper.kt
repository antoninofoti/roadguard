package com.example.roadguard.tflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
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
        fun initOpenCV() {
            OpenCVLoader.initDebug()
        }
    }

    private val MODEL_NAME = "pothole_model.tflite"
    private val INPUT_IMAGE_WIDTH = 300
    private val INPUT_IMAGE_HEIGHT = 300

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

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)

        val blurredMat = Mat()
        Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)

        val processedBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(blurredMat, processedBitmap)

        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(INPUT_IMAGE_HEIGHT, INPUT_IMAGE_WIDTH, ResizeOp.ResizeMethod.BILINEAR))
            .build()

        var tensorImage = TensorImage.fromBitmap(processedBitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val outputLocations = TensorBuffer.createFixedSize(intArrayOf(1, 10, 4), org.tensorflow.lite.DataType.FLOAT32)
        val outputClasses = TensorBuffer.createFixedSize(intArrayOf(1, 10), org.tensorflow.lite.DataType.FLOAT32)
        val outputScores = TensorBuffer.createFixedSize(intArrayOf(1, 10), org.tensorflow.lite.DataType.FLOAT32)
        val numDetections = TensorBuffer.createFixedSize(intArrayOf(1), org.tensorflow.lite.DataType.FLOAT32)

        val outputs = mapOf(
            0 to outputLocations.buffer,
            1 to outputClasses.buffer,
            2 to outputScores.buffer,
            3 to numDetections.buffer
        )

        interpreter?.runForMultipleInputsOutputs(arrayOf(tensorImage.buffer), outputs)

        val detections = mutableListOf<Detection>()
        val scores = outputScores.floatArray
        val locations = outputLocations.floatArray
        val classes = outputClasses.floatArray

        for (i in scores.indices) {
            if (scores[i] > 0.5f) { // Confidence threshold
                val boundingBox = RectF(
                    locations[i * 4 + 1] * bitmap.width,
                    locations[i * 4] * bitmap.height,
                    locations[i * 4 + 3] * bitmap.width,
                    locations[i * 4 + 2] * bitmap.height
                )
                detections.add(
                    Detection(
                        boundingBox,
                        "Pothole", // Assuming single class "Pothole"
                        scores[i]
                    )
                )
            }
        }
        return detections
    }
}
