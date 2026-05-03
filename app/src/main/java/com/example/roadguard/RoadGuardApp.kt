package com.example.roadguard

import android.app.Application
import com.example.roadguard.data.local.RoadGuardDatabase
import com.example.roadguard.ml.FederatedFusionManager
import com.example.roadguard.network.FirebaseFLCoordinator
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RoadGuardApp : Application() {

    // No need for DI framework for this thesis project, a simple singleton approach is fine
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    val database by lazy { RoadGuardDatabase.getInstance(this) }
    
    val fusionManager by lazy { 
        FederatedFusionManager(this, database.detectionDao(), applicationScope) 
    }
    
    val flCoordinator by lazy {
        FirebaseFLCoordinator(FirebaseFirestore.getInstance(), fusionManager, applicationScope)
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize coordinator linkage
        fusionManager.setFLCoordinator(flCoordinator)
    }
}
