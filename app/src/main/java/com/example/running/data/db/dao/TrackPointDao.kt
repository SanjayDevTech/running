package com.example.running.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.running.core.model.PointKind
import com.example.running.data.db.entities.TrackPointEntity

@Dao
interface TrackPointDao {
    @Insert suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE run_id = :runId AND kind = :kind ORDER BY time_millis, id")
    suspend fun get(runId: String, kind: PointKind): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE run_id = :runId AND kind = 'RAW' AND is_primary = 1 ORDER BY time_millis, id")
    suspend fun primaryRaw(runId: String): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE run_id = :runId ORDER BY kind, time_millis, id")
    suspend fun all(runId: String): List<TrackPointEntity>

    @Query("DELETE FROM track_points WHERE run_id = :runId AND kind = :kind")
    suspend fun deleteKind(runId: String, kind: PointKind)

    @Query("SELECT COUNT(*) FROM track_points WHERE run_id = :runId AND kind = 'RAW'")
    suspend fun rawCount(runId: String): Int
}
