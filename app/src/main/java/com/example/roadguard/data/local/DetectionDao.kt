package com.example.roadguard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object for local detection records.
 * Enables sliding-window history and personalization feedback loops.
 */
@Dao
interface DetectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DetectionRecord): Long

    @Update
    suspend fun update(record: DetectionRecord)

    @Query("SELECT * FROM detection_records WHERE id = :id")
    suspend fun getById(id: Long): DetectionRecord?

    @Query("SELECT * FROM detection_records ORDER BY timestamp DESC LIMIT :n")
    suspend fun getLastN(n: Int): List<DetectionRecord>

    @Query("SELECT * FROM detection_records WHERE groundTruthLabel != -1 ORDER BY timestamp DESC")
    suspend fun getLabeled(): List<DetectionRecord>

    @Query("SELECT COUNT(*) FROM detection_records WHERE groundTruthLabel != -1")
    suspend fun countLabeled(): Int

    @Query("SELECT COUNT(*) FROM detection_records WHERE groundTruthLabel != -1")
    fun countLabeledFlow(): kotlinx.coroutines.flow.Flow<Int>

    @Query("DELETE FROM detection_records WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM detection_records WHERE id NOT IN (SELECT id FROM detection_records ORDER BY timestamp DESC LIMIT :maxSize)")
    suspend fun trimHistory(maxSize: Int)
}
