package com.example.roadguard.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.roadguard.R
import com.example.roadguard.network.FirebaseFLCoordinator
import com.example.roadguard.ml.FederatedFusionManager
import com.example.roadguard.data.local.RoadGuardDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Federated Learning Status Card.
 * Shows real-time metrics and participation options (Prompt 9B).
 */
@Composable
fun FLStatusCard(
    coordinator: FirebaseFLCoordinator,
    fusionManager: FederatedFusionManager,
    db: RoadGuardDatabase
) {
    val context = LocalContext.current
    val currentRound by coordinator.currentRound.collectAsState()
    val labeledCount by db.detectionDao().countLabeledFlow().collectAsState(initial = 0)
    
    var localF1 by remember { mutableFloatStateOf(0f) }
    var isParticipating by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            localF1 = fusionManager.getLocalF1()
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(stringResource(R.string.fl_details_title)) },
            text = { 
                Text(stringResource(R.string.fl_details_text)) 
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) { Text("OK") }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.fl_network_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            MetricRow(stringResource(R.string.local_f1_label), "%.4f".format(localF1))
            MetricRow(stringResource(R.string.feedback_samples_label), "$labeledCount")
            
            val roundNum = currentRound?.getLong("round_number") ?: 0
            val participantCount = currentRound?.getLong("participant_count") ?: 0
            
            MetricRow(stringResource(R.string.current_round_label), stringResource(R.string.round_number_format, roundNum))
            MetricRow(stringResource(R.string.participants_label), stringResource(R.string.participants_format, participantCount))

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info, 
                    contentDescription = null, 
                    modifier = Modifier.size(16.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.personalization_active),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showDetails = true }) {
                    Text(stringResource(R.string.details_button))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        isParticipating = true
                        coordinator.participateInRound { success ->
                            isParticipating = false
                            if (success) {
                                Toast.makeText(context, context.getString(R.string.participation_success), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.participation_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isParticipating && currentRound != null
                ) {
                    if (isParticipating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text(stringResource(R.string.participate_button))
                    }
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
