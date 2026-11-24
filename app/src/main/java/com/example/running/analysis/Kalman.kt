package com.example.running.analysis

import com.example.running.core.model.GeoPoint
import com.example.running.core.util.EARTH_RADIUS_M
import com.example.running.core.util.cosLatFactor
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Flat east/north metres around [origin]; accurate to centimetres over a run-sized area. */
internal class LocalProjection(val origin: GeoPoint) {
    private val cosLat = cosLatFactor(origin.lat)
    private val metersPerRad = EARTH_RADIUS_M

    fun east(lon: Double): Double = Math.toRadians(lon - origin.lon) * metersPerRad * cosLat
    fun north(lat: Double): Double = Math.toRadians(lat - origin.lat) * metersPerRad
    fun toGeo(east: Double, north: Double): GeoPoint = GeoPoint(
        lat = origin.lat + Math.toDegrees(north / metersPerRad),
        lon = origin.lon + Math.toDegrees(east / (metersPerRad * cosLat)),
    )
}

/**
 * One axis of a constant-velocity Kalman filter (state: position, velocity) with white-noise
 * acceleration. Both axes are independent because the measurement noise is isotropic.
 */
internal class Kalman1D(position: Double, velocity: Double, posVar: Double, velVar: Double) {
    var p = position; private set
    var v = velocity; private set
    var p00 = posVar; private set
    var p01 = 0.0; private set
    var p11 = velVar; private set

    fun predict(dtSec: Double, accelVar: Double) {
        if (dtSec <= 0) return
        p += v * dtSec
        val dt2 = dtSec * dtSec
        val n00 = p00 + 2 * dtSec * p01 + dt2 * p11 + accelVar * dt2 * dt2 / 4
        val n01 = p01 + dtSec * p11 + accelVar * dt2 * dtSec / 2
        val n11 = p11 + accelVar * dt2
        p00 = n00; p01 = n01; p11 = n11
    }

    fun updatePosition(z: Double, variance: Double) {
        val s = p00 + variance
        val k0 = p00 / s
        val k1 = p01 / s
        val y = z - p
        p += k0 * y
        v += k1 * y
        val n00 = (1 - k0) * p00
        val n01 = (1 - k0) * p01
        val n11 = p11 - k1 * p01
        p00 = n00; p01 = n01; p11 = n11
    }

    fun updateVelocity(z: Double, variance: Double) {
        val s = p11 + variance
        val k0 = p01 / s
        val k1 = p11 / s
        val y = z - v
        p += k0 * y
        v += k1 * y
        val n00 = p00 - k0 * p01
        val n01 = (1 - k1) * p01
        val n11 = (1 - k1) * p11
        p00 = n00; p01 = n01; p11 = n11
    }

    fun snapshot() = State(p, v, p00, p01, p11)

    data class State(val p: Double, val v: Double, val p00: Double, val p01: Double, val p11: Double)
}

/** One measurement for the 2-D filter, already projected to local metres. */
internal data class KalmanMeasurement(
    val timeMillis: Long,
    val east: Double,
    val north: Double,
    val accuracyM: Double,
    /** Doppler speed and bearing; bearing may be null. */
    val speedMps: Double?,
    val speedAccuracyMps: Double?,
    val bearingDeg: Double?,
)

internal data class KalmanOutput(val east: Double, val north: Double, val vEast: Double, val vNorth: Double)

/** 2-D constant-velocity Kalman filter fed with position and (when trustworthy) Doppler velocity. */
internal class GpsKalman(private val accelStd: Double = AnalysisTuning.KALMAN_ACCEL_STD) {
    private var east: Kalman1D? = null
    private var north: Kalman1D? = null
    private var lastTime = 0L

    val isInitialised: Boolean get() = east != null

    fun reset() {
        east = null; north = null
    }

    /** Runs predict + update; returns the filtered state, and the predicted state before the update (for RTS). */
    fun step(m: KalmanMeasurement): Pair<Step, KalmanOutput> {
        val posVar = positionVariance(m.accuracyM)
        val e = east
        val n = north
        if (e == null || n == null) {
            val (ve, vn) = dopplerVector(m) ?: (0.0 to 0.0)
            val ke = Kalman1D(m.east, ve, posVar, 4.0)
            val kn = Kalman1D(m.north, vn, posVar, 4.0)
            east = ke; north = kn; lastTime = m.timeMillis
            val s = Step(m.timeMillis, 0.0, ke.snapshot(), kn.snapshot(), ke.snapshot(), kn.snapshot())
            return s to KalmanOutput(ke.p, kn.p, ke.v, kn.v)
        }
        val dt = (m.timeMillis - lastTime).coerceAtLeast(0) / 1000.0
        lastTime = m.timeMillis
        val q = accelStd * accelStd
        e.predict(dt, q); n.predict(dt, q)
        val predE = e.snapshot(); val predN = n.snapshot()
        e.updatePosition(m.east, posVar); n.updatePosition(m.north, posVar)
        val speed = m.speedMps
        if (speed != null) {
            val sigma = max(m.speedAccuracyMps ?: 0.5, AnalysisTuning.MIN_DOPPLER_SIGMA)
            val vec = dopplerVector(m)
            if (vec != null) {
                e.updateVelocity(vec.first, sigma * sigma); n.updateVelocity(vec.second, sigma * sigma)
            } else if (speed < AnalysisTuning.DOPPLER_STILL_SPEED) {
                val stillVar = max(sigma, AnalysisTuning.DOPPLER_STILL_SPEED).let { it * it }
                e.updateVelocity(0.0, stillVar); n.updateVelocity(0.0, stillVar)
            }
        }
        return Step(m.timeMillis, dt, predE, predN, e.snapshot(), n.snapshot()) to KalmanOutput(e.p, n.p, e.v, n.v)
    }

    data class Step(
        val timeMillis: Long, val dtSec: Double,
        val predE: Kalman1D.State, val predN: Kalman1D.State,
        val filtE: Kalman1D.State, val filtN: Kalman1D.State,
    )

    companion object {
        fun positionVariance(accuracyM: Double): Double {
            val sigma = max(accuracyM / AnalysisTuning.ACCURACY_TO_SIGMA, AnalysisTuning.MIN_POSITION_SIGMA_M)
            return sigma * sigma
        }

        private fun dopplerVector(m: KalmanMeasurement): Pair<Double, Double>? {
            val s = m.speedMps ?: return null
            val b = m.bearingDeg ?: return null
            if (s < AnalysisTuning.DOPPLER_BEARING_MIN_SPEED) return null
            val rad = Math.toRadians(b)
            return s * sin(rad) to s * cos(rad)
        }

        /**
         * Forward filter + Rauch–Tung–Striebel backward pass over one continuous stretch.
         * Returns smoothed positions/velocities aligned with [measurements].
         */
        fun smooth(measurements: List<KalmanMeasurement>, accelStd: Double = AnalysisTuning.KALMAN_ACCEL_STD): List<KalmanOutput> {
            if (measurements.isEmpty()) return emptyList()
            val kf = GpsKalman(accelStd)
            val steps = measurements.map { kf.step(it).first }
            val sE = arrayOfNulls<DoubleArray>(steps.size)
            val sN = arrayOfNulls<DoubleArray>(steps.size)
            val last = steps.last()
            sE[steps.lastIndex] = doubleArrayOf(last.filtE.p, last.filtE.v)
            sN[steps.lastIndex] = doubleArrayOf(last.filtN.p, last.filtN.v)
            for (k in steps.size - 2 downTo 0) {
                val next = steps[k + 1]
                sE[k] = rts(steps[k].filtE, next.predE, next.dtSec, sE[k + 1]!!)
                sN[k] = rts(steps[k].filtN, next.predN, next.dtSec, sN[k + 1]!!)
            }
            return steps.indices.map { KalmanOutput(sE[it]!![0], sN[it]!![0], sE[it]!![1], sN[it]!![1]) }
        }

        private fun rts(f: Kalman1D.State, pred: Kalman1D.State, dt: Double, smoothedNext: DoubleArray): DoubleArray {
            // C = Pf * F' * Pp^-1, with F = [[1, dt], [0, 1]].
            val a00 = f.p00 + dt * f.p01
            val a01 = f.p01
            val a10 = f.p01 + dt * f.p11
            val a11 = f.p11
            val det = pred.p00 * pred.p11 - pred.p01 * pred.p01
            if (det <= 1e-12) return doubleArrayOf(f.p, f.v)
            val i00 = pred.p11 / det
            val i01 = -pred.p01 / det
            val i11 = pred.p00 / det
            val c00 = a00 * i00 + a01 * i01
            val c01 = a00 * i01 + a01 * i11
            val c10 = a10 * i00 + a11 * i01
            val c11 = a10 * i01 + a11 * i11
            val dp = smoothedNext[0] - pred.p
            val dv = smoothedNext[1] - pred.v
            return doubleArrayOf(f.p + c00 * dp + c01 * dv, f.v + c10 * dp + c11 * dv)
        }
    }
}
