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
    fun insert(record: DetectionRecord): Long

    @Update
    fun update(record: DetectionRecord)

    @Query("SELECT * FROM detection_records WHERE id = :id")
    fun getById(id: Long): DetectionRecord?

    @Query("SELECT * FROM detection_records ORDER BY timestamp DESC LIMIT :n")
    fun getLastN(n: Int): List<DetectionRecord>

    @Query("SELECT * FROM detection_records WHERE groundTruthLabel != -1 ORDER BY timestamp DESC")
    fun getLabeled(): List<DetectionRecord>

    @Query("SELECT COUNT(*) FROM detection_records WHERE groundTruthLabel != -1")
    fun countLabeled(): Int

    @Query("SELECT COUNT(*) FROM detection_records WHERE groundTruthLabel != -1")
    fun countLabeledFlow(): kotlinx.coroutines.flow.Flow<Int>

    @Query("DELETE FROM detection_records WHERE timestamp < :timestamp")
    fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM detection_records WHERE id NOT IN (SELECT id FROM detection_records ORDER BY timestamp DESC LIMIT :maxSize)")
    fun trimHistory(maxSize: Int)
}
