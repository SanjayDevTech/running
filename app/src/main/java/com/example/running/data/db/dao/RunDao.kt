package com.example.running.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.running.core.model.ActivityType
import com.example.running.data.db.entities.RunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Insert suspend fun insert(run: RunEntity)

    @Update suspend fun update(run: RunEntity)

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun get(id: String): RunEntity?

    @Query("SELECT * FROM runs WHERE id = :id")
    fun observe(id: String): Flow<RunEntity?>

    @Query("SELECT * FROM runs WHERE status = 'ACTIVE' ORDER BY started_at_millis DESC LIMIT 1")
    suspend fun findActive(): RunEntity?

    @Query("SELECT * FROM runs WHERE status = 'FINISHED' AND (:type IS NULL OR type = :type) ORDER BY started_at_millis DESC")
    fun observeFinished(type: ActivityType?): Flow<List<RunEntity>>

    @Query(
        "SELECT * FROM runs WHERE status = 'FINISHED' AND started_at_millis >= :from AND started_at_millis < :to " +
            "AND (:type IS NULL OR type = :type) ORDER BY started_at_millis"
    )
    fun observeFinishedBetween(from: Long, to: Long, type: ActivityType?): Flow<List<RunEntity>>

    @Query("SELECT id FROM runs")
    suspend fun allIds(): List<String>

    @Query("SELECT id FROM runs WHERE status = 'FINISHED' ORDER BY started_at_millis")
    suspend fun finishedIds(): List<String>

    @Query("UPDATE runs SET name = :name, name_is_custom = 1, updated_at_millis = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("UPDATE runs SET type = :type, type_is_manual = 1, updated_at_millis = :now WHERE id = :id")
    suspend fun setType(id: String, type: ActivityType, now: Long)

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM runs WHERE status = 'FINISHED' AND (:type IS NULL OR type = :type) ORDER BY distance_m DESC LIMIT 1")
    suspend fun longestDistance(type: ActivityType?): RunEntity?

    @Query("SELECT * FROM runs WHERE status = 'FINISHED' AND (:type IS NULL OR type = :type) ORDER BY moving_millis DESC LIMIT 1")
    suspend fun longestDuration(type: ActivityType?): RunEntity?

    /** Flow twins of the two above, for observePersonalBests. */
    @Query("SELECT * FROM runs WHERE status = 'FINISHED' AND (:type IS NULL OR type = :type) ORDER BY distance_m DESC LIMIT 1")
    fun observeLongestDistance(type: ActivityType?): Flow<RunEntity?>

    @Query("SELECT * FROM runs WHERE status = 'FINISHED' AND (:type IS NULL OR type = :type) ORDER BY moving_millis DESC LIMIT 1")
    fun observeLongestDuration(type: ActivityType?): Flow<RunEntity?>

    @Query("SELECT COUNT(*) FROM runs WHERE status = 'FINISHED'")
    fun observeFinishedCount(): Flow<Int>
}
