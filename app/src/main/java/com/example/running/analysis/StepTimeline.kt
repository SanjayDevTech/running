package com.example.running.analysis

import com.example.running.core.model.PauseInterval
import com.example.running.core.model.SensorSample
import com.example.running.core.model.SensorType

/**
 * Cumulative step count as a function of time, built from STEP_DETECTOR events (1 per step) or
 * STEP_COUNTER samples (cumulative; resets tolerated). Linear between samples. Steps taken while
 * paused are excluded by [stepsBetween] when pauses are given. RESUME_GAP samples with a known step
 * count (taken while the app was dead) are added at their time, so they ramp in linearly from the last
 * step before the interruption.
 */
internal class StepTimeline private constructor(
    private val times: LongArray,
    private val cumulative: DoubleArray,
    private val pauses: List<PauseInterval>,
) {
    val hasData: Boolean get() = times.size >= 2

    /** Cumulative steps at [t], ignoring pauses. */
    fun at(t: Long): Double {
        if (times.isEmpty()) return 0.0
        if (t <= times[0]) return cumulative[0]
        if (t >= times[times.lastIndex]) return cumulative[cumulative.lastIndex]
        var lo = 0
        var hi = times.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= t) lo = mid else hi = mid
        }
        val span = (times[hi] - times[lo]).toDouble()
        val f = if (span <= 0) 1.0 else (t - times[lo]) / span
        return cumulative[lo] + (cumulative[hi] - cumulative[lo]) * f
    }

    /** Steps in [from, to] excluding paused time. */
    fun stepsBetween(from: Long, to: Long): Double {
        if (to <= from) return 0.0
        var total = at(to) - at(from)
        for (p in pauses) {
            val s = maxOf(p.startMillis, from)
            val e = minOf(p.endMillis ?: to, to)
            if (e > s) total -= at(e) - at(s)
        }
        return total.coerceAtLeast(0.0)
    }

    /** Steps per minute over [from, to] (wall time). */
    fun cadence(from: Long, to: Long): Double {
        val dt = to - from
        if (dt <= 0) return 0.0
        return (at(to) - at(from)) * 60_000.0 / dt
    }

    /** Centered cadence around [t]. */
    fun cadenceAround(t: Long, halfWindowMillis: Long): Double = cadence(t - halfWindowMillis, t + halfWindowMillis)

    companion object {
        val EMPTY = StepTimeline(LongArray(0), DoubleArray(0), emptyList())

        fun from(samples: List<SensorSample>, pauses: List<PauseInterval> = emptyList()): StepTimeline {
            val detector = samples.filter { it.type == SensorType.STEP_DETECTOR }.sortedBy { it.timeMillis }
            val counter = samples.filter { it.type == SensorType.STEP_COUNTER }.sortedBy { it.timeMillis }
            val gaps = samples.filter { it.type == SensorType.RESUME_GAP && it.value > 0.0 }
            val fromCounter = counterTimeline(counter)?.let { withGaps(it.first, it.second, gaps) }
            val counterTotal = fromCounter?.second?.lastOrNull() ?: 0.0
            // Detector events have exact timing; prefer them unless they clearly missed steps.
            val useDetector = detector.size >= 2 && detector.size >= 0.9 * counterTotal
            return when {
                useDetector -> {
                    val events = (detector + gaps).sortedBy { it.timeMillis }
                    val t = LongArray(events.size + 1)
                    val c = DoubleArray(events.size + 1)
                    // A detector event marks a completed step; anchor zero one step-period earlier.
                    t[0] = detector[0].timeMillis - 500
                    events.forEachIndexed { i, s -> t[i + 1] = s.timeMillis; c[i + 1] = c[i] + s.value.coerceAtLeast(1.0) }
                    StepTimeline(t, c, pauses)
                }
                fromCounter != null -> StepTimeline(fromCounter.first, fromCounter.second, pauses)
                else -> EMPTY
            }
        }

        /** Adds each gap's steps at its time to a cumulative timeline. */
        private fun withGaps(times: LongArray, cumulative: DoubleArray, gaps: List<SensorSample>): Pair<LongArray, DoubleArray> {
            if (gaps.isEmpty()) return times to cumulative
            val inc = ArrayList<Pair<Long, Double>>(times.size + gaps.size)
            for (i in times.indices) inc += times[i] to (if (i == 0) 0.0 else cumulative[i] - cumulative[i - 1])
            gaps.forEach { inc += it.timeMillis to it.value }
            inc.sortBy { it.first }
            val t = LongArray(inc.size) { inc[it].first }
            val c = DoubleArray(inc.size)
            c[0] = inc[0].second
            for (i in 1 until inc.size) c[i] = c[i - 1] + inc[i].second
            return t to c
        }

        private fun counterTimeline(counter: List<SensorSample>): Pair<LongArray, DoubleArray>? {
            if (counter.size < 2) return null
            val t = LongArray(counter.size)
            val c = DoubleArray(counter.size)
            t[0] = counter[0].timeMillis
            for (i in 1 until counter.size) {
                t[i] = counter[i].timeMillis
                // A reboot resets the counter; a negative delta then means "unknown", counted as 0.
                c[i] = c[i - 1] + (counter[i].value - counter[i - 1].value).coerceAtLeast(0.0)
            }
            return t to c
        }
    }
}
