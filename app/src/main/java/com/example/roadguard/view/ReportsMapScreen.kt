package com.example.roadguard.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roadguard.BuildConfig
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

@Composable
fun ReportsMapScreen(reportsViewModel: ReportsViewModel = viewModel()) {
    val reports = reportsViewModel.reports.value
    val isLoading = reportsViewModel.isLoading.value
    val error = reportsViewModel.error.value
    val reportsWithLocation = reports.count { it.location != null }
    val backend = if (BuildConfig.USE_FIREBASE_EMULATORS) "Emulator" else "Production"
    val firstLocation = reports.firstOrNull { it.location != null }?.location

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
                    report.location?.let {
                        Marker(
                            state = MarkerState(position = LatLng(it.latitude, it.longitude)),
                            title = "Report",
                            snippet = "Severity: ${report.severity}"
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color(0xCC111827))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Backend: $backend",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "Reports: ${reports.size} (with location: $reportsWithLocation)",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = firstLocation?.let {
                        String.format(Locale.US, "First marker: %.5f, %.5f", it.latitude, it.longitude)
                    } ?: "First marker: none",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (reportsWithLocation == 0) {
                Text(
                    text = "No reports with coordinates loaded.",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xCC000000))
                        .padding(10.dp),
                    color = Color.White
                )
            }
        }
    }
}
