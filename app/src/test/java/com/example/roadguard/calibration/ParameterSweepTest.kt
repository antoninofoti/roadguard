package com.example.roadguard.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ParameterSweep] and [GroundTruthEntry].
 *
 * Uses synthetic sensor data with known anomaly positions
 * to verify the sweep pipeline produces correct metrics.
 */
class ParameterSweepTest {

    private val sweep = ParameterSweep()

    // ── Ground Truth Parsing ──

    @Test
    fun `parse valid ground truth CSV`() {
        val csv = """
            lat,lng,type,radius_m,description
            41.8902,12.4922,pothole,15,Via Tiburtina
            41.8910,12.4930,bump,10,Near metro
        """.trimIndent()

        val entries = GroundTruthEntry.fromCsv(csv)
        assertEquals(2, entries.size)
        assertEquals("pothole", entries[0].type)
        assertEquals(41.8902, entries[0].lat, 0.0001)
        assertEquals(12.4922, entries[0].lng, 0.0001)
        assertEquals(15.0, entries[0].radiusMeters, 0.1)
        assertEquals("Via Tiburtina", entries[0].description)
    }

    @Test
    fun `parse CSV with minimal columns`() {
        val csv = """
            lat,lng,type
            41.89,12.49,pothole
        """.trimIndent()

        val entries = GroundTruthEntry.fromCsv(csv)
        assertEquals(1, entries.size)
        assertEquals(15.0, entries[0].radiusMeters, 0.1) // Default radius
    }

    @Test
    fun `parse empty CSV returns empty list`() {
        val csv = "lat,lng,type"
        assertEquals(0, GroundTruthEntry.fromCsv(csv).size)
    }

    @Test
    fun `parse CSV with malformed lines skips them`() {
        val csv = """
            lat,lng,type
            not_a_number,12.49,pothole
            41.89,12.49,bump
        """.trimIndent()

        val entries = GroundTruthEntry.fromCsv(csv)
        assertEquals(1, entries.size)
        assertEquals("bump", entries[0].type)
    }

    // ── CSV Sensor Reading Parsing ──

    @Test
    fun `parse sensor CSV with valid data`() {
        val csv = """
            Timestamp,Accel_Raw_X,Accel_Raw_Y,Accel_Raw_Z,Accel_Filtered_Mag,Gyro_Raw_X,Gyro_Raw_Y,Gyro_Raw_Z,Gyro_Filtered_Mag,Lat,Lng,Speed_kmh,Anomaly_Type,Anomaly_Confidence
            1000000,0.1,0.2,9.8,9.81,0.01,0.02,0.03,0.04,41.89,12.49,30,,
        """.trimIndent()

        val readings = sweep.parseCsv(csv)
        assertEquals(1, readings.size)
        assertEquals(1000000L, readings[0].timestamp)
        assertEquals(0.1f, readings[0].accelX, 0.01f)
        assertEquals(9.8f, readings[0].accelZ, 0.01f)
        assertEquals(41.89, readings[0].lat!!, 0.01)
        assertEquals(12.49, readings[0].lng!!, 0.01)
    }

    @Test
    fun `parse sensor CSV without GPS returns null lat lng`() {
        val csv = """
            Timestamp,Accel_Raw_X,Accel_Raw_Y,Accel_Raw_Z,Accel_Filtered_Mag,Gyro_Raw_X,Gyro_Raw_Y,Gyro_Raw_Z,Gyro_Filtered_Mag,Lat,Lng,Speed_kmh,Anomaly_Type,Anomaly_Confidence
            1000000,0.1,0.2,9.8,9.81,0.01,0.02,0.03,0.04,,,,, 
        """.trimIndent()

        val readings = sweep.parseCsv(csv)
        assertEquals(1, readings.size)
        assertEquals(null, readings[0].lat)
        assertEquals(null, readings[0].lng)
    }

    // ── Haversine Distance ──

    @Test
    fun `haversine distance between same point is zero`() {
        val dist = sweep.haversineDistance(41.89, 12.49, 41.89, 12.49)
        assertEquals(0.0, dist, 0.1)
    }

    @Test
    fun `haversine distance between close points is reasonable`() {
        // ~10m apart in latitude
        val dist = sweep.haversineDistance(41.89000, 12.49, 41.89009, 12.49)
        assertTrue("Distance should be ~10m but was $dist", dist > 5 && dist < 15)
    }

    // ── Sweep with Synthetic Data ──

    @Test
    fun `sweep with single combination computes correct metrics`() {
        // Create synthetic data: a pothole at position (41.89, 12.49)
        // with a huge acceleration spike
        val readings = mutableListOf<CsvSensorReading>()

        // 100 normal readings
        for (i in 0 until 100) {
            readings.add(CsvSensorReading(
                timestamp = (i * 20_000_000).toLong(), // 20ms apart = 50Hz
                accelX = 0.1f, accelY = 0.1f, accelZ = 9.8f,
                gyroX = 0.01f, gyroY = 0.01f, gyroZ = 0.01f,
                lat = 41.89, lng = 12.49
            ))
        }
        // Big spike (simulated pothole)
        readings.add(CsvSensorReading(
            timestamp = 2_000_000_000L,
            accelX = 5.0f, accelY = 3.0f, accelZ = 20.0f,
            gyroX = 2.0f, gyroY = 1.5f, gyroZ = 1.0f,
            lat = 41.89, lng = 12.49
        ))

        val groundTruth = listOf(
            GroundTruthEntry(lat = 41.89, lng = 12.49, type = "pothole")
        )

        val config = SweepConfig(
            kalmanQ = listOf(0.01f),
            kalmanR = listOf(0.5f),
            accelThresholds = listOf(2.5f),
            gyroThresholds = listOf(2.0f)
        )

        val results = sweep.runSweep(readings, groundTruth, config)
        assertEquals(1, results.size)

        val result = results[0]
        assertEquals(0.01f, result.q, 0.001f)
        assertEquals(0.5f, result.r, 0.001f)
        assertEquals(2.5f, result.accelThreshold, 0.01f)
        assertEquals(2.0f, result.gyroThreshold, 0.01f)

        // Metrics should be valid (0-1 range)
        assertTrue("Precision should be in [0,1]", result.precision in 0f..1f)
        assertTrue("Recall should be in [0,1]", result.recall in 0f..1f)
        assertTrue("F1 should be in [0,1]", result.f1 in 0f..1f)
    }

    @Test
    fun `sweep with no ground truth gives zero recall`() {
        val readings = listOf(CsvSensorReading(
            timestamp = 0L,
            accelX = 0.1f, accelY = 0.1f, accelZ = 9.8f,
            gyroX = 0.01f, gyroY = 0.01f, gyroZ = 0.01f,
            lat = 41.89, lng = 12.49
        ))

        val config = SweepConfig(
            kalmanQ = listOf(0.01f),
            kalmanR = listOf(0.5f),
            accelThresholds = listOf(2.5f),
            gyroThresholds = listOf(2.0f)
        )

        val results = sweep.runSweep(readings, emptyList(), config)
        assertEquals(1, results.size)
        // No ground truth → FN = 0, but also no TP → recall is undefined (0)
        assertEquals(0f, results[0].recall, 0.001f)
    }

    @Test
    fun `sweep config total combinations is correct`() {
        val config = SweepConfig(
            kalmanQ = listOf(0.01f, 0.1f),       // 2
            kalmanR = listOf(0.5f, 1.0f),         // 2
            accelThresholds = listOf(2.0f, 2.5f), // 2
            gyroThresholds = listOf(2.0f)          // 1
        )
        assertEquals(8, config.totalCombinations)
    }

    @Test
    fun `sweep results are sorted by F1 descending`() {
        val readings = mutableListOf<CsvSensorReading>()
        for (i in 0 until 100) {
            readings.add(CsvSensorReading(
                timestamp = (i * 20_000_000).toLong(),
                accelX = 0.1f, accelY = 0.1f, accelZ = 9.8f,
                gyroX = 0.01f, gyroY = 0.01f, gyroZ = 0.01f,
                lat = 41.89, lng = 12.49
            ))
        }

        val groundTruth = listOf(
            GroundTruthEntry(lat = 41.89, lng = 12.49, type = "pothole")
        )

        val config = SweepConfig(
            kalmanQ = listOf(0.01f, 0.1f),
            kalmanR = listOf(0.5f, 1.0f),
            accelThresholds = listOf(2.0f, 3.0f),
            gyroThresholds = listOf(2.0f)
        )

        val results = sweep.runSweep(readings, groundTruth, config)

        for (i in 1 until results.size) {
            assertTrue("Results should be sorted by F1 descending",
                results[i].f1 <= results[i - 1].f1)
        }
    }
}
