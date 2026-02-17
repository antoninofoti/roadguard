package com.example.roadguard.repository

import android.net.Uri
import com.example.roadguard.model.DetectionSource
import com.example.roadguard.model.Report
import com.example.roadguard.model.ReportStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class ReportRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val reportsCollection = firestore.collection("reports")
    private val storageReference = storage.reference.child("report_images")

    // ========== Original methods (backward compatible) ==========

    suspend fun addReport(imageUri: Uri, location: GeoPoint, severity: Float): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not logged in"))
            val imageFileName = "${UUID.randomUUID()}.jpg"
            val imageRef = storageReference.child(imageFileName)

            // 1. Upload image to Firebase Storage
            val uploadTask = imageRef.putFile(imageUri).await()
            val imageUrl = uploadTask.storage.downloadUrl.await().toString()

            // 2. Create Report object
            val reportId = reportsCollection.document().id
            val report = Report(
                id = reportId,
                userId = userId,
                imageUrl = imageUrl,
                location = location,
                timestamp = Date(),
                severity = severity
            )

            // 3. Save report to Firestore
            reportsCollection.document(reportId).set(report).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReports(): Result<List<Report>> {
        return try {
            val snapshot = reportsCollection.get().await()
            val reports = snapshot.toObjects(Report::class.java)
            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserReports(userId: String): Result<List<Report>> {
        return try {
            val snapshot = reportsCollection.whereEqualTo("userId", userId).get().await()
            val reports = snapshot.toObjects(Report::class.java)
            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteReport(reportId: String): Result<Unit> {
        return try {
            reportsCollection.document(reportId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== Phase 2: Fusion-aware methods ==========

    /**
     * Create a report with full fusion metadata.
     *
     * Used by the FusionEngine when auto-reporting or after user confirmation.
     *
     * @param imageUri Local URI of the captured image
     * @param location GPS location of the detection
     * @param severity Normalized severity (0.0 - 1.0)
     * @param cvConfidence CV model confidence
     * @param sensorConfidence Sensor anomaly confidence
     * @param fusedScore Combined fusion score
     * @param damageType Type of damage detected
     * @param detectionSource How the report was generated (DUAL_CONFIRMED, CV_ONLY, etc.)
     */
    suspend fun addFusionReport(
        imageUri: Uri,
        location: GeoPoint,
        severity: Float,
        cvConfidence: Float,
        sensorConfidence: Float,
        fusedScore: Float,
        damageType: String,
        detectionSource: String
    ): Result<String> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.failure(Exception("User not logged in"))

            val imageFileName = "${UUID.randomUUID()}.jpg"
            val imageRef = storageReference.child(imageFileName)

            // 1. Upload image
            val uploadTask = imageRef.putFile(imageUri).await()
            val imageUrl = uploadTask.storage.downloadUrl.await().toString()

            // 2. Create report with fusion data
            val reportId = reportsCollection.document().id
            val report = Report(
                id = reportId,
                userId = userId,
                imageUrl = imageUrl,
                location = location,
                timestamp = Date(),
                severity = severity,
                status = ReportStatus.PENDING.name,
                detectionSource = detectionSource,
                cvConfidence = cvConfidence,
                sensorConfidence = sensorConfidence,
                fusedScore = fusedScore,
                damageType = damageType
            )

            // 3. Save to Firestore
            reportsCollection.document(reportId).set(report).await()
            Result.success(reportId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update the status of a report (operator workflow).
     *
     * @param reportId ID of the report to update
     * @param newStatus New status (CONFIRMED, REJECTED, RESOLVED)
     * @param operatorId UID of the operator making the change
     * @param notes Optional operator notes
     */
    suspend fun updateReportStatus(
        reportId: String,
        newStatus: ReportStatus,
        operatorId: String,
        notes: String = ""
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus.name,
                "operatorId" to operatorId,
                "notes" to notes
            )
            if (newStatus == ReportStatus.RESOLVED) {
                updates["resolvedAt"] = Date()
            }
            reportsCollection.document(reportId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all pending reports for the operator dashboard.
     * Returns reports sorted by timestamp (newest first).
     */
    suspend fun getPendingReports(): Result<List<Report>> {
        return try {
            val snapshot = reportsCollection
                .whereEqualTo("status", ReportStatus.PENDING.name)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            val reports = snapshot.toObjects(Report::class.java)
            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get reports filtered by status.
     */
    suspend fun getReportsByStatus(status: ReportStatus): Result<List<Report>> {
        return try {
            val snapshot = reportsCollection
                .whereEqualTo("status", status.name)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            val reports = snapshot.toObjects(Report::class.java)
            Result.success(reports)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

