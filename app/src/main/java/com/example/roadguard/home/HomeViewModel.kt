package com.example.roadguard.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roadguard.model.Report
import com.example.roadguard.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _reports = MutableStateFlow<List<Report>>(emptyList())
    val reports: StateFlow<List<Report>> = _reports

    private val _selectedReport = MutableStateFlow<Report?>(null)
    val selectedReport: StateFlow<Report?> = _selectedReport

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    init {
        fetchReports()
        fetchUser()
    }

    private fun fetchUser() {
        viewModelScope.launch {
            val userId = auth.currentUser?.uid
            if (userId != null) {
                firestore.collection("users").document(userId).get()
                    .addOnSuccessListener { document ->
                        if (document != null) {
                            _user.value = document.toObject<User>()
                        }
                    }
            }
        }
    }

    private fun fetchReports() {
        viewModelScope.launch {
            firestore.collection("reports")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        // Handle error
                        return@addSnapshotListener
                    }

                    snapshot?.let {
                        _reports.value = it.toObjects()
                    }
                }
        }
    }

    fun getReportById(reportId: String) {
        viewModelScope.launch {
            firestore.collection("reports").document(reportId).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        _selectedReport.value = document.toObject<Report>()
                    } else {
                        // Handle error
                    }
                }
                .addOnFailureListener {
                    // Handle error
                }
        }
    }

    fun updateReport(report: Report) {
        viewModelScope.launch {
            firestore.collection("reports").document(report.id).set(report)
        }
    }

    fun deleteReport(reportId: String) {
        viewModelScope.launch {
            firestore.collection("reports").document(reportId).delete()
        }
    }
}
