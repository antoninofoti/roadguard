package com.example.roadguard.view

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.roadguard.model.Report
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReportItem(report: Report) {
    val formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        .format(Date(report.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(report.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Report Image",
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Date: $formattedDate", style = MaterialTheme.typography.bodyMedium)
                Text("Severity: ${report.severity}", style = MaterialTheme.typography.bodyMedium)
                Text("Location: (${String.format("%.4f", report.location.latitude)}, ${String.format("%.4f", report.location.longitude)})", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
