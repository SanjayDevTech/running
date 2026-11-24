package com.example.running.analysis

import com.example.running.core.model.ElevationSource
import kotlin.math.pow

/** Altitude from pressure (international barometric formula, standard sea-level pressure). */
internal fun pressureToAltitudeM(hPa: Double): Double = 44_330.77 * (1.0 - (hPa / 1_013.25).pow(0.190263))

/** Time-based centered moving average over sorted [times]. */
internal fun movingAverage(times: LongArray, values: DoubleArray, halfWindowMillis: Long): DoubleArray {
    val out = DoubleArray(values.size)
    var lo = 0
    var hi = 0
    var sum = 0.0
    for (i in values.indices) {
        while (hi < values.size && times[hi] <= times[i] + halfWindowMillis) { sum += values[hi]; hi++ }
        while (times[lo] < times[i] - halfWindowMillis) { sum -= values[lo]; lo++ }
        out[i] = sum / (hi - lo)
    }
    return out
}

/** Linear interpolation of a sorted series at [t] (clamped at the ends). */
internal fun interpolateSeries(times: LongArray, values: DoubleArray, t: Long): Double {
    if (t <= times[0]) return values[0]
    if (t >= times[times.lastIndex]) return values[values.lastIndex]
    var lo = 0
    var hi = times.lastIndex
    while (hi - lo > 1) {
        val mid = (lo + hi) ushr 1
        if (times[mid] <= t) lo = mid else hi = mid
    }
    val span = (times[hi] - times[lo]).toDouble()
    return if (span <= 0) values[hi] else values[lo] + (values[hi] - values[lo]) * (t - times[lo]) / span
}

/**
 * Dead-band gain/loss accumulator: a change is counted once the value has moved [threshold] away from
 * the last reference, so sensor noise below the threshold never adds up.
 */
internal class ClimbCounter(private val threshold: Double) {
    private var ref: Double? = null
    var gain = 0.0; private set
    var loss = 0.0; private set

    /** Feeds a value; returns (gain, loss) realised by this value. */
    fun add(value: Double): Pair<Double, Double> {
        val r = ref
        if (r == null) { ref = value; return 0.0 to 0.0 }
        return when {
            value - r >= threshold -> { val g = value - r; gain += g; ref = value; g to 0.0 }
            r - value >= threshold -> { val l = r - value; loss += l; ref = value; 0.0 to l }
            else -> 0.0 to 0.0
        }
    }

    /** Next value starts a fresh reference (e.g. after a pause). */
    fun restart() { ref = null }
}

/** Per-point elevation for a run, from the barometer when present, else smoothed GPS altitude. */
internal data class ElevationTrack(val source: ElevationSource, val values: DoubleArray?, val threshold: Double)

internal object ElevationBuilder {
    /**
     * @param pointTimes times of the output points.
     * @param gpsAltitude per point: MSL altitude if known else ellipsoid altitude; null if unknown/poor.
     * @param pressure (time, hPa) samples.
     */
    fun build(pointTimes: LongArray, gpsAltitude: Array<Double?>, pressure: List<Pair<Long, Double>>): ElevationTrack {
        if (pointTimes.isEmpty()) return ElevationTrack(ElevationSource.NONE, null, 0.0)
        val baro = pressure.filter { it.second in 300.0..1_100.0 }.sortedBy { it.first }
        val span = pointTimes.last() - pointTimes.first()
        val baroSpan = if (baro.size >= 2) baro.last().first - baro.first().first else 0
        if (baro.size >= AnalysisTuning.BARO_MIN_SAMPLES && baroSpan >= span / 2) {
            val t = LongArray(baro.size) { baro[it].first }
            val raw = DoubleArray(baro.size) { pressureToAltitudeM(baro[it].second) }
            val smooth = movingAverage(t, raw, AnalysisTuning.BARO_SMOOTH_HALF_WINDOW_MILLIS)
            val perPoint = DoubleArray(pointTimes.size) { interpolateSeries(t, smooth, pointTimes[it]) }
            // Anchor the relative barometric profile to GPS altitude so absolute values look right.
            val offsets = pointTimes.indices.mapNotNull { i -> gpsAltitude[i]?.let { it - perPoint[i] } }.sorted()
            val offset = if (offsets.isEmpty()) 0.0 else offsets[offsets.size / 2]
            for (i in perPoint.indices) perPoint[i] += offset
            return ElevationTrack(ElevationSource.BAROMETER, perPoint, AnalysisTuning.BARO_HYSTERESIS_M)
        }
        val known = pointTimes.indices.filter { gpsAltitude[it] != null }
        if (known.size < 2) return ElevationTrack(ElevationSource.NONE, null, 0.0)
        val t = LongArray(known.size) { pointTimes[known[it]] }
        val v = DoubleArray(known.size) { gpsAltitude[known[it]]!! }
        val smooth = movingAverage(t, v, AnalysisTuning.GPS_ALT_SMOOTH_HALF_WINDOW_MILLIS)
        val perPoint = DoubleArray(pointTimes.size) { interpolateSeries(t, smooth, pointTimes[it]) }
        return ElevationTrack(ElevationSource.GPS, perPoint, AnalysisTuning.GPS_ALT_HYSTERESIS_M)
    }
}
