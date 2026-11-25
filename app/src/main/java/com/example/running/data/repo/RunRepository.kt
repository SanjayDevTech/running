package com.example.running.data.repo

import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.ActivityType
import com.example.running.core.model.AnalysisInput
import com.example.running.core.model.BestEffortWithRank
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.PersonalBests
import com.example.running.core.model.Run
import com.example.running.core.model.RunAnalysis
import com.example.running.core.model.RunGraph
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.Segment
import com.example.running.core.model.SensorSample
import com.example.running.core.model.Split
import com.example.running.core.model.TrackPoint
import kotlinx.coroutines.flow.Flow

/** The only way tracks read or write runs. Multi-table writes are transactional. */
interface RunRepository {
    // ---- Recording (A) ----

    /** Name placeholder "Run", type RUN, status ACTIVE. */
    suspend fun createActiveRun(
        id: String,
        startedAtMillis: Long,
        zoneId: String,
        engineMode: LocationEngineMode,
        startEngine: LocationEngine,
        devDualEngine: Boolean,
    ): Run

    /** Kind must be RAW; A batches (~every 5 s). */
    suspend fun appendRawPoints(points: List<TrackPoint>)
    suspend fun appendSensorSamples(runId: String, samples: List<SensorSample>)

    /** One recording flush: both batches in a single transaction (all or nothing, one commit). */
    suspend fun appendRecorded(runId: String, points: List<TrackPoint>, samples: List<SensorSample>) {
        appendRawPoints(points)
        appendSensorSamples(runId, samples)
    }
    suspend fun startPause(runId: String, atMillis: Long)

    /** Closes the open pause. */
    suspend fun endPause(runId: String, atMillis: Long)
    suspend fun markEngineFallback(runId: String)
    suspend fun findActiveRun(): Run?

    /** Primary RAW points, pauses (an open one is closed at [endedAtMillis]) and sensor samples. */
    suspend fun loadAnalysisInput(runId: String, endedAtMillis: Long, profile: RunnerProfile): AnalysisInput

    /**
     * Finishes a run: replaces FILTERED points, splits, segments, best efforts; writes stats;
     * status = FINISHED; ended_at; analysis_version. Keeps name if nameIsCustom, keeps type if
     * typeIsManual, otherwise applies analysis.suggestedName / suggestedType.
     */
    suspend fun saveAnalysis(runId: String, endedAtMillis: Long, analysis: RunAnalysis)

    // ---- History / detail (D) ----

    /** FINISHED, newest first. */
    fun observeRuns(filter: ActivityFilter): Flow<List<Run>>
    fun observeRun(runId: String): Flow<Run?>
    suspend fun getRun(runId: String): Run?
    suspend fun getFilteredPoints(runId: String): List<TrackPoint>

    /** All engines (dev compare). */
    suspend fun getRawPoints(runId: String): List<TrackPoint>
    fun observeSplits(runId: String): Flow<List<Split>>
    fun observeSegments(runId: String): Flow<List<Segment>>
    fun observeBestEfforts(runId: String): Flow<List<BestEffortWithRank>>
    suspend fun rename(runId: String, name: String)

    /** Also re-applies the auto-name if !nameIsCustom (uses the RunNamer passed in the constructor). */
    suspend fun setType(runId: String, type: ActivityType)
    suspend fun deleteRun(runId: String)

    // ---- Progress (E) ----

    /** FINISHED runs starting in [fromMillis, toMillis), oldest first. */
    fun observeRunsBetween(fromMillis: Long, toMillis: Long, filter: ActivityFilter): Flow<List<Run>>
    fun observePersonalBests(filter: ActivityFilter): Flow<PersonalBests>

    // ---- Backup (F) ----

    suspend fun getAllRunIds(): List<String>
    suspend fun getRunGraph(runId: String): RunGraph?

    /** Inserts the whole graph in one transaction. Returns false (and writes nothing) if the id exists. */
    suspend fun importRunGraph(graph: RunGraph): Boolean
}
