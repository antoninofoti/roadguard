package com.example.roadguard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Main Room database for RoadGuard local storage.
 * Stores detection history for on-device personalization of fusion weights.
 */
@Database(entities = [DetectionRecord::class], version = 1, exportSchema = false)
abstract class RoadGuardDatabase : RoomDatabase() {
    abstract fun detectionDao(): DetectionDao

    companion object {
        @Volatile
        private var INSTANCE: RoadGuardDatabase? = null

        fun getInstance(context: Context): RoadGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RoadGuardDatabase::class.java,
                    "roadguard_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
