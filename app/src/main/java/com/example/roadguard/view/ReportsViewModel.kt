package com.example.roadguard.view

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roadguard.model.Report
import com.example.roadguard.repository.ReportRepository
import kotlinx.coroutines.launch

class ReportsViewModel : ViewModel() {

    private val reportRepository = ReportRepository()

    val reports = mutableStateOf<List<Report>>(emptyList())
    val isLoading = mutableStateOf(true)
    val error = mutableStateOf<String?>(null)

    init {
        fetchReports()
    }

    fun fetchReports() {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            reportRepository.getReports()
                .onSuccess {
                    reports.value = it.sortedByDescending { report -> report.timestamp } // Show newest first
                }
                .onFailure {
                    error.value = it.message
                }
            isLoading.value = false
        }
    }
}
