package com.example.roadguard.services

import com.example.roadguard.sensor.AnomalyEvent
import com.example.roadguard.sensor.AnomalyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for SensorService CSV formatting logic.
 *
 * Since SensorService is tightly coupled with Android Context and Sensors,
 * these tests verify the CSV column alignment and formatting logic
 * by simulating the string building process outside of the service.
 */
class SensorServiceTest {

    @Test
    fun testCsvHeaderColumnCount() {
        // Header from SensorService (Phase D: includes AmbientLight_lux, IsNight)
        val header = "Timestamp,Accel_Raw_X,Accel_Raw_Y,Accel_Raw_Z," +
                "Accel_Filtered_Mag," +
                "Gyro_Raw_X,Gyro_Raw_Y,Gyro_Raw_Z," +
                "Gyro_Filtered_Mag," +
                "Lat,Lng,Speed_kmh," +
                "AmbientLight_lux,IsNight," +
                "Anomaly_Type,Anomaly_Confidence\n"

        val headerColumns = header.trim().split(",")
        assertEquals("Should have 16 columns", 16, headerColumns.size)
    }

    @Test
    fun testCsvFormattingWithFullData() {
        // Simulated inputs
        val timestamp = 1678886400000L
        val accelRaw = floatArrayOf(1.0f, 2.0f, 3.0f)
        val accelMagFiltered = 3.5f
        val gyroRaw = floatArrayOf(0.1f, 0.2f, 0.3f)
        val gyroMagFiltered = 0.4f

        // Simulated location values
        val lat = "41.9028"
        val lng = "12.4964"
        val speedKmh = "36.0"

        // Simulated light context values
        val lightLux = "450.0"
        val isNight = "false"

        // Simulated Event
        val mockEvent = AnomalyEvent(
            type = AnomalyType.POTHOLE,
            severity = 0.9f,
            confidence = 0.85f,
            timestamp = timestamp,
            accelPeak = 15.0f,
            gyroPeak = 3.0f
        )

        val anomalyType = mockEvent.type.name
        val anomalyConfidence = mockEvent.confidence.toString()

        val line = "$timestamp,${accelRaw[0]},${accelRaw[1]},${accelRaw[2]}," +
                "${accelMagFiltered}," +
                "${gyroRaw[0]},${gyroRaw[1]},${gyroRaw[2]}," +
                "${gyroMagFiltered}," +
                "$lat,$lng,$speedKmh," +
                "$lightLux,$isNight," +
                "$anomalyType,$anomalyConfidence\n"

        val lineColumns = line.trim().split(",")
        assertEquals("Data columns should match header (16)", 16, lineColumns.size)

        // Assert specific values
        assertEquals(timestamp.toString(), lineColumns[0])   // Timestamp
        assertEquals("1.0", lineColumns[1])                  // Accel X
        assertEquals("3.5", lineColumns[4])                  // Accel Filtered Mag
        assertEquals("0.1", lineColumns[5])                  // Gyro X
        assertEquals("0.4", lineColumns[8])                  // Gyro Filtered Mag
        assertEquals("41.9028", lineColumns[9])              // Lat
        assertEquals("12.4964", lineColumns[10])             // Lng
        assertEquals("36.0", lineColumns[11])                // Speed km/h
        assertEquals("450.0", lineColumns[12])               // AmbientLight lux
        assertEquals("false", lineColumns[13])               // IsNight
        assertEquals("POTHOLE", lineColumns[14])             // Anomaly Type
        assertEquals("0.85", lineColumns[15])                // Confidence
    }

    @Test
    fun testCsvFormattingWithMissingData() {
        // When GPS and light sensor are unavailable
        val timestamp = 1678886400000L
        val accelRaw = floatArrayOf(0.1f, 0.2f, 9.8f)
        val accelMagFiltered = 9.81f
        val gyroRaw = floatArrayOf(0.01f, 0.02f, 0.03f)
        val gyroMagFiltered = 0.04f

        val lat = ""
        val lng = ""
        val speedKmh = ""
        val lightLux = ""     // No light sensor
        val isNight = "false"
        val anomalyType = ""
        val anomalyConfidence = ""

        val line = "$timestamp,${accelRaw[0]},${accelRaw[1]},${accelRaw[2]}," +
                "${accelMagFiltered}," +
                "${gyroRaw[0]},${gyroRaw[1]},${gyroRaw[2]}," +
                "${gyroMagFiltered}," +
                "$lat,$lng,$speedKmh," +
                "$lightLux,$isNight," +
                "$anomalyType,$anomalyConfidence\n"

        val lineColumns = line.trim().split(",")
        assertEquals("Data columns should still be 16 even with empty values", 16, lineColumns.size)
    }

    @Test
    fun testAnomalyEventCreation() {
        // Verify AnomalyEvent can be created with all fields
        val event = AnomalyEvent(
            type = AnomalyType.POTHOLE,
            severity = 0.7f,
            confidence = 0.9f,
            timestamp = System.nanoTime(),
            accelPeak = 12.0f,
            gyroPeak = 2.5f
        )

        assertTrue("Severity should be in [0,1]", event.severity in 0f..1f)
        assertTrue("Confidence should be in [0,1]", event.confidence in 0f..1f)
        assertTrue("Accel peak should be positive", event.accelPeak > 0f)
        assertTrue("Gyro peak should be positive", event.gyroPeak > 0f)
    }
}
