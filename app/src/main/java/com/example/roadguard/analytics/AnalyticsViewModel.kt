package com.example.roadguard.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roadguard.model.Report
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for the analytics dashboard.
 *
 * Fetches all reports from Firestore and runs on-device
 * predictive analytics: priority zones, clusters, trends, and summary.
 */
class AnalyticsViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val analytics = PredictiveAnalytics()

    // State
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _summary = MutableStateFlow<AnalyticsSummary?>(null)
    val summary: StateFlow<AnalyticsSummary?> = _summary

    private val _priorityZones = MutableStateFlow<List<PriorityZone>>(emptyList())
    val priorityZones: StateFlow<List<PriorityZone>> = _priorityZones

    private val _clusters = MutableStateFlow<List<DamageCluster>>(emptyList())
    val clusters: StateFlow<List<DamageCluster>> = _clusters

    private val _forecast = MutableStateFlow<DegradationForecast?>(null)
    val forecast: StateFlow<DegradationForecast?> = _forecast

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        refresh()
    }

    /**
     * Fetch all reports and run analytics.
     */
    fun refresh() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("reports").get().await()
                val reports: List<Report> = snapshot.toObjects()

                // Run all analytics on the fetched data
                _summary.value = analytics.computeSummary(reports)
                _priorityZones.value = analytics.calculateMaintenancePriority(reports)
                _clusters.value = analytics.identifyDamageClusters(reports)
                _forecast.value = analytics.predictDegradationTrend(reports)

            } catch (e: Exception) {
                _error.value = "Failed to load analytics: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
