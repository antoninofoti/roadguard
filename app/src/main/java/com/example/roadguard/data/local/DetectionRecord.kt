package com.example.roadguard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local record of a pothole detection event for on-device fusion weight optimization.
 * Part of the FedRoadGuard personalization layer (Thesis Chapter 4).
 */
@Entity(tableName = "detection_records")
data class DetectionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val cvConf: Float,        // Vision branch confidence [0,1]
    val imuConf: Float,       // IMU branch confidence {0.0, 1.0}
    val fusedScore: Float,    // Final fused score
    val predictedLabel: Int,  // 0=normal, 1=pothole (model prediction)
    val groundTruthLabel: Int // -1=unknown, 0=normal, 1=pothole (user feedback)
)
