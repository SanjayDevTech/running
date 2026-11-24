package com.example.running.testing

import com.example.running.core.model.AnalysisInput
import com.example.running.core.model.LocationEngine
import com.example.running.core.model.LocationFix
import com.example.running.core.model.PauseInterval
import com.example.running.core.model.RunnerProfile
import com.example.running.core.model.SensorSample
import com.example.running.core.model.SensorType
import com.example.running.core.model.Sex
import com.example.running.core.model.TrackPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** GPS fixture (architecture.md §6.2). `t` values are ms from [startMillis]. */
@Serializable
data class GpsFixture(
    val name: String,
    val description: String = "",
    val zoneId: String = "Asia/Kolkata",
    val startMillis: Long,
    val profile: FixtureProfile = FixtureProfile(),
    val fixes: List<FixtureFix> = emptyList(),
    val steps: List<FixtureStep> = emptyList(),
    val stepCounter: List<FixtureCount> = emptyList(),
    val pressure: List<FixturePressure> = emptyList(),
    val pauses: List<FixturePause> = emptyList(),
    /** Free-form per owner; read with expect["key"]. */
    val expect: JsonObject = JsonObject(emptyMap()),
) {
    @Serializable data class FixtureProfile(
        val weightKg: Double = 70.0, val ageYears: Int = 30, val sex: String = "UNSPECIFIED", val heightCm: Int? = null,
    )
    @Serializable data class FixtureFix(
        val t: Long, val lat: Double, val lon: Double,
        val alt: Double? = null, val msl: Double? = null, val hAcc: Float? = null, val vAcc: Float? = null,
        val spd: Float? = null, val sAcc: Float? = null, val brg: Float? = null, val engine: String = "PLAY",
    )
    @Serializable data class FixtureStep(val t: Long)
    @Serializable data class FixtureCount(val t: Long, val count: Long)
    @Serializable data class FixturePressure(val t: Long, val hPa: Double)
    @Serializable data class FixturePause(val start: Long, val end: Long? = null)

    val endMillis: Long get() = startMillis + (fixes.maxOfOrNull { it.t } ?: 0)

    fun runnerProfile(): RunnerProfile = RunnerProfile(
        weightKg = profile.weightKg, ageYears = profile.ageYears,
        sex = Sex.entries.firstOrNull { it.name == profile.sex } ?: Sex.UNSPECIFIED, heightCm = profile.heightCm,
    )

    fun toLocationFixes(): List<LocationFix> = fixes.map { f ->
        LocationFix(
            timeMillis = startMillis + f.t, elapsedRealtimeNanos = f.t * 1_000_000L, lat = f.lat, lon = f.lon,
            altitudeM = f.alt, mslAltitudeM = f.msl, horizontalAccuracyM = f.hAcc, verticalAccuracyM = f.vAcc,
            speedMps = f.spd, speedAccuracyMps = f.sAcc, bearingDeg = f.brg, engine = LocationEngine.valueOf(f.engine),
        )
    }

    /** RAW points; all marked primary and paused per [pauses]. */
    fun toRawPoints(runId: String): List<TrackPoint> = toLocationFixes().map { fix ->
        val rel = fix.timeMillis - startMillis
        fix.toRawPoint(runId, isPrimary = true, paused = pauses.any { rel >= it.start && (it.end == null || rel < it.end) })
    }

    fun toSensorSamples(): List<SensorSample> = (
        steps.map { SensorSample(SensorType.STEP_DETECTOR, startMillis + it.t, 1.0) } +
            stepCounter.map { SensorSample(SensorType.STEP_COUNTER, startMillis + it.t, it.count.toDouble()) } +
            pressure.map { SensorSample(SensorType.PRESSURE, startMillis + it.t, it.hPa) }
        ).sortedBy { it.timeMillis }

    fun toPauses(): List<PauseInterval> = pauses.map { PauseInterval(startMillis + it.start, it.end?.let { e -> startMillis + e } ?: endMillis) }

    fun toAnalysisInput(runId: String = "fixture-$name"): AnalysisInput = AnalysisInput(
        runId = runId, startedAtMillis = startMillis, endedAtMillis = endMillis, zoneId = zoneId,
        rawPoints = toRawPoints(runId), pauses = toPauses(), sensorSamples = toSensorSamples(),
        profile = runnerProfile(), manualType = null,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Loads `fixtures/<name>.json` from the test classpath (src/test/resources). */
        fun load(name: String): GpsFixture {
            val path = "fixtures/${name.removeSuffix(".json")}.json"
            val stream = requireNotNull(GpsFixture::class.java.classLoader?.getResourceAsStream(path)) { "Missing $path" }
            return json.decodeFromString(serializer(), stream.bufferedReader().use { it.readText() })
        }
    }
}
