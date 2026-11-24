package com.example.running.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.running.data.db.entities.SensorSampleEntity

@Dao
interface SensorSampleDao {
    @Insert suspend fun insertAll(samples: List<SensorSampleEntity>)

    @Query("SELECT * FROM sensor_samples WHERE run_id = :runId ORDER BY time_millis, id")
    suspend fun forRun(runId: String): List<SensorSampleEntity>
}
