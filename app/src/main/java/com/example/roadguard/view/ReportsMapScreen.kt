package com.example.roadguard.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun ReportsMapScreen(reportsViewModel: ReportsViewModel = viewModel()) {
    val reports = reportsViewModel.reports.value
    val isLoading = reportsViewModel.isLoading.value
    val error = reportsViewModel.error.value

    // Default camera position (e.g., Rome)
    val initialPosition = LatLng(41.902782, 12.496366)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPosition, 10f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (error != null) {
            Text(
                text = "Error: $error",
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                reports.forEach { report ->
                    Marker(
                        state = MarkerState(position = LatLng(report.location.latitude, report.location.longitude)),
                        title = "Report",
                        snippet = "Severity: ${report.severity}"
                    )
                }
            }
        }
    }
}
