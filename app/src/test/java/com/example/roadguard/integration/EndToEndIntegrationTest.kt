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
        val accelFilter = KalmanFilter3D(q = 0.01f, r = 0.5f)
        val gyroFilter = KalmanFilter3D(q = 0.01f, r = 0.5f)
        
        val anomalyDetector = AnomalyDetector(
            windowSize = 50,
            accelStdThreshold = 2.5f,
            gyroStdThreshold = 2.0f,
            minSeverity = 0.1f
        )
        
        val fusionEngine = FusionEngine()
        
        // 2. Event tracking
        var potholeDetectedBySensor = false
        var sensorAnomalyType: AnomalyType? = null
        
        // 3. Fill the window with baseline readings (50 samples of normal driving)
        //    Use System.nanoTime() so FusionEngine temporal matching can work.
        for (i in 0 until 50) {
            val baseAccelMag = accelFilter.updateAndGetMagnitude(
                0f + Random.nextFloat() * 0.1f - 0.05f,
                0f,
                9.81f + Random.nextFloat() * 0.1f - 0.05f
            )
            val baseGyroMag = gyroFilter.updateAndGetMagnitude(
                Random.nextFloat() * 0.02f - 0.01f,
                0f,
                0f
            )
            anomalyDetector.addReading(baseAccelMag, baseGyroMag, System.nanoTime())
            Thread.sleep(1) // Small delay for time progression
        }
        
        // 4. Inject pothole — 4 samples of extreme values
        for (i in 0 until 4) {
            val potholeAccelMag = accelFilter.updateAndGetMagnitude(0f, 0f, 45.0f)
            val potholeGyroMag = gyroFilter.updateAndGetMagnitude(15.0f, 0f, 0f)
            val event = anomalyDetector.addReading(potholeAccelMag, potholeGyroMag, System.nanoTime())
            
            if (event != null) {
                println("  Sensor Anomaly Detected: ${event.type} (conf=${event.confidence})")
                potholeDetectedBySensor = true
                sensorAnomalyType = event.type
                
                // Feed to Fusion Engine (sensor side)
                fusionEngine.onSensorAnomaly(event)
            }
        }
        
        // 5. Assert sensor detected a POTHOLE
        assertTrue("Sensor should have detected the physical impact", potholeDetectedBySensor)
        assertEquals("Should classify as POTHOLE", AnomalyType.POTHOLE, sensorAnomalyType)
        
        // 6. Simulate CV detection happening right after (within temporal window)
        println("  CV Model reports: 'pothole' (0.95 conf)")
        val cvResult = fusionEngine.onCvDetection(confidence = 0.95f, label = "pothole")
        
        println("  --> Fusion Triggered: Score=${cvResult.fusedScore}, Action=${cvResult.action}, Source=${cvResult.detectionSource}")
        
        // 7. Verify fusion results
        println("\nFinal Results Verification:")
        println("- Sensor Detection: $potholeDetectedBySensor")
        println("- Final Fused Score: ${cvResult.fusedScore}")
        println("- Final Action: ${cvResult.action}")
        println("- Final Source: ${cvResult.detectionSource}")
        
        // Score: 0.55 * 0.95 (CV) + 0.30 * 0.9 (Sensor) + 0.15 * 1.0 (Temporal) ≈ 0.9425
        assertTrue("Fusion score should be high > 0.85, was ${cvResult.fusedScore}", cvResult.fusedScore > 0.85f)
        assertEquals("Action should be AUTO_REPORT", FusionAction.AUTO_REPORT, cvResult.action)
        assertEquals("Source should be DUAL_CONFIRMED", DetectionSource.DUAL_CONFIRMED.name, cvResult.detectionSource)
    }
}

