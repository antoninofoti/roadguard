package com.example.roadguard.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.roadguard.model.Report
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState

@Composable
fun ReportDetailScreen(
    navController: NavController,
    report: Report,
    homeViewModel: HomeViewModel = viewModel()
) {
    var severity by remember { mutableStateOf(report.severity.toString()) }
    val userState by homeViewModel.user.collectAsState()
    val user = userState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Report Details")
        AsyncImage(
            model = report.imageUrl,
            contentDescription = "Pothole image",
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentScale = ContentScale.Crop
        )
        TextField(
            value = severity,
            onValueChange = { severity = it },
            label = { Text("Severity") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = !(user?.role == "admin" || user?.uid == report.userId)
        )
        report.location?.let {
            GoogleMap(modifier = Modifier.weight(1f)) {
                Marker(
                    state = MarkerState(position = LatLng(it.latitude, it.longitude)),
                    title = "Pothole"
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (user?.role == "admin" || user?.uid == report.userId) {
                Button(onClick = {
                    val updatedReport = report.copy(severity = severity.toFloat())
                    homeViewModel.updateReport(updatedReport)
                    navController.popBackStack()
                }) {
                    Text("Update")
                }
                Button(onClick = {
                    homeViewModel.deleteReport(report.id)
                    navController.popBackStack()
                }) {
                    Text("Delete")
                }
            }
        }
    }
}
