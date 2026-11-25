package com.example.running.testing

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
import com.example.running.data.repo.RunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory RunRepository with the same semantics as RoomRunRepository (idempotent import, PB ranking). */
class FakeRunRepository(
    private val clock: FakeClock = FakeClock(),
    private val runNamer: RunNamer = object : RunNamer {
        override fun name(startedAtMillis: Long, zoneId: String, type: ActivityType) =
            if (type == ActivityType.WALK) "Walk" else "Run"
    },
) : RunRepository {
    val graphs = MutableStateFlow<Map<String, RunGraph>>(emptyMap())

    private fun edit(runId: String, block: (RunGraph) -> RunGraph) =
        graphs.update { m -> m[runId]?.let { m + (runId to block(it)) } ?: m }

    private fun finished(filter: ActivityFilter) = graphs.value.values
        .filter { it.run.status == RunStatus.FINISHED && (filter.type == null || it.run.type == filter.type) }

    override suspend fun createActiveRun(
        id: String, startedAtMillis: Long, zoneId: String, engineMode: LocationEngineMode,
        startEngine: LocationEngine, devDualEngine: Boolean,
    ): Run {
        val now = clock.nowMillis()
        val run = Run(
            id, "Run", false, ActivityType.RUN, false, RunStatus.ACTIVE, startedAtMillis, null, zoneId,
            engineMode, startEngine, false, devDualEngine, RunStats(), 0, now, now,
        )
        graphs.update { it + (id to RunGraph(run, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())) }
        return run
    }

    /** The next this-many append calls throw (a full disk or transient I/O error). */
    var failingAppends = 0

    private fun maybeFailAppend() {
        if (failingAppends > 0) {
            failingAppends--
            throw java.io.IOException("disk full")
        }
    }

    override suspend fun appendRawPoints(points: List<TrackPoint>) {
        maybeFailAppend()
        require(points.all { it.kind == PointKind.RAW })
        points.groupBy { it.runId }.forEach { (id, pts) -> edit(id) { it.copy(points = it.points + pts) } }
    }

    override suspend fun appendSensorSamples(runId: String, samples: List<SensorSample>) {
        maybeFailAppend()
        edit(runId) { it.copy(sensorSamples = it.sensorSamples + samples) }
    }

    /** Atomic like Room's transaction: a failure writes neither batch. */
    var recordedFlushes = 0
        private set

    override suspend fun appendRecorded(runId: String, points: List<TrackPoint>, samples: List<SensorSample>) {
        maybeFailAppend()
        require(points.all { it.kind == PointKind.RAW })
        recordedFlushes++
        edit(runId) { it.copy(points = it.points + points, sensorSamples = it.sensorSamples + samples) }
    }

    override suspend fun startPause(runId: String, atMillis: Long) = edit(runId) { g ->
        if (g.pauses.any { it.endMillis == null }) g else g.copy(pauses = g.pauses + PauseInterval(atMillis, null))
    }

    override suspend fun endPause(runId: String, atMillis: Long) = edit(runId) { g ->
        g.copy(pauses = g.pauses.map { if (it.endMillis == null) it.copy(endMillis = atMillis) else it })
    }

    override suspend fun markEngineFallback(runId: String) =
        edit(runId) { it.copy(run = it.run.copy(engineFallback = true, updatedAtMillis = clock.nowMillis())) }

    override suspend fun findActiveRun(): Run? =
        graphs.value.values.map { it.run }.filter { it.status == RunStatus.ACTIVE }.maxByOrNull { it.startedAtMillis }

    override suspend fun loadAnalysisInput(runId: String, endedAtMillis: Long, profile: RunnerProfile): AnalysisInput {
        val g = requireNotNull(graphs.value[runId])
        return AnalysisInput(
            runId, g.run.startedAtMillis, endedAtMillis, g.run.zoneId,
            g.points.filter { it.kind == PointKind.RAW && it.isPrimary }.sortedBy { it.timeMillis },
            g.pauses.sortedBy { it.startMillis }.map { PauseInterval(it.startMillis, it.endMillis ?: endedAtMillis) },
            g.sensorSamples.sortedBy { it.timeMillis }, profile,
            if (g.run.typeIsManual) g.run.type else null,
        )
    }

    override suspend fun saveAnalysis(runId: String, endedAtMillis: Long, analysis: RunAnalysis) = edit(runId) { g ->
        g.copy(
            run = g.run.copy(
                name = if (g.run.nameIsCustom) g.run.name else analysis.suggestedName,
                type = if (g.run.typeIsManual) g.run.type else analysis.suggestedType,
                status = RunStatus.FINISHED, endedAtMillis = endedAtMillis, stats = analysis.stats,
                analysisVersion = analysis.analysisVersion, updatedAtMillis = clock.nowMillis(),
            ),
            points = g.points.filter { it.kind == PointKind.RAW } +
                analysis.filteredPoints.map { it.copy(runId = runId, kind = PointKind.FILTERED) },
            splits = analysis.splits, segments = analysis.segments, bestEfforts = analysis.bestEfforts,
        )
    }

    override fun observeRuns(filter: ActivityFilter): Flow<List<Run>> = graphs.map {
        finished(filter).map { it.run }.sortedByDescending { it.startedAtMillis }
    }.distinctUntilChanged()

    override fun observeRun(runId: String): Flow<Run?> = graphs.map { it[runId]?.run }.distinctUntilChanged()
    override suspend fun getRun(runId: String): Run? = graphs.value[runId]?.run
    override suspend fun getFilteredPoints(runId: String): List<TrackPoint> =
        graphs.value[runId]?.points.orEmpty().filter { it.kind == PointKind.FILTERED }.sortedBy { it.timeMillis }
    override suspend fun getRawPoints(runId: String): List<TrackPoint> =
        graphs.value[runId]?.points.orEmpty().filter { it.kind == PointKind.RAW }.sortedBy { it.timeMillis }
    override fun observeSplits(runId: String): Flow<List<Split>> =
        graphs.map { it[runId]?.splits.orEmpty().sortedBy { s -> s.index } }.distinctUntilChanged()
    override fun observeSegments(runId: String): Flow<List<Segment>> =
        graphs.map { it[runId]?.segments.orEmpty().sortedBy { s -> s.startMillis } }.distinctUntilChanged()

    override fun observeBestEfforts(runId: String): Flow<List<BestEffortWithRank>> = graphs.map { m ->
        val g = m[runId] ?: return@map emptyList()
        g.bestEfforts.map { e ->
            val faster = m.values.filter { it.run.status == RunStatus.FINISHED && it.run.type == g.run.type }
                .flatMap { it.bestEfforts }
                .count { it.distance == e.distance && it.durationMillis < e.durationMillis }
            BestEffortWithRank(e, faster + 1)
        }.sortedBy { it.effort.distance.ordinal }
    }.distinctUntilChanged()

    override suspend fun rename(runId: String, name: String) =
        edit(runId) { it.copy(run = it.run.copy(name = name.trim(), nameIsCustom = true, updatedAtMillis = clock.nowMillis())) }

    override suspend fun setType(runId: String, type: ActivityType) = edit(runId) { g ->
        val r = g.run
        g.copy(
            run = r.copy(
                type = type, typeIsManual = true, updatedAtMillis = clock.nowMillis(),
                name = if (r.nameIsCustom) r.name else runNamer.name(r.startedAtMillis, r.zoneId, type),
            )
        )
    }

    override suspend fun deleteRun(runId: String) = graphs.update { it - runId }

    override fun observeRunsBetween(fromMillis: Long, toMillis: Long, filter: ActivityFilter): Flow<List<Run>> =
        graphs.map {
            finished(filter).map { it.run }.filter { it.startedAtMillis in fromMillis until toMillis }
                .sortedBy { it.startedAtMillis }
        }.distinctUntilChanged()

    override fun observePersonalBests(filter: ActivityFilter): Flow<PersonalBests> = graphs.map {
        val runs = finished(filter)
        PersonalBests(
            efforts = BestEffortDistance.entries.associateWith { d ->
                runs.flatMap { g -> g.bestEfforts.filter { it.distance == d }.map { g.run to it } }
                    .minWithOrNull(compareBy({ it.second.durationMillis }, { it.first.startedAtMillis }))
                    ?.let { (r, e) -> EffortRecord(r.id, r.name, r.startedAtMillis, e) }
            },
            longestDistance = runs.maxByOrNull { it.run.stats.distanceM }?.run?.toRecord(),
            longestDuration = runs.maxByOrNull { it.run.stats.movingMillis }?.run?.toRecord(),
        )
    }.distinctUntilChanged()

    private fun Run.toRecord() = RunRecord(id, name, startedAtMillis, stats.distanceM, stats.movingMillis)

    override suspend fun getAllRunIds(): List<String> = graphs.value.keys.toList()
    override suspend fun getRunGraph(runId: String): RunGraph? = graphs.value[runId]

    override suspend fun importRunGraph(graph: RunGraph): Boolean {
        if (graphs.value.containsKey(graph.run.id)) return false
        graphs.update { it + (graph.run.id to graph) }
        return true
    }
}
