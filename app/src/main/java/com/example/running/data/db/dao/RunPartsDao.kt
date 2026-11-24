package com.example.running.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.running.core.model.ActivityType
import com.example.running.core.model.BestEffortDistance
import com.example.running.data.db.entities.BestEffortEntity
import com.example.running.data.db.entities.BestEffortRunRow
import com.example.running.data.db.entities.PauseEntity
import com.example.running.data.db.entities.RankedEffortRow
import com.example.running.data.db.entities.SegmentEntity
import com.example.running.data.db.entities.SplitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunPartsDao {
    // ---- pauses ----
    @Insert suspend fun insertPause(p: PauseEntity): Long

    @Query("UPDATE pauses SET end_millis = :end WHERE run_id = :runId AND end_millis IS NULL")
    suspend fun closeOpenPause(runId: String, end: Long)

    @Query("SELECT * FROM pauses WHERE run_id = :runId ORDER BY start_millis")
    suspend fun pauses(runId: String): List<PauseEntity>

    @Insert suspend fun insertPauses(p: List<PauseEntity>)

    // ---- derived parts: replaced wholesale by saveAnalysis ----
    @Query("DELETE FROM splits WHERE run_id = :runId")
    suspend fun deleteSplits(runId: String)

    @Query("DELETE FROM segments WHERE run_id = :runId")
    suspend fun deleteSegments(runId: String)

    @Query("DELETE FROM best_efforts WHERE run_id = :runId")
    suspend fun deleteBestEfforts(runId: String)

    @Insert suspend fun insertSplits(s: List<SplitEntity>)

    @Insert suspend fun insertSegments(s: List<SegmentEntity>)

    @Insert suspend fun insertBestEfforts(b: List<BestEffortEntity>)

    @Query("SELECT * FROM splits WHERE run_id = :runId ORDER BY split_index")
    fun observeSplits(runId: String): Flow<List<SplitEntity>>

    @Query("SELECT * FROM segments WHERE run_id = :runId ORDER BY start_millis")
    fun observeSegments(runId: String): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM splits WHERE run_id = :runId ORDER BY split_index")
    suspend fun splits(runId: String): List<SplitEntity>

    @Query("SELECT * FROM segments WHERE run_id = :runId ORDER BY start_millis")
    suspend fun segments(runId: String): List<SegmentEntity>

    @Query("SELECT * FROM best_efforts WHERE run_id = :runId")
    suspend fun bestEfforts(runId: String): List<BestEffortEntity>

    /** Each effort of one run with its all-time rank among FINISHED runs of the same type (1 = PB). */
    @Query(
        """
        SELECT be.*, (SELECT COUNT(*) FROM best_efforts o JOIN runs r2 ON r2.id = o.run_id
                      WHERE o.distance = be.distance AND r2.type = r.type AND r2.status = 'FINISHED'
                        AND o.duration_millis < be.duration_millis) + 1 AS effort_rank
        FROM best_efforts be JOIN runs r ON r.id = be.run_id WHERE be.run_id = :runId
        """
    )
    fun observeRankedEfforts(runId: String): Flow<List<RankedEffortRow>>

    /** Fastest effort for one distance (type null = all types). */
    @Query(
        """
        SELECT be.*, r.name AS run_name, r.started_at_millis AS run_started_at_millis
        FROM best_efforts be JOIN runs r ON r.id = be.run_id
        WHERE r.status = 'FINISHED' AND be.distance = :distance AND (:type IS NULL OR r.type = :type)
        ORDER BY be.duration_millis ASC, r.started_at_millis ASC LIMIT 1
        """
    )
    fun observeBest(distance: BestEffortDistance, type: ActivityType?): Flow<BestEffortRunRow?>
}
