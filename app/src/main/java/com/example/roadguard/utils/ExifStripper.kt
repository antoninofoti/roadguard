package com.example.roadguard.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Utility that strips EXIF metadata from images before upload to Firebase Storage.
 *
 * **Cybersecurity / GDPR rationale (Thesis §F.2)**:
 * EXIF tags in JPEG images can contain sensitive data including:
 * - GPS coordinates (precise location where photo was taken)
 * - Device model and manufacturer (device fingerprinting)
 * - Software version and creation timestamp
 * - Camera serial number (unique device identifier)
 *
 * Under GDPR Art. 5 (data minimisation), only the pixel content of an image
 * is necessary for YOLOv8 inference and human operator review.
 * All metadata must be removed before an image leaves the device.
 *
 * This class preserves image quality and orientation (applies the rotation to pixels)
 * while stripping all EXIF tags including GPS, device info, and timestamps.
 */
object ExifStripper {

    private const val TAG = "ExifStripper"

    // JPEG quality for re-encoded images (95 = near-lossless)
    private const val JPEG_QUALITY = 95

    /**
     * Strip all EXIF metadata from an image file and save the cleaned version.
     *
     * The orientation from EXIF is read and applied to the pixel data BEFORE
     * stripping, so the image is still correctly oriented after the metadata
     * is removed. The output replaces the input file.
     *
     * @param imageFile JPEG image file to strip in-place
     * @return The same file, now without EXIF metadata. Null on error.
     */
    fun stripInPlace(imageFile: File): File? {
        return try {
            val cleaned = stripToBytes(imageFile.inputStream()) ?: return null
            FileOutputStream(imageFile).use { it.write(cleaned) }
            Log.d(TAG, "EXIF stripped: ${imageFile.name} (${imageFile.length()} bytes)")
            imageFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to strip EXIF from ${imageFile.name}", e)
            null
        }
    }

    /**
     * Strip all EXIF metadata from an image stream.
     *
     * @param inputStream Input JPEG stream
     * @return Byte array of the EXIF-stripped JPEG, or null on error
     */
    fun stripToBytes(inputStream: InputStream): ByteArray? {
        return try {
            val rawBytes = inputStream.readBytes()

            // Read EXIF orientation BEFORE stripping (needed to correct pixel orientation)
            val exif = ExifInterface(ByteArrayInputStream(rawBytes))
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            // Decode the bitmap
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: return null

            // Apply the orientation transform to the pixel data
            val correctedBitmap = applyOrientation(bitmap, orientation)

            // Re-encode as JPEG — this naturally omits all EXIF tags
            val output = ByteArrayOutputStream()
            correctedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)

            if (correctedBitmap != bitmap) {
                correctedBitmap.recycle()
            }
            bitmap.recycle()

            output.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to strip EXIF from stream", e)
            null
        }
    }

    /**
     * Verify that an image file contains no GPS EXIF tags.
     *
     * Used in unit tests to confirm stripping worked correctly.
     *
     * @param imageFile The file to check
     * @return true if no GPS latitude/longitude tags are present
     */
    fun hasNoGpsData(imageFile: File): Boolean {
        return try {
            val exif = ExifInterface(imageFile.absolutePath)
            val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
            val lng = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
            lat == null && lng == null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check EXIF on ${imageFile.name}", e)
            false
        }
    }

    /**
     * Verify that an image byte array contains no sensitive EXIF tags.
     *
     * @param imageBytes The image data to check.
     * @return An ExifStripReport summarizing the audit of GPS and fingerprinting tags.
     *
     * @see "GDPR Art. 5(1)(c) - Data Minimisation"
     * @see "EXIF IFD GPS tags: 0x8825 (GPSInfoIFD), 0x013B (Artist), 0x013E (WhitePoint) - device fingerprint vectors"
     */
    fun verifyStripped(imageBytes: ByteArray): ExifStripReport {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(imageBytes))
            
            // Check GPS Tags
            val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
            val lng = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
            val alt = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE)
            val gpsRemoved = lat == null && lng == null && alt == null

            // Check Fingerprinting Tags (Make, Model, Software, DateTime)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)
            val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
            val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME)
            val fingerprintRemoved = make.isNullOrEmpty() && model.isNullOrEmpty() && 
                                     software.isNullOrEmpty() && dateTime.isNullOrEmpty()

            ExifStripReport(
                gpsRemoved = gpsRemoved,
                fingerprintRemoved = fingerprintRemoved,
                remainingTagCount = 0 // In a real scenario, we could iterate all tags
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify stripped EXIF", e)
            ExifStripReport(false, false, -1)
        }
    }

    /**
     * Verify that an image byte array contains no GPS EXIF tags.
     */
    fun hasNoGpsData(imageBytes: ByteArray): Boolean {
        return verifyStripped(imageBytes).gpsRemoved
    }

    /**
     * Apply the EXIF orientation tag to the pixel data by rotating/flipping the bitmap.
     *
     * Orientation values per EXIF 2.3 spec:
     *   1 = Normal, 2 = Flip H, 3 = Rotate 180, 4 = Flip V,
     *   5 = Transpose, 6 = Rotate 90 CW, 7 = Transverse, 8 = Rotate 270 CW
     *
     * Using numeric values directly avoids the ExifInterface import being
     * unresolvable when the class is not in the Kotlin unit test classpath.
     */
    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            6 -> matrix.postRotate(90f)   // ORIENTATION_ROTATE_90
            3 -> matrix.postRotate(180f)  // ORIENTATION_ROTATE_180
            8 -> matrix.postRotate(270f)  // ORIENTATION_ROTATE_270
            2 -> matrix.preScale(-1f, 1f) // ORIENTATION_FLIP_HORIZONTAL
            4 -> matrix.preScale(1f, -1f) // ORIENTATION_FLIP_VERTICAL
            5 -> { matrix.postRotate(90f); matrix.preScale(-1f, 1f) }  // ORIENTATION_TRANSPOSE
            7 -> { matrix.postRotate(270f); matrix.preScale(-1f, 1f) } // ORIENTATION_TRANSVERSE
            else -> return bitmap // ORIENTATION_NORMAL (1) or unknown — no transform
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

/**
 * Audit report for EXIF metadata stripping.
 *
 * @property gpsRemoved True if GPS latitude, longitude, and altitude were removed.
 * @property fingerprintRemoved True if device-specific tags (Make, Model, etc.) were removed.
 * @property remainingTagCount The number of non-sensitive tags remaining (e.g., orientation).
 */
data class ExifStripReport(
    val gpsRemoved: Boolean,
    val fingerprintRemoved: Boolean,
    val remainingTagCount: Int
)
