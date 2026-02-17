package com.example.roadguard.operator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roadguard.model.Report
import com.example.roadguard.model.ReportStatus
import com.example.roadguard.repository.ReportRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the operator dashboard.
 *
 * Manages:
 * - Pending reports list
 * - Report status updates (confirm/reject/resolve)
 * - Filter by status
 */
class OperatorViewModel : ViewModel() {

    private val reportRepository = ReportRepository()
    private val auth = FirebaseAuth.getInstance()

    // Reports list
    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports

    // Selected report for detail view
    private val _selectedReport = MutableStateFlow<Report?>(null)
    val selectedReport: StateFlow<Report?> = _selectedReport

    // Current filter
    private val _currentFilter = MutableStateFlow(ReportStatus.PENDING)
    val currentFilter: StateFlow<ReportStatus> = _currentFilter

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Operation result
    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage

    init {
        loadReports(ReportStatus.PENDING)
    }

    /**
     * Load reports filtered by status.
     */
    fun loadReports(status: ReportStatus) {
        _currentFilter.value = status
        _isLoading.value = true

        viewModelScope.launch {
            val result = reportRepository.getReportsByStatus(status)
            result.onSuccess { reports ->
                _reports.value = reports
            }
            result.onFailure { e ->
                _operationMessage.value = "Error loading reports: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    /**
     * Select a report for the detail view.
     */
    fun selectReport(report: Report) {
        _selectedReport.value = report
    }

    /**
     * Clear the selected report (go back to list).
     */
    fun clearSelection() {
        _selectedReport.value = null
    }

    /**
     * Confirm a report as real road damage.
     */
    fun confirmReport(reportId: String, notes: String = "") {
        updateStatus(reportId, ReportStatus.CONFIRMED, notes, "Report confirmed")
    }

    /**
     * Reject a report as a false positive.
     */
    fun rejectReport(reportId: String, notes: String = "") {
        updateStatus(reportId, ReportStatus.REJECTED, notes, "Report rejected")
    }

    /**
     * Mark a report's damage as resolved/repaired.
     */
    fun resolveReport(reportId: String, notes: String = "") {
        updateStatus(reportId, ReportStatus.RESOLVED, notes, "Report marked as resolved")
    }

    /**
     * Update a report's status and refresh the list.
     */
    private fun updateStatus(
        reportId: String,
        newStatus: ReportStatus,
        notes: String,
        successMessage: String
    ) {
        val operatorId = auth.currentUser?.uid ?: return
        _isLoading.value = true

        viewModelScope.launch {
            val result = reportRepository.updateReportStatus(reportId, newStatus, operatorId, notes)
            result.onSuccess {
                _operationMessage.value = successMessage
                _selectedReport.value = null
                loadReports(_currentFilter.value)
            }
            result.onFailure { e ->
                _operationMessage.value = "Error: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    /**
     * Clear the operation message (after showing it to the user).
     */
    fun clearMessage() {
        _operationMessage.value = null
    }
}
