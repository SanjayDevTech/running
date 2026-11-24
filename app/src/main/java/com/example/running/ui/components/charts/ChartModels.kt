package com.example.running.ui.components.charts

data class ChartPoint(val x: Double, val y: Double)

data class BarDatum(val label: String, val value: Double)

/** [points] (sorted by x) cut into consecutive stretches at each x in [breaks]; empty stretches dropped. */
fun splitAtBreaks(points: List<ChartPoint>, breaks: List<Double>): List<List<ChartPoint>> {
    if (breaks.isEmpty()) return listOf(points)
    val cuts = breaks.sorted()
    return points.groupBy { p -> cuts.count { it <= p.x } }.toSortedMap().values.filter { it.isNotEmpty() }
}
