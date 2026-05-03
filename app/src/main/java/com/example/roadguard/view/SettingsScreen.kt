package com.example.roadguard.view

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.roadguard.data.local.RoadGuardDatabase
import com.example.roadguard.ml.FederatedFusionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings Screen for FedRoadGuard.
 * Implements the personalization toggle (Prompt 7C Task 2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val db = remember { RoadGuardDatabase.getInstance(context) }

    val sharedPrefs = remember { context.getSharedPreferences("roadguard_fusion_prefs", 0) }
    var isEnabled by remember { 
        mutableStateOf(sharedPrefs.getBoolean("personalization_enabled", false)) 
    }
    
    var labeledCount by remember { mutableIntStateOf(0) }
    var localF1 by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            labeledCount = db.detectionDao().countLabeled()
            localF1 = sharedPrefs.getFloat("local_f1", 0f)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = "Federated Learning",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Personalized Detection (Beta)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Optimize fusion parameters based on your feedback",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (labeledCount >= FederatedFusionManager.MIN_SAMPLES_FOR_OPTIMIZATION) {
                                isEnabled = true
                                sharedPrefs.edit().putBoolean("personalization_enabled", true).apply()
                                val f1 = sharedPrefs.getFloat("local_f1", 0f)
                                Toast.makeText(context, "Personalization enabled (Local F1: %.2f)".format(f1), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "At least 20 feedbacks are required to activate", Toast.LENGTH_LONG).show()
                                isEnabled = false
                            }
                        } else {
                            isEnabled = false
                            sharedPrefs.edit().putBoolean("personalization_enabled", false).apply()
                        }
                    }
                )
            }

            if (labeledCount < FederatedFusionManager.MIN_SAMPLES_FOR_OPTIMIZATION) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Feedback progress: $labeledCount/${FederatedFusionManager.MIN_SAMPLES_FOR_OPTIMIZATION}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (localF1 > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Current Local F1-Score: %.4f".format(localF1),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Divider(modifier = Modifier.padding(vertical = 24.dp))
            
            Text(
                text = "Data Sovereignty",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your fusion weights and feedback history are stored locally on this device and are never transmitted to the central server.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
