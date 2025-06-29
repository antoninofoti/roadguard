package com.example.roadguard.camera

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.roadguard.model.Report
import com.example.roadguard.services.SensorService
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import android.graphics.BitmapFactory
import android.graphics.Bitmap

@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var location by remember { mutableStateOf<Location?>(null) }
    var sensorService by remember { mutableStateOf<SensorService?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!OpenCVLoader.initDebug()) {
            Log.e("CameraScreen", "OpenCV initialization failed")
        } else {
            Log.d("CameraScreen", "OpenCV initialization successful")
        }
    }

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as SensorService.LocalBinder
                sensorService = binder.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                sensorService = null
            }
        }
    }

    LaunchedEffect(Unit) {
        Intent(context, SensorService::class.java).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            context.unbindService(connection)
        }
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted: Boolean ->
            if (isGranted) {
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                        location = loc
                    }
                } catch (e: SecurityException) {
                    Log.e("CameraScreen", "Location permission not granted", e)
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                val previewView = PreviewView(it)
                val executor = ContextCompat.getMainExecutor(it)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Use case binding failed", e)
                    }
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        if (isUploading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            Button(
                onClick = {
                    val severity = sensorService?.getSeverity() ?: 0f
                    takePicture(imageCapture, context, location, severity) {
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text("Take Picture")
            }
        }
    }
}

private fun takePicture(
    imageCapture: ImageCapture,
    context: Context,
    location: Location?,
    severity: Float,
    onReportUploaded: () -> Unit
) {
    val photoFile = createFile(context)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                Log.d("CameraScreen", "Photo capture succeeded: $savedUri")
                val processedUri = processImage(context, savedUri)
                uploadReport(processedUri, location, severity, onReportUploaded)
            }

            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraScreen", "Photo capture failed: ${exc.message}", exc)
            }
        }
    )
}

private fun processImage(context: Context, uri: Uri): Uri {
    val inputStream = context.contentResolver.openInputStream(uri)
    val bitmap = BitmapFactory.decodeStream(inputStream)
    val mat = Mat()
    Utils.bitmapToMat(bitmap, mat)

    // Convert to grayscale
    val grayMat = Mat()
    Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)

    // Apply Canny edge detection
    val edgesMat = Mat()
    Imgproc.Canny(grayMat, edgesMat, 80.0, 100.0)

    // Convert back to bitmap
    val resultBitmap = Bitmap.createBitmap(edgesMat.cols(), edgesMat.rows(), Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(edgesMat, resultBitmap)

    // Save the processed bitmap to a new file
    val processedFile = createFile(context, "processed_")
    val outputStream = FileOutputStream(processedFile)
    resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
    outputStream.close()

    return Uri.fromFile(processedFile)
}

private fun uploadReport(
    imageUri: Uri,
    location: Location?,
    severity: Float,
    onReportUploaded: () -> Unit
) {
    val storageRef = FirebaseStorage.getInstance().reference
    val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid ?: return

    val imageRef = storageRef.child("images/${userId}/${imageUri.lastPathSegment}")
    val uploadTask = imageRef.putFile(imageUri)

    uploadTask.continueWithTask { task ->
        if (!task.isSuccessful) {
            task.exception?.let {
                throw it
            }
        }
        imageRef.downloadUrl
    }.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val downloadUri = task.result
            val geoPoint = location?.let { GeoPoint(it.latitude, it.longitude) }

            val report = Report(
                imageUrl = downloadUri.toString(),
                location = geoPoint,
                userId = userId,
                severity = severity
            )

            firestore.collection("reports")
                .add(report)
                .addOnSuccessListener {
                    Log.d("CameraScreen", "Report uploaded successfully")
                    onReportUploaded()
                }
                .addOnFailureListener { e ->
                    Log.e("CameraScreen", "Error uploading report", e)
                }
        }
    }
}

private fun createFile(context: Context, prefix: String = "JPEG_"): File {
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir: File? = context.getExternalFilesDir(null)
    return File.createTempFile(
        "${prefix}${timeStamp}_",
        ".jpg",
        storageDir
    )
}
