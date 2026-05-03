package com.example.roadguard.ml

import android.content.Context
import android.util.Log
import com.example.roadguard.data.local.DetectionDao
import com.example.roadguard.data.local.DetectionRecord
import com.example.roadguard.network.FirebaseFLCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlin.math.max

/**
 * Manages on-device personalization of fusion weights (alpha, beta, gamma).
 * Implements a local grid search over fusion parameters based on user feedback.
 * Part of the FedRoadGuard architecture (Thesis Chapter 4).
 */
class FederatedFusionManager(
    private val context: Context,
    private val detectionDao: DetectionDao,
    private val coroutineScope: CoroutineScope
) {
    private var flCoordinator: FirebaseFLCoordinator? = null

    fun setFLCoordinator(coordinator: FirebaseFLCoordinator) {
        this.flCoordinator = coordinator
    }

    companion object {
        private const val TAG = "FedFusionManager"
        const val MIN_SAMPLES_FOR_OPTIMIZATION = 20
        const val MAX_HISTORY_SIZE = 100
        const val PREFS_NAME = "roadguard_fusion_prefs"
        const val PREF_ALPHA = "local_alpha"
        const val PREF_BETA = "local_beta"
        const val PREF_GAMMA = "local_gamma"
        const val PREF_LOCAL_F1 = "local_f1"
        const val PREF_ENABLED = "personalization_enabled"
        
        val ALPHA_SWEEP = listOf(0.30f, 0.40f, 0.50f, 0.55f, 0.60f, 0.70f)
        const val GAMMA_FIXED = 0.15f
    }

    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Record a detection event for future optimization.
     */
    fun recordDetection(cvConf: Float, imuConf: Float, fusedScore: Float, predicted: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            val record = DetectionRecord(
                timestamp = System.currentTimeMillis(),
                cvConf = cvConf,
                imuConf = imuConf,
                fusedScore = fusedScore,
                predictedLabel = predicted,
                groundTruthLabel = -1 // Unknown until feedback
            )
            val id = detectionDao.insert(record)
            detectionDao.trimHistory(MAX_HISTORY_SIZE)
            Log.d(TAG, "Recorded detection #$id for local optimization history.")
        }
    }

    /**
     * Submit user feedback for a specific detection.
     */
    fun submitFeedback(recordId: Long, isActuallyPothole: Boolean) {
        coroutineScope.launch(Dispatchers.IO) {
            val record = detectionDao.getById(recordId) ?: return@launch
            val updatedRecord = record.copy(groundTruthLabel = if (isActuallyPothole) 1 else 0)
            detectionDao.update(updatedRecord)
            
            val count = detectionDao.countLabeled()
            Log.d(TAG, "Feedback received for record #$recordId. Total labeled: $count")
            
            if (count >= MIN_SAMPLES_FOR_OPTIMIZATION) {
                tryOptimizeFusionWeights()
            }
        }
    }

    /**
     * Perform local grid search to find optimal fusion weights for this device.
     */
    private suspend fun tryOptimizeFusionWeights() = withContext(Dispatchers.Default) {
        val records = detectionDao.getLabeled()
        if (records.size < MIN_SAMPLES_FOR_OPTIMIZATION) return@withContext

        Log.d(TAG, "Starting local fusion weight optimization on ${records.size} samples...")
        
        var bestAlpha = 0.55f
        var bestBeta = 0.30f
        var bestF1 = -1f

        for (alpha in ALPHA_SWEEP) {
            val beta = max(0f, 0.85f - alpha)
            val gamma = GAMMA_FIXED
            
            val currentF1 = computeLocalF1(records, alpha, beta, gamma)
            if (currentF1 > bestF1) {
                bestF1 = currentF1
                bestAlpha = alpha
                bestBeta = beta
            }
        }

        sharedPrefs.edit()
            .putFloat(PREF_ALPHA, bestAlpha)
            .putFloat(PREF_BETA, bestBeta)
            .putFloat(PREF_GAMMA, GAMMA_FIXED)
            .putFloat(PREF_LOCAL_F1, bestF1)
            .apply()

        Log.i(TAG, "FedRG: Local optimization complete. alpha=%.2f beta=%.2f F1=%.4f"
            .format(bestAlpha, bestBeta, bestF1))

        // Task 9A: Participate in FL round after optimization
        if (isNetworkAvailable()) {
            coroutineScope.launch {
                flCoordinator?.participateInRound()
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Get the current personalized weights.
     */
    fun getPersonalizedWeights(): Triple<Float, Float, Float> {
        val alpha = sharedPrefs.getFloat(PREF_ALPHA, 0.55f)
        val beta = sharedPrefs.getFloat(PREF_BETA, 0.30f)
        val gamma = sharedPrefs.getFloat(PREF_GAMMA, 0.15f)
        return Triple(alpha, beta, gamma)
    }

    /**
     * Check if personalization is enabled in settings.
     */
    fun isPersonalizationEnabled(): Boolean {
        return sharedPrefs.getBoolean(PREF_ENABLED, false)
    }

    /**
     * Get the local F1 score from the last optimization.
     */
    fun getLocalF1(): Float {
        return sharedPrefs.getFloat(PREF_LOCAL_F1, 0.0f)
    }

    /**
     * Internal helper to compute F1 score on local history for a specific weight set.
     */
    private fun computeLocalF1(
        records: List<DetectionRecord>,
        alpha: Float,
        beta: Float,
        gamma: Float
    ): Float {
        var tp = 0
        var fp = 0
        var fn = 0

        for (record in records) {
            // Temporal bonus is 1.0 if both signals are present in our simple history model
            val temporalBonus = if (record.cvConf > 0.1f && record.imuConf > 0.1f) 1.0f else 0.0f
            val score = alpha * record.cvConf + beta * record.imuConf + gamma * temporalBonus
            val predicted = if (score >= 0.50f) 1 else 0
            val actual = record.groundTruthLabel

            when {
                predicted == 1 && actual == 1 -> tp++
                predicted == 1 && actual == 0 -> fp++
                predicted == 0 && actual == 1 -> fn++
            }
        }

        if (tp == 0) return 0f
        val precision = tp.toFloat() / (tp + fp)
        val recall = tp.toFloat() / (tp + fn)
        return 2 * (precision * recall) / (precision + recall)
    }
}
