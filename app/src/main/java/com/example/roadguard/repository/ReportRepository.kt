package com.example.roadguard.repository

import android.net.Uri
import com.example.roadguard.model.Report
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ReportRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val reportsCollection = firestore.collection("reports")
    private val storageReference = storage.reference.child("report_images")

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
                timestamp = System.currentTimeMillis(),
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
            // Optional: Delete image from storage as well
            // val report = getReport(reportId).getOrNull()
            // report?.imageUrl?.let { storage.getReferenceFromUrl(it).delete().await() }
            reportsCollection.document(reportId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
