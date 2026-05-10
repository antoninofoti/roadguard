package com.example.roadguard

import android.app.Application
import android.util.Log
import com.example.roadguard.data.local.RoadGuardDatabase
import com.example.roadguard.ml.FederatedFusionManager
import com.example.roadguard.network.FirebaseFLCoordinator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RoadGuardApp : Application() {

    companion object {
        private const val TAG = "RoadGuardApp"
    }

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
        configureFirebaseBackends()
        // Initialize coordinator linkage
        fusionManager.setFLCoordinator(flCoordinator)
    }

    private fun configureFirebaseBackends() {
        if (!BuildConfig.USE_FIREBASE_EMULATORS) return

        val host = BuildConfig.FIREBASE_EMULATOR_HOST
        FirebaseFirestore.getInstance().useEmulator(host, BuildConfig.FIRESTORE_EMULATOR_PORT)
        FirebaseAuth.getInstance().useEmulator(host, BuildConfig.AUTH_EMULATOR_PORT)
        FirebaseStorage.getInstance().useEmulator(host, BuildConfig.STORAGE_EMULATOR_PORT)

        Log.i(
            TAG,
            "Firebase emulators enabled: host=$host firestore=${BuildConfig.FIRESTORE_EMULATOR_PORT} auth=${BuildConfig.AUTH_EMULATOR_PORT} storage=${BuildConfig.STORAGE_EMULATOR_PORT}"
        )
    }
}
