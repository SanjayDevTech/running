package com.example.running.backup

import com.example.running.core.model.ActivityType
import com.example.running.core.model.BestEffort
import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.ElevationSource
import com.example.running.core.model.GeoBounds
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationEngineMode
import com.example.running.core.model.PauseInterval
import com.example.running.core.model.PointKind
import com.example.running.core.model.Run
import com.example.running.core.model.RunGraph
import com.example.running.core.model.RunStats
import com.example.running.core.model.RunStatus
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.Segment
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.SensorSample
import com.example.running.core.model.SensorType
import com.example.running.core.model.Sex
import com.example.running.core.model.Split
import com.example.running.core.model.TrackPoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Backup file schema (architecture.md §3.6). Every field added later must have a default so old
 * files stay readable; unknown keys are ignored so newer files stay readable by older code.
 */
@Serializable
internal data class BackupPayload(
    val schemaVersion: Int = 1,
    val createdAtMillis: Long,
    val appVersion: String,
    val profile: ProfileSnapshot,
    val settings: SettingsSnapshot = SettingsSnapshot(),
    val runs: List<RunSnapshot> = emptyList(),
)

@Serializable
internal data class ProfileSnapshot(
    val weightKg: Double,
    val ageYears: Int,
    val sex: String,
    val heightCm: Int? = null,
    val learnedRunStrideM: Double? = null,
    val learnedWalkStrideM: Double? = null,
)

/** Backup folder and schedule are device-local: deliberately not part of a backup. */
@Serializable
internal data class SettingsSnapshot(val engineMode: String = "AUTO")

@Serializable
internal data class RunSnapshot(
    val id: String,
    val name: String,
    val nameIsCustom: Boolean = false,
    val type: String,
    val typeIsManual: Boolean = false,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val zoneId: String,
    val engineMode: String = "AUTO",
    val startEngine: String = "PLAY",
    val engineFallback: Boolean = false,
    val devDualEngine: Boolean = false,
    val stats: StatsSnapshot = StatsSnapshot(),
    val analysisVersion: Int = 0,
    val createdAtMillis: Long = startedAtMillis,
    val updatedAtMillis: Long = startedAtMillis,
    val points: List<PointSnapshot> = emptyList(),
    val pauses: List<PauseSnapshot> = emptyList(),
    val splits: List<SplitSnapshot> = emptyList(),
    val segments: List<SegmentSnapshot> = emptyList(),
    val bestEfforts: List<BestEffortSnapshot> = emptyList(),
    val sensorSamples: List<SensorSnapshot> = emptyList(),
)

@Serializable
internal data class StatsSnapshot(
    val distanceM: Double = 0.0,
    val elapsedMillis: Long = 0,
    val movingMillis: Long = 0,
    val pausedMillis: Long = 0,
    val avgPaceSecPerKm: Double? = null,
    val maxSpeedMps: Double? = null,
    val elevationGainM: Double? = null,
    val elevationLossM: Double? = null,
    val elevationSource: String = "NONE",
    val caloriesKcal: Double? = null,
    val steps: Int? = null,
    val avgCadenceSpm: Double? = null,
    val walkFraction: Double? = null,
    val stepFilledM: Double = 0.0,
    val minLat: Double? = null,
    val minLon: Double? = null,
    val maxLat: Double? = null,
    val maxLon: Double? = null,
    val weightKgUsed: Double? = null,
    val strideMUsed: Double? = null,
)

/** Short JSON names keep files small (a run has thousands of points). */
@Serializable
internal data class PointSnapshot(
    @SerialName("k") val kind: String,
    @SerialName("e") val engine: String? = null,
    @SerialName("p") val isPrimary: Boolean = true,
    @SerialName("t") val timeMillis: Long,
    @SerialName("n") val elapsedRealtimeNanos: Long? = null,
    @SerialName("la") val lat: Double,
    @SerialName("lo") val lon: Double,
    @SerialName("alt") val altitudeM: Double? = null,
    @SerialName("msl") val mslAltitudeM: Double? = null,
    @SerialName("ha") val horizontalAccuracyM: Float? = null,
    @SerialName("va") val verticalAccuracyM: Float? = null,
    @SerialName("s") val speedMps: Float? = null,
    @SerialName("sa") val speedAccuracyMps: Float? = null,
    @SerialName("b") val bearingDeg: Float? = null,
    @SerialName("pz") val paused: Boolean = false,
    @SerialName("sf") val stepFilled: Boolean = false,
    @SerialName("d") val distanceM: Double? = null,
    @SerialName("el") val elevationM: Double? = null,
    @SerialName("pc") val paceSecPerKm: Double? = null,
    @SerialName("sk") val segmentKind: String? = null,
)

@Serializable
internal data class PauseSnapshot(val startMillis: Long, val endMillis: Long? = null)

@Serializable
internal data class SplitSnapshot(
    val index: Int,
    val distanceM: Double,
    val startMillis: Long,
    val endMillis: Long,
    val movingMillis: Long,
    val elapsedMillis: Long,
    val paceSecPerKm: Double? = null,
    val elevationGainM: Double? = null,
    val elevationLossM: Double? = null,
    val avgCadenceSpm: Double? = null,
    val walkFraction: Double? = null,
)

@Serializable
internal data class SegmentSnapshot(
    val kind: String,
    val startMillis: Long,
    val endMillis: Long,
    val startDistanceM: Double,
    val endDistanceM: Double,
    val avgSpeedMps: Double? = null,
    val avgCadenceSpm: Double? = null,
)

@Serializable
internal data class BestEffortSnapshot(
    val distance: String,
    val durationMillis: Long,
    val startMillis: Long,
    val endMillis: Long,
    val startDistanceM: Double,
)

@Serializable
internal data class SensorSnapshot(
    @SerialName("y") val type: String,
    @SerialName("t") val timeMillis: Long,
    @SerialName("v") val value: Double,
)

internal val BackupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

// ---- domain -> snapshot ------------------------------------------------------------------

internal fun RunnerProfile.toSnapshot() = ProfileSnapshot(
    weightKg = weightKg, ageYears = ageYears, sex = sex.name, heightCm = heightCm,
    learnedRunStrideM = learnedRunStrideM, learnedWalkStrideM = learnedWalkStrideM,
)

internal fun RunGraph.toSnapshot(): RunSnapshot = RunSnapshot(
    id = run.id, name = run.name, nameIsCustom = run.nameIsCustom,
    type = run.type.name, typeIsManual = run.typeIsManual,
    startedAtMillis = run.startedAtMillis, endedAtMillis = run.endedAtMillis, zoneId = run.zoneId,
    engineMode = run.engineMode.name, startEngine = run.startEngine.name,
    engineFallback = run.engineFallback, devDualEngine = run.devDualEngine,
    stats = run.stats.toSnapshot(), analysisVersion = run.analysisVersion,
    createdAtMillis = run.createdAtMillis, updatedAtMillis = run.updatedAtMillis,
    points = points.map { it.toSnapshot() },
    pauses = pauses.map { PauseSnapshot(it.startMillis, it.endMillis) },
    splits = splits.map {
        SplitSnapshot(
            it.index, it.distanceM, it.startMillis, it.endMillis, it.movingMillis, it.elapsedMillis,
            it.paceSecPerKm, it.elevationGainM, it.elevationLossM, it.avgCadenceSpm, it.walkFraction,
        )
    },
    segments = segments.map {
        SegmentSnapshot(
            it.kind.name, it.startMillis, it.endMillis, it.startDistanceM, it.endDistanceM,
            it.avgSpeedMps, it.avgCadenceSpm,
        )
    },
    bestEfforts = bestEfforts.map {
        BestEffortSnapshot(it.distance.name, it.durationMillis, it.startMillis, it.endMillis, it.startDistanceM)
    },
    sensorSamples = sensorSamples.map { SensorSnapshot(it.type.name, it.timeMillis, it.value) },
)

private fun RunStats.toSnapshot() = StatsSnapshot(
    distanceM = distanceM, elapsedMillis = elapsedMillis, movingMillis = movingMillis, pausedMillis = pausedMillis,
    avgPaceSecPerKm = avgPaceSecPerKm, maxSpeedMps = maxSpeedMps,
    elevationGainM = elevationGainM, elevationLossM = elevationLossM, elevationSource = elevationSource.name,
    caloriesKcal = caloriesKcal, steps = steps, avgCadenceSpm = avgCadenceSpm,
    walkFraction = walkFraction, stepFilledM = stepFilledM,
    minLat = bounds?.minLat, minLon = bounds?.minLon, maxLat = bounds?.maxLat, maxLon = bounds?.maxLon,
    weightKgUsed = weightKgUsed, strideMUsed = strideMUsed,
)

private fun TrackPoint.toSnapshot() = PointSnapshot(
    kind = kind.name, engine = engine?.name, isPrimary = isPrimary, timeMillis = timeMillis,
    elapsedRealtimeNanos = elapsedRealtimeNanos, lat = lat, lon = lon,
    altitudeM = altitudeM, mslAltitudeM = mslAltitudeM,
    horizontalAccuracyM = horizontalAccuracyM, verticalAccuracyM = verticalAccuracyM,
    speedMps = speedMps, speedAccuracyMps = speedAccuracyMps, bearingDeg = bearingDeg,
    paused = paused, stepFilled = stepFilled, distanceM = distanceM, elevationM = elevationM,
    paceSecPerKm = paceSecPerKm, segmentKind = segmentKind?.name,
)

// ---- snapshot -> domain (throws IllegalArgumentException on unknown enum names) ------------

internal fun ProfileSnapshot.toDomain() = RunnerProfile(
    weightKg = weightKg, ageYears = ageYears, sex = enumOrDefault(sex, Sex.UNSPECIFIED),
    heightCm = heightCm, learnedRunStrideM = learnedRunStrideM, learnedWalkStrideM = learnedWalkStrideM,
)

internal fun RunSnapshot.toGraph(): RunGraph {
    val run = Run(
        id = id, name = name, nameIsCustom = nameIsCustom,
        type = ActivityType.valueOf(type), typeIsManual = typeIsManual, status = RunStatus.FINISHED,
        startedAtMillis = startedAtMillis, endedAtMillis = endedAtMillis, zoneId = zoneId,
        engineMode = enumOrDefault(engineMode, LocationEngineMode.AUTO),
        startEngine = enumOrDefault(startEngine, LocationEngine.PLAY),
        engineFallback = engineFallback, devDualEngine = devDualEngine,
        stats = stats.toDomain(), analysisVersion = analysisVersion,
        createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis,
    )
    return RunGraph(
        run = run,
        points = points.map { it.toDomain(id) },
        pauses = pauses.map { PauseInterval(it.startMillis, it.endMillis) },
        splits = splits.map {
            Split(
                it.index, it.distanceM, it.startMillis, it.endMillis, it.movingMillis, it.elapsedMillis,
                it.paceSecPerKm, it.elevationGainM, it.elevationLossM, it.avgCadenceSpm, it.walkFraction,
            )
        },
        segments = segments.map {
            Segment(
                SegmentKind.valueOf(it.kind), it.startMillis, it.endMillis, it.startDistanceM, it.endDistanceM,
                it.avgSpeedMps, it.avgCadenceSpm,
            )
        },
        bestEfforts = bestEfforts.map {
            BestEffort(BestEffortDistance.valueOf(it.distance), it.durationMillis, it.startMillis, it.endMillis, it.startDistanceM)
        },
        sensorSamples = sensorSamples.map { SensorSample(SensorType.valueOf(it.type), it.timeMillis, it.value) },
    )
}

private fun StatsSnapshot.toDomain(): RunStats {
    val bounds = if (minLat != null && minLon != null && maxLat != null && maxLon != null) {
        GeoBounds(minLat, minLon, maxLat, maxLon)
    } else {
        null
    }
    return RunStats(
        distanceM = distanceM, elapsedMillis = elapsedMillis, movingMillis = movingMillis, pausedMillis = pausedMillis,
        avgPaceSecPerKm = avgPaceSecPerKm, maxSpeedMps = maxSpeedMps,
        elevationGainM = elevationGainM, elevationLossM = elevationLossM,
        elevationSource = enumOrDefault(elevationSource, ElevationSource.NONE),
        caloriesKcal = caloriesKcal, steps = steps, avgCadenceSpm = avgCadenceSpm,
        walkFraction = walkFraction, stepFilledM = stepFilledM, bounds = bounds,
        weightKgUsed = weightKgUsed, strideMUsed = strideMUsed,
    )
}

private fun PointSnapshot.toDomain(runId: String) = TrackPoint(
    runId = runId, kind = PointKind.valueOf(kind), engine = engine?.let { LocationEngine.valueOf(it) },
    isPrimary = isPrimary, timeMillis = timeMillis, elapsedRealtimeNanos = elapsedRealtimeNanos,
    lat = lat, lon = lon, altitudeM = altitudeM, mslAltitudeM = mslAltitudeM,
    horizontalAccuracyM = horizontalAccuracyM, verticalAccuracyM = verticalAccuracyM,
    speedMps = speedMps, speedAccuracyMps = speedAccuracyMps, bearingDeg = bearingDeg,
    paused = paused, stepFilled = stepFilled, distanceM = distanceM, elevationM = elevationM,
    paceSecPerKm = paceSecPerKm, segmentKind = segmentKind?.let { SegmentKind.valueOf(it) },
)

/** Lenient for settings-like values (a newer file may carry a constant this build lacks). */
private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
