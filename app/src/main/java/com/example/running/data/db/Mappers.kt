package com.example.running.data.db

import com.example.running.core.model.BestEffort
import com.example.running.core.model.GeoBounds
import com.example.running.core.model.PauseInterval
import com.example.running.core.model.Run
import com.example.running.core.model.RunStats
import com.example.running.core.model.Segment
import com.example.running.core.model.SensorSample
import com.example.running.core.model.Split
import com.example.running.core.model.TrackPoint
import com.example.running.data.db.entities.BestEffortEntity
import com.example.running.data.db.entities.PauseEntity
import com.example.running.data.db.entities.RunEntity
import com.example.running.data.db.entities.SegmentEntity
import com.example.running.data.db.entities.SensorSampleEntity
import com.example.running.data.db.entities.SplitEntity
import com.example.running.data.db.entities.TrackPointEntity

// entity <-> domain. Domain types never import Room.

fun RunEntity.toDomain(): Run = Run(
    id = id, name = name, nameIsCustom = nameIsCustom, type = type, typeIsManual = typeIsManual,
    status = status, startedAtMillis = startedAtMillis, endedAtMillis = endedAtMillis, zoneId = zoneId,
    engineMode = engineMode, startEngine = startEngine, engineFallback = engineFallback,
    devDualEngine = devDualEngine,
    stats = RunStats(
        distanceM = distanceM, elapsedMillis = elapsedMillis, movingMillis = movingMillis,
        pausedMillis = pausedMillis, avgPaceSecPerKm = avgPaceSecPerKm, maxSpeedMps = maxSpeedMps,
        elevationGainM = elevationGainM, elevationLossM = elevationLossM, elevationSource = elevationSource,
        caloriesKcal = caloriesKcal, steps = steps, avgCadenceSpm = avgCadenceSpm,
        walkFraction = walkFraction, stepFilledM = stepFilledM,
        bounds = GeoBounds.ofNullable(minLat, minLon, maxLat, maxLon),
        weightKgUsed = weightKgUsed, strideMUsed = strideMUsed,
    ),
    analysisVersion = analysisVersion, createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis,
)

fun Run.toEntity(): RunEntity = RunEntity(
    id = id, name = name, nameIsCustom = nameIsCustom, type = type, typeIsManual = typeIsManual,
    status = status, startedAtMillis = startedAtMillis, endedAtMillis = endedAtMillis, zoneId = zoneId,
    engineMode = engineMode, startEngine = startEngine, engineFallback = engineFallback,
    devDualEngine = devDualEngine,
    distanceM = stats.distanceM, elapsedMillis = stats.elapsedMillis, movingMillis = stats.movingMillis,
    pausedMillis = stats.pausedMillis, avgPaceSecPerKm = stats.avgPaceSecPerKm, maxSpeedMps = stats.maxSpeedMps,
    elevationGainM = stats.elevationGainM, elevationLossM = stats.elevationLossM,
    elevationSource = stats.elevationSource, caloriesKcal = stats.caloriesKcal, steps = stats.steps,
    avgCadenceSpm = stats.avgCadenceSpm, walkFraction = stats.walkFraction, stepFilledM = stats.stepFilledM,
    minLat = stats.bounds?.minLat, minLon = stats.bounds?.minLon,
    maxLat = stats.bounds?.maxLat, maxLon = stats.bounds?.maxLon,
    weightKgUsed = stats.weightKgUsed, strideMUsed = stats.strideMUsed,
    analysisVersion = analysisVersion, createdAtMillis = createdAtMillis, updatedAtMillis = updatedAtMillis,
)

fun TrackPointEntity.toDomain(): TrackPoint = TrackPoint(
    runId = runId, kind = kind, engine = engine, isPrimary = isPrimary, timeMillis = timeMillis,
    elapsedRealtimeNanos = elapsedRealtimeNanos, lat = lat, lon = lon, altitudeM = altitudeM,
    mslAltitudeM = mslAltitudeM, horizontalAccuracyM = horizontalAccuracyM,
    verticalAccuracyM = verticalAccuracyM, speedMps = speedMps, speedAccuracyMps = speedAccuracyMps,
    bearingDeg = bearingDeg, paused = paused, stepFilled = stepFilled, distanceM = distanceM,
    elevationM = elevationM, paceSecPerKm = paceSecPerKm, segmentKind = segmentKind,
)

fun TrackPoint.toEntity(): TrackPointEntity = TrackPointEntity(
    runId = runId, kind = kind, engine = engine, isPrimary = isPrimary, timeMillis = timeMillis,
    elapsedRealtimeNanos = elapsedRealtimeNanos, lat = lat, lon = lon, altitudeM = altitudeM,
    mslAltitudeM = mslAltitudeM, horizontalAccuracyM = horizontalAccuracyM,
    verticalAccuracyM = verticalAccuracyM, speedMps = speedMps, speedAccuracyMps = speedAccuracyMps,
    bearingDeg = bearingDeg, paused = paused, stepFilled = stepFilled, distanceM = distanceM,
    elevationM = elevationM, paceSecPerKm = paceSecPerKm, segmentKind = segmentKind,
)

fun PauseEntity.toDomain(): PauseInterval = PauseInterval(startMillis, endMillis)
fun PauseInterval.toEntity(runId: String): PauseEntity = PauseEntity(runId = runId, startMillis = startMillis, endMillis = endMillis)

fun SplitEntity.toDomain(): Split = Split(
    index = splitIndex, distanceM = distanceM, startMillis = startMillis, endMillis = endMillis,
    movingMillis = movingMillis, elapsedMillis = elapsedMillis, paceSecPerKm = paceSecPerKm,
    elevationGainM = elevationGainM, elevationLossM = elevationLossM, avgCadenceSpm = avgCadenceSpm,
    walkFraction = walkFraction,
)
fun Split.toEntity(runId: String): SplitEntity = SplitEntity(
    runId = runId, splitIndex = index, distanceM = distanceM, startMillis = startMillis, endMillis = endMillis,
    movingMillis = movingMillis, elapsedMillis = elapsedMillis, paceSecPerKm = paceSecPerKm,
    elevationGainM = elevationGainM, elevationLossM = elevationLossM, avgCadenceSpm = avgCadenceSpm,
    walkFraction = walkFraction,
)

fun SegmentEntity.toDomain(): Segment = Segment(
    kind = kind, startMillis = startMillis, endMillis = endMillis, startDistanceM = startDistanceM,
    endDistanceM = endDistanceM, avgSpeedMps = avgSpeedMps, avgCadenceSpm = avgCadenceSpm,
)
fun Segment.toEntity(runId: String): SegmentEntity = SegmentEntity(
    runId = runId, kind = kind, startMillis = startMillis, endMillis = endMillis,
    startDistanceM = startDistanceM, endDistanceM = endDistanceM, avgSpeedMps = avgSpeedMps,
    avgCadenceSpm = avgCadenceSpm,
)

fun BestEffortEntity.toDomain(): BestEffort = BestEffort(
    distance = distance, durationMillis = durationMillis, startMillis = startMillis, endMillis = endMillis,
    startDistanceM = startDistanceM,
)
fun BestEffort.toEntity(runId: String): BestEffortEntity = BestEffortEntity(
    runId = runId, distance = distance, durationMillis = durationMillis, startMillis = startMillis,
    endMillis = endMillis, startDistanceM = startDistanceM,
)

fun SensorSampleEntity.toDomain(): SensorSample = SensorSample(type, timeMillis, value)
fun SensorSample.toEntity(runId: String): SensorSampleEntity =
    SensorSampleEntity(runId = runId, type = type, timeMillis = timeMillis, value = value)
