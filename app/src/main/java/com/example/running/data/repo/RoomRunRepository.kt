package com.example.running.data.repo

import androidx.room.withTransaction
import com.example.running.core.contracts.RunNamer
import com.example.running.core.model.ActivityFilter
import com.example.running.core.model.ActivityType
import com.example.running.core.model.AnalysisInput
import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.BestEffortWithRank
import com.example.running.core.model.EffortRecord
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.PauseInterval
import com.example.running.core.model.PersonalBests
import com.example.running.core.model.PointKind
import com.example.running.core.model.Run
import com.example.running.core.model.RunAnalysis
import com.example.running.core.model.RunGraph
import com.example.running.core.model.RunRecord
import com.example.running.core.model.RunStats
import com.example.running.core.model.RunStatus
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.Segment
import com.example.running.core.model.SensorSample
import com.example.running.core.model.Split
import com.example.running.core.model.TrackPoint
import com.example.running.core.util.AppDispatchers
import com.example.running.core.util.Clock
import com.example.running.data.db.RunningDatabase
import com.example.running.data.db.entities.PauseEntity
import com.example.running.data.db.entities.RunEntity
import com.example.running.data.db.toDomain
import com.example.running.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** [runNamer] is a provider so the repository can be built before AnalysisModule. */
class RoomRunRepository(
    private val db: RunningDatabase,
    private val clock: Clock,
    @Suppress("unused") private val dispatchers: AppDispatchers,
    private val runNamer: () -> RunNamer,
) : RunRepository {

    private val runs = db.runDao()
    private val points = db.trackPointDao()
    private val parts = db.runPartsDao()
    private val samples = db.sensorSampleDao()

    // ---------------- Recording ----------------

    override suspend fun createActiveRun(
        id: String,
        startedAtMillis: Long,
        zoneId: String,
        engineMode: LocationEngineMode,
        startEngine: LocationEngine,
        devDualEngine: Boolean,
    ): Run {
        val now = clock.nowMillis()
        val run = Run(
            id = id, name = "Run", nameIsCustom = false, type = ActivityType.RUN, typeIsManual = false,
            status = RunStatus.ACTIVE, startedAtMillis = startedAtMillis, endedAtMillis = null, zoneId = zoneId,
            engineMode = engineMode, startEngine = startEngine, engineFallback = false,
            devDualEngine = devDualEngine, stats = RunStats(), analysisVersion = 0,
            createdAtMillis = now, updatedAtMillis = now,
        )
        runs.insert(run.toEntity())
        return run
    }

    override suspend fun appendRawPoints(points: List<TrackPoint>) {
        if (points.isEmpty()) return
        require(points.all { it.kind == PointKind.RAW }) { "appendRawPoints takes RAW points only" }
        this.points.insertAll(points.map { it.toEntity() })
    }

    override suspend fun appendSensorSamples(runId: String, samples: List<SensorSample>) {
        if (samples.isEmpty()) return
        this.samples.insertAll(samples.map { it.toEntity(runId) })
    }

    override suspend fun startPause(runId: String, atMillis: Long) {
        db.withTransaction {
            // Never two open pauses: a repeated pause() is a no-op.
            if (parts.pauses(runId).none { it.endMillis == null }) {
                parts.insertPause(PauseEntity(runId = runId, startMillis = atMillis, endMillis = null))
            }
        }
    }

    override suspend fun endPause(runId: String, atMillis: Long) = parts.closeOpenPause(runId, atMillis)

    override suspend fun markEngineFallback(runId: String) {
        db.withTransaction {
            val run = runs.get(runId) ?: return@withTransaction
            if (!run.engineFallback) runs.update(run.copy(engineFallback = true, updatedAtMillis = clock.nowMillis()))
        }
    }

    override suspend fun findActiveRun(): Run? = runs.findActive()?.toDomain()

    override suspend fun loadAnalysisInput(runId: String, endedAtMillis: Long, profile: RunnerProfile): AnalysisInput =
        db.withTransaction {
            val run = requireNotNull(runs.get(runId)) { "No run $runId" }
            AnalysisInput(
                runId = runId,
                startedAtMillis = run.startedAtMillis,
                endedAtMillis = endedAtMillis,
                zoneId = run.zoneId,
                rawPoints = points.primaryRaw(runId).map { it.toDomain() },
                pauses = parts.pauses(runId).map { PauseInterval(it.startMillis, it.endMillis ?: endedAtMillis) },
                sensorSamples = samples.forRun(runId).map { it.toDomain() },
                profile = profile,
                manualType = if (run.typeIsManual) run.type else null,
            )
        }

    override suspend fun saveAnalysis(runId: String, endedAtMillis: Long, analysis: RunAnalysis) {
        db.withTransaction {
            val run = requireNotNull(runs.get(runId)) { "No run $runId" }
            points.deleteKind(runId, PointKind.FILTERED)
            points.insertAll(analysis.filteredPoints.map { it.copy(runId = runId, kind = PointKind.FILTERED).toEntity() })
            parts.deleteSplits(runId)
            parts.insertSplits(analysis.splits.map { it.toEntity(runId) })
            parts.deleteSegments(runId)
            parts.insertSegments(analysis.segments.map { it.toEntity(runId) })
            parts.deleteBestEfforts(runId)
            parts.insertBestEfforts(analysis.bestEfforts.map { it.toEntity(runId) })
            val updated = run.toDomain().copy(
                name = if (run.nameIsCustom) run.name else analysis.suggestedName,
                type = if (run.typeIsManual) run.type else analysis.suggestedType,
                status = RunStatus.FINISHED,
                endedAtMillis = endedAtMillis,
                stats = analysis.stats,
                analysisVersion = analysis.analysisVersion,
                updatedAtMillis = clock.nowMillis(),
            )
            runs.update(updated.toEntity())
        }
    }

    // ---------------- History / detail ----------------

    override fun observeRuns(filter: ActivityFilter): Flow<List<Run>> =
        runs.observeFinished(filter.type).map { list -> list.map(RunEntity::toDomain) }

    override fun observeRun(runId: String): Flow<Run?> = runs.observe(runId).map { it?.toDomain() }

    override suspend fun getRun(runId: String): Run? = runs.get(runId)?.toDomain()

    override suspend fun getFilteredPoints(runId: String): List<TrackPoint> =
        points.get(runId, PointKind.FILTERED).map { it.toDomain() }

    override suspend fun getRawPoints(runId: String): List<TrackPoint> =
        points.get(runId, PointKind.RAW).map { it.toDomain() }

    override fun observeSplits(runId: String): Flow<List<Split>> =
        parts.observeSplits(runId).map { list -> list.map { it.toDomain() } }

    override fun observeSegments(runId: String): Flow<List<Segment>> =
        parts.observeSegments(runId).map { list -> list.map { it.toDomain() } }

    override fun observeBestEfforts(runId: String): Flow<List<BestEffortWithRank>> =
        parts.observeRankedEfforts(runId).map { rows ->
            rows.map { BestEffortWithRank(it.effort.toDomain(), it.rank) }.sortedBy { it.effort.distance.ordinal }
        }

    override suspend fun rename(runId: String, name: String) = runs.rename(runId, name.trim(), clock.nowMillis())

    override suspend fun setType(runId: String, type: ActivityType) {
        db.withTransaction {
            val now = clock.nowMillis()
            runs.setType(runId, type, now)
            val run = runs.get(runId) ?: return@withTransaction
            if (!run.nameIsCustom) {
                runs.update(run.copy(name = runNamer().name(run.startedAtMillis, run.zoneId, type), updatedAtMillis = now))
            }
        }
    }

    override suspend fun deleteRun(runId: String) = runs.delete(runId)

    // ---------------- Progress ----------------

    override fun observeRunsBetween(fromMillis: Long, toMillis: Long, filter: ActivityFilter): Flow<List<Run>> =
        runs.observeFinishedBetween(fromMillis, toMillis, filter.type).map { list -> list.map(RunEntity::toDomain) }

    override fun observePersonalBests(filter: ActivityFilter): Flow<PersonalBests> {
        val type = filter.type
        val effortFlows: List<Flow<Pair<BestEffortDistance, EffortRecord?>>> = BestEffortDistance.entries.map { d ->
            parts.observeBest(d, type).map { row ->
                d to row?.let { EffortRecord(it.effort.runId, it.runName, it.runStartedAtMillis, it.effort.toDomain()) }
            }
        }
        val efforts = combine(effortFlows) { pairs -> pairs.toMap() }
        return combine(efforts, runs.observeLongestDistance(type), runs.observeLongestDuration(type)) { e, dist, dur ->
            PersonalBests(
                efforts = e,
                longestDistance = dist?.toRunRecord(),
                longestDuration = dur?.toRunRecord(),
            )
        }.distinctUntilChanged()
    }

    private fun RunEntity.toRunRecord() = RunRecord(id, name, startedAtMillis, distanceM, movingMillis)

    // ---------------- Backup ----------------

    override suspend fun getAllRunIds(): List<String> = runs.allIds()

    override suspend fun getRunGraph(runId: String): RunGraph? = db.withTransaction {
        val run = runs.get(runId) ?: return@withTransaction null
        RunGraph(
            run = run.toDomain(),
            points = points.all(runId).map { it.toDomain() },
            pauses = parts.pauses(runId).map { it.toDomain() },
            splits = parts.splits(runId).map { it.toDomain() },
            segments = parts.segments(runId).map { it.toDomain() },
            bestEfforts = parts.bestEfforts(runId).map { it.toDomain() },
            sensorSamples = samples.forRun(runId).map { it.toDomain() },
        )
    }

    override suspend fun importRunGraph(graph: RunGraph): Boolean = db.withTransaction {
        val id = graph.run.id
        if (runs.get(id) != null) return@withTransaction false
        runs.insert(graph.run.toEntity())
        points.insertAll(graph.points.map { it.copy(runId = id).toEntity() })
        parts.insertPauses(graph.pauses.map { it.toEntity(id) })
        parts.insertSplits(graph.splits.map { it.toEntity(id) })
        parts.insertSegments(graph.segments.map { it.toEntity(id) })
        parts.insertBestEfforts(graph.bestEfforts.map { it.toEntity(id) })
        samples.insertAll(graph.sensorSamples.map { it.toEntity(id) })
        true
    }
}
