package com.example.roadguard.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState

@Composable
fun HomeScreen(navController: NavController, homeViewModel: HomeViewModel = viewModel()) {
    val auth = FirebaseAuth.getInstance()
    val reports by homeViewModel.reports.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            GoogleMap(modifier = Modifier.fillMaxSize()) {
                reports.forEach { report ->
                    report.location?.let {
                        val color = getMarkerColor(report.severity)
                        Marker(
                            state = MarkerState(position = LatLng(it.latitude, it.longitude)),
                            title = "Pothole",
                            snippet = "Severity: ${report.severity}",
                            icon = BitmapDescriptorFactory.defaultMarker(color)
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { navController.navigate("camera") }) {
                Text("Report a Pothole")
            }
            Button(onClick = {
                auth.signOut()
                navController.navigate("auth") {
                    popUpTo("home") { inclusive = true }
                }
            }) {
                Text("Sign Out")
            }
        }
    }
}

private fun getMarkerColor(severity: Float): Float {
    return when {
        severity > 25 -> BitmapDescriptorFactory.HUE_RED
        severity > 15 -> BitmapDescriptorFactory.HUE_ORANGE
        else -> BitmapDescriptorFactory.HUE_YELLOW
    }
}
