package com.example.roadguard.network

import android.os.Build
import android.util.Log
import com.example.roadguard.ml.FederatedFusionManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Coordinates Federated Learning rounds via Firebase Firestore.
 * Implements the FedRoadGuard orchestration layer (Thesis Chapter 4).
 *
 * PRIVACY PRINCIPLE: Only local F1 scores are uploaded — NEVER the fusion weights.
 */
class FirebaseFLCoordinator(
    private val firestore: FirebaseFirestore,
    private val fusionManager: FederatedFusionManager,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "FirebaseFLCoordinator"
        private const val COLLECTION_ROUNDS = "fl_rounds"
        private const val COLLECTION_METRICS = "fl_metrics"
    }

    private val deviceId: String = getAnonymizedDeviceId()

    private val _currentRound = MutableStateFlow<DocumentSnapshot?>(null)
    val currentRound = _currentRound.asStateFlow()

    init {
        startListeningForRounds()
    }

    /**
     * Set up a listener for new FL rounds.
     */
    private fun startListeningForRounds() {
        firestore.collection(COLLECTION_ROUNDS)
            .whereEqualTo("status", "open")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w(TAG, "FedRG: Listen failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    _currentRound.value = snapshot.documents.first()
                } else {
                    _currentRound.value = null
                }
            }
    }

    /**
     * Get the currently active FL round (synchronous-like via await).
     */
    suspend fun getCurrentRound(): DocumentSnapshot? {
        return try {
            firestore.collection(COLLECTION_ROUNDS)
                .whereEqualTo("status", "open")
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching current round", e)
            null
        }
    }

    /**
     * Participate in the current FL round by uploading local F1 utility metrics.
     */
    fun participateInRound(onComplete: (Boolean) -> Unit = {}) {
        coroutineScope.launch(Dispatchers.IO) {
            val round = _currentRound.value ?: getCurrentRound()
            if (round == null) {
                Log.d(TAG, "No open FL round found. Skipping participation.")
                onComplete(false)
                return@launch
            }

            val localF1 = fusionManager.getLocalF1()
            if (localF1 <= 0f) {
                Log.d(TAG, "Local F1 is 0.0 (no optimization done). Skipping participation.")
                onComplete(false)
                return@launch
            }

            val roundId = round.id
            val metrics = hashMapOf(
                "round_id" to roundId,
                "local_f1" to localF1.toDouble(),
                "device_model" to Build.MODEL,
                "app_version" to "1.0.0-fedrg",
                "timestamp" to Timestamp.now()
            )

            try {
                firestore.collection(COLLECTION_METRICS)
                    .document("${deviceId}_${roundId}")
                    .set(metrics)
                    .await()
                
                Log.i(TAG, "FedRG: Participated in round $roundId with local F1 %.4f".format(localF1))
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading metrics", e)
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    /**
     * Read stats for a specific round to display in the UI.
     */
    suspend fun getRoundStats(roundId: String): Map<String, Any>? {
        return try {
            firestore.collection(COLLECTION_ROUNDS).document(roundId).get().await().data
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generate an anonymized device ID using a SHA-256 hash of hardware identifiers.
     * Truncated to 16 chars to preserve privacy while maintaining uniqueness.
     */
    private fun getAnonymizedDeviceId(): String {
        return try {
            val input = "${Build.MODEL}${Build.BOARD}"
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            "unknown_device_${System.currentTimeMillis()}"
        }
    }
}
