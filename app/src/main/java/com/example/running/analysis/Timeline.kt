package com.example.running.analysis

import com.example.running.core.model.BestEffort
import com.example.running.core.model.BestEffortDistance
import com.example.running.core.model.Segment
import com.example.running.core.model.SegmentKind
import com.example.running.core.model.Split
import com.example.running.core.util.METERS_PER_KM
import com.example.running.core.util.paceSecPerKm
import kotlin.math.roundToLong

/**
 * The analysed run as cumulative arrays over the output points. "Interval i" runs from point i-1 to
 * point i; [kind] is its classification, null when it is excluded (first point, or it spans a pause).
 * Distance only grows in RUN/WALK intervals; moving time only in RUN/WALK intervals.
 */
internal class Timeline(
    val time: LongArray,
    val distance: DoubleArray,
    val moving: DoubleArray,
    val kind: Array<SegmentKind?>,
    val steps: DoubleArray,
    val gain: DoubleArray,
    val loss: DoubleArray,
) {
    val size: Int get() = time.size
    val totalDistance: Double get() = if (size == 0) 0.0 else distance[size - 1]
    val totalMoving: Double get() = if (size == 0) 0.0 else moving[size - 1]

    fun intervalDistance(i: Int) = distance[i] - distance[i - 1]
    fun intervalMoving(i: Int) = moving[i] - moving[i - 1]

    /** Speed over a centered ±[halfWindowMillis] window (distance / moving time), null when not moving enough. */
    fun windowSpeed(i: Int, halfWindowMillis: Long, minMovingMillis: Double = 3_000.0): Double? {
        var lo = i
        var hi = i
        while (lo > 0 && time[i] - time[lo - 1] <= halfWindowMillis) lo--
        while (hi < size - 1 && time[hi + 1] - time[i] <= halfWindowMillis) hi++
        val m = moving[hi] - moving[lo]
        if (m < minMovingMillis) return null
        return (distance[hi] - distance[lo]) / (m / 1000.0)
    }
}

internal object SplitCalculator {
    private class Acc(val index: Int, val startTime: Long, val startDist: Double) {
        var dist = 0.0; var moving = 0.0; var gain = 0.0; var loss = 0.0; var steps = 0.0; var walk = 0.0
        fun add(tl: Timeline, i: Int, f: Double) {
            val d = tl.intervalDistance(i) * f
            dist += d
            moving += tl.intervalMoving(i) * f
            gain += tl.gain[i] * f
            loss += tl.loss[i] * f
            steps += tl.steps[i] * f
            if (tl.kind[i] == SegmentKind.WALK) walk += d
        }
    }

    /** 1 km splits on moving distance; a partial last split is kept when it is at least 10 m. */
    fun compute(tl: Timeline, hasElevation: Boolean, hasSteps: Boolean): List<Split> {
        if (tl.size < 2) return emptyList()
        val out = mutableListOf<Split>()
        var acc = Acc(0, tl.time[0], 0.0)
        var nextKm = METERS_PER_KM
        fun close(endTime: Long, endDist: Double) {
            out += toSplit(acc, endTime, endDist, hasElevation, hasSteps)
            acc = Acc(out.size, endTime, endDist)
        }
        for (i in 1 until tl.size) {
            val d = tl.intervalDistance(i)
            if (d <= 0.0) { acc.add(tl, i, 1.0); continue }
            var pos = tl.distance[i - 1]
            var used = 0.0
            while (tl.distance[i] >= nextKm - 1e-9) {
                val f = (nextKm - pos) / d
                acc.add(tl, i, f)
                used += f
                val crossTime = tl.time[i - 1] + ((tl.time[i] - tl.time[i - 1]) * used).roundToLong()
                close(crossTime, nextKm)
                pos = nextKm
                nextKm += METERS_PER_KM
            }
            if (used < 1.0) acc.add(tl, i, 1.0 - used)
        }
        val endDist = tl.totalDistance
        if (endDist - acc.startDist >= 10.0) close(tl.time[tl.size - 1], endDist)
        return out
    }

    private fun toSplit(a: Acc, endTime: Long, endDist: Double, hasElevation: Boolean, hasSteps: Boolean): Split {
        val distance = endDist - a.startDist
        val moving = a.moving.roundToLong()
        return Split(
            index = a.index, distanceM = distance, startMillis = a.startTime, endMillis = endTime,
            movingMillis = moving, elapsedMillis = (endTime - a.startTime).coerceAtLeast(0),
            paceSecPerKm = paceSecPerKm(distance, moving),
            elevationGainM = if (hasElevation) a.gain else null,
            elevationLossM = if (hasElevation) a.loss else null,
            avgCadenceSpm = if (hasSteps && a.moving > 0) a.steps * 60_000.0 / a.moving else null,
            walkFraction = if (distance > 0) (a.walk / distance).coerceIn(0.0, 1.0) else null,
        )
    }
}

internal object BestEffortCalculator {
    /** Fastest window (moving time) for each distance the run covered; the start is interpolated. */
    fun compute(tl: Timeline): List<BestEffort> = BestEffortDistance.entries.mapNotNull { target ->
        if (tl.size < 2 || tl.totalDistance < target.meters) return@mapNotNull null
        var best: BestEffort? = null
        var bestMoving = Double.MAX_VALUE
        var s = 0
        for (j in 1 until tl.size) {
            val startDist = tl.distance[j] - target.meters
            if (startDist < -1e-9) continue
            // Largest s with distance[s] <= startDist (the latest such point gives the least moving time).
            while (s + 1 < j && tl.distance[s + 1] <= startDist + 1e-9) s++
            val (startMoving, startTime) = if (s + 1 <= j && tl.distance[s + 1] > tl.distance[s]) {
                val f = ((startDist - tl.distance[s]) / (tl.distance[s + 1] - tl.distance[s])).coerceIn(0.0, 1.0)
                (tl.moving[s] + f * (tl.moving[s + 1] - tl.moving[s])) to
                    (tl.time[s] + (f * (tl.time[s + 1] - tl.time[s])).roundToLong())
            } else {
                tl.moving[s] to tl.time[s]
            }
            val duration = tl.moving[j] - startMoving
            if (duration < bestMoving) {
                bestMoving = duration
                best = BestEffort(target, duration.roundToLong(), startTime, tl.time[j], startDist.coerceAtLeast(0.0))
            }
        }
        best
    }
}

internal object SegmentBuilder {
    /** RUN / WALK / STOP segments over the non-paused timeline; a pause always ends a segment. */
    fun compute(tl: Timeline, hasSteps: Boolean): List<Segment> {
        val out = mutableListOf<Segment>()
        var i = 1
        while (i < tl.size) {
            val k = tl.kind[i]
            if (k == null) { i++; continue }
            val from = i - 1
            var to = i
            while (to + 1 < tl.size && tl.kind[to + 1] == k) to++
            val wall = (tl.time[to] - tl.time[from]).coerceAtLeast(0)
            val dist = tl.distance[to] - tl.distance[from]
            val moving = tl.moving[to] - tl.moving[from]
            var steps = 0.0
            for (x in from + 1..to) steps += tl.steps[x]
            out += Segment(
                kind = k, startMillis = tl.time[from], endMillis = tl.time[to],
                startDistanceM = tl.distance[from], endDistanceM = tl.distance[to],
                avgSpeedMps = if (k == SegmentKind.STOP) 0.0 else if (moving > 0) dist / (moving / 1000.0) else null,
                avgCadenceSpm = if (hasSteps && wall > 0) steps * 60_000.0 / wall else null,
            )
            i = to + 1
        }
        return out
    }
}
