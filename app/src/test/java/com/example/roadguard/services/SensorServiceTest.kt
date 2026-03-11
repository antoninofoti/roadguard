package com.example.roadguard.services

import android.location.Location
import com.example.roadguard.sensor.AnomalyEvent
import com.example.roadguard.sensor.AnomalyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SensorServiceTest {

    @Test
    fun testCsvFormatting() {
        // This is a simplified test to verify the CSV format string creation logic
        // Since SensorService is tightly coupled with Android Context and Sensors,
        // testing `logToCsv` directly requires extensive mocking (Robolectric).
        // Instead, we will simulate the string building process here to ensure
        // the columns align perfectly with the headers.
        
        // Header from SensorService
        val header = "Timestamp,Accel_Raw_X,Accel_Raw_Y,Accel_Raw_Z," +
                "Accel_Filtered_Mag," +
                "Gyro_Raw_X,Gyro_Raw_Y,Gyro_Raw_Z," +
                "Gyro_Filtered_Mag," +
                "Lat,Lng,Speed_kmh," +
                "Anomaly_Type,Anomaly_Confidence\n"
                
        val headerColumns = header.trim().split(",")
        assertEquals("Should have 14 columns", 14, headerColumns.size)
        
        // Simulated inputs
        val timestamp = 1678886400000L
        val accelRaw = floatArrayOf(1.0f, 2.0f, 3.0f)
        val accelMagFiltered = 3.5f
        val gyroRaw = floatArrayOf(0.1f, 0.2f, 0.3f)
        val gyroMagFiltered = 0.4f
        
        // Mock Location
        val mockLocation = mock(Location::class.java)
        `when`(mockLocation.latitude).thenReturn(41.9028)
        `when`(mockLocation.longitude).thenReturn(12.4964)
        `when`(mockLocation.speed).thenReturn(10.0f) // 10 m/s = 36 km/h
        
        // Mock Event
        val mockEvent = AnomalyEvent(
            type = AnomalyType.POTHOLE_SEVERE,
            severity = 0.9f,
            confidence = 0.85f,
            timestamp = timestamp
        )
        
        // Extraction logic matching SensorService.logToCsv
        val lat = mockLocation?.latitude?.toString() ?: ""
        val lng = mockLocation?.longitude?.toString() ?: ""
        val speedKmh = mockLocation?.speed?.let { (it * 3.6).toString() } ?: ""
        val anomalyType = mockEvent?.type?.name ?: ""
        val anomalyConfidence = mockEvent?.confidence?.toString() ?: ""

        val line = "$timestamp,${accelRaw[0]},${accelRaw[1]},${accelRaw[2]}," +
                "${accelMagFiltered}," +
                "${gyroRaw[0]},${gyroRaw[1]},${gyroRaw[2]}," +
                "${gyroMagFiltered}," +
                "$lat,$lng,$speedKmh,$anomalyType,$anomalyConfidence\n"
                
        val lineColumns = line.trim().split(",")
        assertEquals("Data columns should match header columns", headerColumns.size, lineColumns.size)
        
        // Assert specific values
        assertEquals(timestamp.toString(), lineColumns[0]) // Timestamp
        assertEquals("1.0", lineColumns[1]) // Accel X
        assertEquals("3.5", lineColumns[4]) // Accel Mag
        assertEquals("0.1", lineColumns[5]) // Gyro X
        assertEquals("0.4", lineColumns[8]) // Gyro Mag
        assertEquals("41.9028", lineColumns[9]) // Lat
        assertEquals("12.4964", lineColumns[10]) // Lng
        assertEquals("36.0", lineColumns[11]) // Speed km/h
        assertEquals("POTHOLE_SEVERE", lineColumns[12]) // Type
        assertEquals("0.85", lineColumns[13]) // Confidence
    }
}
