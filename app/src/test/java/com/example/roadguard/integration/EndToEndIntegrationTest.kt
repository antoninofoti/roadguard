package com.example.roadguard.integration

import com.example.roadguard.detection.FusionAction
import com.example.roadguard.detection.FusionResult
import com.example.roadguard.detection.FusionEngine
import com.example.roadguard.model.DetectionSource
import com.example.roadguard.sensor.AnomalyDetector
import com.example.roadguard.sensor.AnomalyType
import com.example.roadguard.sensor.KalmanFilter3D
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * End-to-End Integration Test.
 *
 * Simulates a full data pipeline:
 * Raw Sensor Data (Simulated) -> KalmanFilter3D -> AnomalyDetector -> FusionEngine -> Decision
 *
 * This proves that the individual components work correctly when connected together.
 */
class EndToEndIntegrationTest {

    @Test
    fun `full pipeline processes pothole event with dual confirmation`() {
        // 1. Initialize the pipeline components
        // Simulating the SensorService instance
        val accelFilter = KalmanFilter3D(q = 0.01f, r = 0.5f)
        val gyroFilter = KalmanFilter3D(q = 0.01f, r = 0.5f)
        
        val anomalyDetector = AnomalyDetector(
            windowSize = 50,
            accelStdThreshold = 2.5f,
            gyroStdThreshold = 2.0f,
            minSeverity = 0.1f
        )
        
        // Fusion engine with standard weights (CV=0.55, Sensor=0.30, Temporal=0.15)
        val fusionEngine = FusionEngine()
        
        // 2. Event tracking variables
        var potholeDetectedBySensor = false
        var finalFusedScore: Float = 0f
        var finalAction: FusionAction = FusionAction.DISCARD
        var finalSource: String = "NONE"
        
        // Time constant: 50Hz sampling = 20ms per sample
        val dtMillis = 20L
        // Base timestamp: Current time, aligned to ms
        var currentTimeMs = System.currentTimeMillis()
        
        // 3. Simulation Loop: 3 seconds (150 samples)
        // We inject a POTHOLE event at t=1.0s (sample 50)
        
        println("Starting Simulation: 3.0s duration, POTHOLE at 1.0s")
        
        for (i in 0 until 150) {
            val t = i * 0.02f // Time in seconds
            
            // --- A. Generate Synthetic Raw Sensor Data ---
            var rawAccelX = 0f
            var rawAccelY = 0f
            var rawAccelZ = 9.81f // Gravity
            var rawGyroX = 0f
            var rawGyroY = 0f
            var rawGyroZ = 0f
            
            // Inject Pothole at t=1.0s to 1.08s (4 samples)
            if (t >= 1.0f && t < 1.08f) {
                // Pothole signature: Sharp vertical spike + rotational snap
                rawAccelZ = 22.0f // > 2g impact
                rawGyroX = 2.5f   // Significant rotation
            } else {
                // Normal driving noise
                rawAccelZ += (Random.nextFloat() * 0.3f - 0.15f)
                rawGyroX += (Random.nextFloat() * 0.1f - 0.05f)
            }
            
            // --- B. Step 1: Kalman Filtering ---
            // Process raw data through independent filters
            val filteredAccelMag = accelFilter.updateAndGetMagnitude(rawAccelX, rawAccelY, rawAccelZ)
            val filteredGyroMag = gyroFilter.updateAndGetMagnitude(rawGyroX, rawGyroY, rawGyroZ)
            
            // --- C. Step 2: Anomaly Detection ---
            // Pass filtered magnitudes to detector
            // Note: AnomalyDetector uses System.nanoTime() internally for cooldown logic,
            // so we must pass a monotonic timestamp. We'll use our simulated time converted to nanos.
            val simulatedNanoTime = currentTimeMs * 1_000_000L
            
            val anomalyEvent = anomalyDetector.addReading(
                accelMagnitude = filteredAccelMag,
                gyroMagnitude = filteredGyroMag,
                timestamp = simulatedNanoTime
            )
            
            // --- D. Step 3: Fusion Engine (Sensor Side) ---
            if (anomalyEvent != null) {
                println("  [t=${"%.2f".format(t)}s] Sensor Anomaly Detected: ${anomalyEvent.type} (conf=${anomalyEvent.confidence})")
                potholeDetectedBySensor = true
                
                // Assert it identified a POTHOLE (high accel + high gyro)
                assertEquals("Should classify as POTHOLE", AnomalyType.POTHOLE, anomalyEvent.type)
                
                // Feed to Fusion Engine
                val result = fusionEngine.onSensorAnomaly(anomalyEvent)
                
                // Capture result state
                finalFusedScore = result.fusedScore
                finalAction = result.action
                finalSource = result.detectionSource
            }
            
            // --- E. Step 4: Computer Vision Detection Analysis ---
            // Simulate that the YOLO model runs asynchronously and reports a detection slightly after the impact
            // Let's say at t=1.04s (sample 52), the camera sees the pothole
            if (i == 52) { 
                println("  [t=${"%.2f".format(t)}s] CV Model reports: 'pothole' (0.95 conf)")
                
                val cvResult = fusionEngine.onCvDetection(
                    confidence = 0.95f, 
                    label = "pothole"
                )
                
                println("  --> Fusion Triggered: Score=${cvResult.fusedScore}, Action=${cvResult.action}")
                
                // This should be the definitive result
                finalFusedScore = cvResult.fusedScore
                finalAction = cvResult.action
                finalSource = cvResult.detectionSource
            }
            
            // Advance time
            currentTimeMs += dtMillis
        }
        
        // 4. Final Verification
        println("\nFinal Results Verification:")
        println("- Sensor Detection: $potholeDetectedBySensor")
        println("- Final Fused Score: $finalFusedScore")
        println("- Final Action: $finalAction")
        println("- Final Source: $finalSource")
        
        assertTrue("Sensor should have detected the physical impact", potholeDetectedBySensor)
        
        // Score calculation verification: 
        // 0.55 * 0.95 (CV) + 0.30 * 0.9 (Sensor) + 0.15 * 1.0 (Temporal) ≈ 0.5225 + 0.27 + 0.15 = 0.9425
        assertTrue("Fusion score should be high > 0.85", finalFusedScore > 0.85f)
        
        assertEquals("Action should be AUTO_REPORT", FusionAction.AUTO_REPORT, finalAction)
        assertEquals("Source should be DUAL_CONFIRMED", DetectionSource.DUAL_CONFIRMED.name, finalSource)
    }
}
